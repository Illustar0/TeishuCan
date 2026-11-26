package com.illustar0.teishucan.data

import com.illustar0.teishucan.concurrency.RefreshTracker
import com.illustar0.teishucan.di.ApplicationScope
import com.illustar0.teishucan.domain.CampusFeature
import com.illustar0.teishucan.domain.ConflictChoice
import com.illustar0.teishucan.domain.TimetableEntry
import com.illustar0.teishucan.domain.TimetableTerm
import com.illustar0.teishucan.domain.TimetableWeek
import com.illustar0.teishucan.domain.withFront
import com.illustar0.zzu.eas.Semester
import com.illustar0.zzu.eas.SemesterId
import com.illustar0.zzu.eas.SessionTeacher
import com.illustar0.zzu.eas.SessionView
import com.illustar0.zzu.eas.Timetable
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.serializer
import kotlin.time.Duration
import kotlin.time.Instant

@Singleton
class TimetableRepository @Inject constructor(
    private val session: ZzuSession,
    stores: JsonDataStoreFactory,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val cache = stores.createSnapshotStore(
        name = "timetable_snapshot.json",
        initial = emptyList(),
        serializer = serializer<List<TimetableTerm>>(),
        session = session.state,
    )
    private val refreshTracker = RefreshTracker()

    /** 用户的置顶选择和电表一样只保存在本机，与登录无关，退出登录后也保留。 */
    private val choiceStore = stores.create(
        name = "timetable_conflict_choices.json",
        defaultValue = emptyList(),
        serializer = serializer<List<ConflictChoice>>(),
    )

    val terms: StateFlow<List<TimetableTerm>> = cache.value
    val isRefreshing: StateFlow<Boolean> = refreshTracker.isRefreshing

    /** 冲突课程的置顶选择，按选择的先后排列。尚未读出时为空，课表暂按默认叠放。 */
    val conflictChoices: StateFlow<List<ConflictChoice>> =
        choiceStore.data.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * 会话恢复、置顶选择也读出之后的 [terms] 与 [conflictChoices]，在那之前不发出。
     * 应用不在前台时也要据此行动的功能用它，不会把还没读出的课表当成没有课。
     */
    val restoredSchedule: Flow<Pair<List<TimetableTerm>, List<ConflictChoice>>> =
        combine(cache.restoredValue, choiceStore.data, ::Pair)

    /** 把 [front] 置顶在 [conflict] 这组冲突课程里。写入失败时抛出 IOException，原有的选择保持不变。 */
    suspend fun putInFront(conflict: List<TimetableEntry>, front: TimetableEntry) {
        choiceStore.update { choices -> choices.withFront(conflict, front) }
    }

    suspend fun isStaleAt(now: Instant, maxAge: Duration): Boolean = cache.isStaleAt(now, maxAge)

    private class SharedRefresh(val owner: ZzuClients, val result: Deferred<Unit>)

    private val latestRefresh = AtomicReference<SharedRefresh?>(null)

    /**
     * 更新学期列表与当前学期的课程。几个页面会同时刷新，后到的调用方并入同一次登录进行中的那一次。
     * 旧登录的刷新不能合并：它的结果写不进缓存，新登录也就等不到数据。
     */
    suspend fun refresh() = refreshTracker.track {
        session.request(CampusFeature.Timetable) {
            val clients = this
            val candidate = SharedRefresh(
                owner = clients,
                result = scope.async(start = CoroutineStart.LAZY) { fetchCurrentTerm(clients) },
            )
            val running = latestRefresh.updateAndGet { latest ->
                if (latest != null && latest.owner === clients && !latest.result.isCompleted) latest else candidate
            }
            if (running === candidate) {
                candidate.result.invokeOnCompletion { latestRefresh.compareAndSet(candidate, null) }
            } else {
                candidate.result.cancel()
            }
            checkNotNull(running).result.await()
        }
    }

    private suspend fun fetchCurrentTerm(clients: ZzuClients) {
        val refreshVersion = cache.nextRefreshVersion()
        val eas = clients.eas()
        val current = eas.currentSemester()
        val (listedSemesters, timetable) = coroutineScope {
            val listedSemesters = async { eas.semesters() }
            val timetable = async { eas.timetable(current.id) }
            listedSemesters.await() to timetable.await()
        }
        val semesters = (listedSemesters + current).distinctBy { it.id }.sortedBy { it.startDate }
        cache.update(clients, refreshVersion) { terms ->
            val loaded = terms.associateBy { it.id }
            semesters.map { semester ->
                if (semester.id == current.id) {
                    timetable.semester.toTerm(timetable)
                } else {
                    loaded[semester.id.value.toString()] ?: semester.toTerm()
                }
            }
        }
    }

    suspend fun loadTerm(termId: String) = session.request(CampusFeature.Timetable) {
        val timetable = eas().timetable(SemesterId(termId.toLong()))
        cache.amend(this) { terms ->
            terms.map { term ->
                if (term.id != termId) term else timetable.semester.toTerm(timetable)
            }
        }
    }
}

internal fun Semester.toTerm(timetable: Timetable? = null): TimetableTerm {
    val numbers = timetable?.weekIndices() ?: weekIndices
    return TimetableTerm(
        id = id.value.toString(),
        schoolYear = schoolYear,
        // 学期代码形如 2026-2027-1，末段是学期序号。
        number = code.substringAfterLast('-'),
        startDate = startDate,
        endDate = endDate,
        weeks = numbers.map { number ->
            val weekStart = weekStart(number, timetable)
            TimetableWeek(
                number = number,
                dates = List(DayOfWeek.entries.size) { weekStart.plus(it, DateTimeUnit.DAY) },
                entries = timetable?.week(number)?.sessions().orEmpty().map { it.toEntry() },
            )
        },
        isLoaded = timetable != null,
    )
}

/**
 * 第 [number] 周的第一天。校历里的周次由 SDK 换算；校历之外的周次只因 [timetable] 在那周排了课才出现，
 * 就从那周课程的实际日期推出，而不是按周序号外推。
 */
private fun Semester.weekStart(number: Int, timetable: Timetable?): LocalDate {
    weekDateRange(number)?.let { return it.start }
    val scheduledDate = checkNotNull(timetable).sessions.first { it.weekIndex == number }.date
    val firstDay = if (startsWeekOnSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
    val daysIntoWeek = (scheduledDate.dayOfWeek.isoDayNumber - firstDay.isoDayNumber).mod(DayOfWeek.entries.size)
    return scheduledDate.minus(daysIntoWeek, DateTimeUnit.DAY)
}

internal fun SessionView.toEntry() = TimetableEntry(
    classId = teachingClass.id,
    day = session.weekday,
    date = session.date,
    startPeriod = session.startUnit,
    startTime = session.startTime.toCampusTime(),
    endTime = session.endTime.toCampusTime(),
    title = displayTitle,
    teacher = session.teachers
        .map(SessionTeacher::nameZh)
        .filter(String::isNotBlank)
        .joinToString("、"),
    location = session.room?.nameZh ?: session.customPlace,
    periodSpan = session.endUnit - session.startUnit + 1,
)

private fun Instant.toCampusTime(): LocalTime = toLocalDateTime(CampusZone).time

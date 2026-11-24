package com.illustar0.teishucan.data

import com.illustar0.teishucan.concurrency.RefreshTracker
import com.illustar0.teishucan.di.ApplicationScope
import com.illustar0.teishucan.domain.CampusFeature
import com.illustar0.teishucan.domain.TimetableEntry
import com.illustar0.teishucan.domain.TimetableTerm
import com.illustar0.teishucan.domain.TimetableWeek
import com.illustar0.zzu.ZzuTimeZone
import com.illustar0.zzu.eas.Semester
import com.illustar0.zzu.eas.SemesterId
import com.illustar0.zzu.eas.SessionTeacher
import com.illustar0.zzu.eas.SessionView
import com.illustar0.zzu.eas.Timetable
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.serializer
import kotlin.time.Duration
import kotlin.time.Instant

@Singleton
class TimetableRepository @Inject constructor(
    private val session: ZzuSession,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val cache = session.createSnapshotCache(
        "timetable.json",
        emptyList(),
        serializer<List<TimetableTerm>>(),
    )
    private val refreshTracker = RefreshTracker()

    val terms: StateFlow<List<TimetableTerm>> = cache.value
    val isRefreshing: StateFlow<Boolean> = refreshTracker.isRefreshing

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
        val eas = clients.eas()
        val current = eas.currentSemester()
        val (listedSemesters, timetable) = coroutineScope {
            val listedSemesters = async { eas.semesters() }
            val timetable = async { eas.timetable(current.id) }
            listedSemesters.await() to timetable.await()
        }
        val semesters = (listedSemesters + current).distinctBy { it.id }.sortedBy { it.startDate }
        cache.update(clients) { terms ->
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
    val start = startDate.toJavaLocalDate()
    val firstDay = if (startsWeekOnSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
    val firstWeekStart = start.with(TemporalAdjusters.previousOrSame(firstDay))
    val numbers = timetable?.weekIndices() ?: weekIndices
    return TimetableTerm(
        id = id.value.toString(),
        schoolYear = schoolYear,
        // 教务系统的学期名形如 2026-2027-1，末段是学期序号。
        number = nameZh.substringAfterLast('-'),
        startDate = start,
        endDate = endDate.toJavaLocalDate(),
        weeks = numbers.map { number ->
            val weekStart = weekDateRange(number)?.start?.toJavaLocalDate()
                ?: firstWeekStart.plusWeeks(number - 1L)
            TimetableWeek(
                number = number,
                dates = List(DayOfWeek.entries.size) { weekStart.plusDays(it.toLong()) },
                entries = timetable?.week(number)?.sessions().orEmpty().map { it.toEntry() },
            )
        },
        isLoaded = timetable != null,
    )
}

internal fun SessionView.toEntry() = TimetableEntry(
    day = session.weekday.toJavaDayOfWeek(),
    date = session.date.toJavaLocalDate(),
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

private fun Instant.toCampusTime(): LocalTime = toLocalDateTime(ZzuTimeZone).time.toJavaLocalTime()

package com.illustar0.teishucan.ui.classrooms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.concurrency.PendingFailure
import com.illustar0.teishucan.data.CampusZone
import com.illustar0.teishucan.data.ClassroomRepository
import com.illustar0.teishucan.data.ClassroomRequestKey
import com.illustar0.teishucan.data.ZzuSession
import com.illustar0.teishucan.data.campusMinuteTicks
import com.illustar0.teishucan.data.classroomRequestKey
import com.illustar0.teishucan.domain.AccountState
import com.illustar0.teishucan.domain.ClassroomBuildingGroup
import com.illustar0.teishucan.domain.ClassroomCatalogData
import com.illustar0.teishucan.domain.ClassroomDaySnapshot
import com.illustar0.teishucan.domain.ClassroomFilter
import com.illustar0.teishucan.domain.ClassroomItem
import com.illustar0.teishucan.domain.ClassroomLoadProgress
import com.illustar0.teishucan.domain.ScheduleUnit
import com.illustar0.teishucan.domain.effectiveUnits
import com.illustar0.teishucan.domain.filterClassrooms
import com.illustar0.teishucan.domain.groupClassroomsByBuilding
import com.illustar0.teishucan.domain.searchClassroomsByBuilding
import com.illustar0.teishucan.ui.component.FailureToasts
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

private val StateStopTimeout = 5.seconds

/** 围绕当前选中日期预投影的相邻半径，让跟手左右滑和快甩切日时已缓存邻页零闪烁。 */
internal const val AdjacentProjectedDaysRadius = 2

data class ClassroomDayPageState(
    val date: LocalDate,
    val effectiveFilter: ClassroomFilter = ClassroomFilter(),
    val filteredRooms: List<ClassroomItem> = emptyList(),
    val groups: List<ClassroomBuildingGroup> = emptyList(),
    val status: ClassroomBrowserStatus = ClassroomBrowserStatus.Loading,
    val isRefreshing: Boolean = false,
    val loadProgress: ClassroomLoadProgress? = null,
    val lastUpdatedAt: Instant? = null,
)

data class AvailableClassroomsState(
    val selectedDate: LocalDate,
    val today: LocalDate,
    val currentTime: LocalTime,
    val catalog: ClassroomCatalogData? = null,
    val rawFilter: ClassroomFilter = ClassroomFilter(),
    val dayPages: Map<LocalDate, ClassroomDayPageState> = emptyMap(),
    val searchResultGroups: List<ClassroomBuildingGroup> = emptyList(),
) {
    val selectedDay: ClassroomDayPageState
        get() = pageFor(selectedDate)

    val filter: ClassroomFilter
        get() = selectedDay.effectiveFilter

    val filteredRooms: List<ClassroomItem>
        get() = selectedDay.filteredRooms

    val groups: List<ClassroomBuildingGroup>
        get() = selectedDay.groups

    val status: ClassroomBrowserStatus
        get() = selectedDay.status

    val isRefreshing: Boolean
        get() = selectedDay.isRefreshing

    val loadProgress: ClassroomLoadProgress?
        get() = selectedDay.loadProgress

    val lastUpdatedAt: Instant?
        get() = selectedDay.lastUpdatedAt

    /** 返回 [date] 的页面状态；未命中投影表时返回对应生效筛选条件的加载占位态。 */
    fun pageFor(date: LocalDate): ClassroomDayPageState = dayPages[date] ?: ClassroomDayPageState(
        date = date,
        effectiveFilter = rawFilter.effectiveFor(isToday = date == today),
    )
}

private data class QueryInput(
    val date: LocalDate = Clock.System.todayIn(CampusZone),
    val filter: ClassroomFilter = ClassroomFilter(),
    val refreshNonce: Long = 0L,
)

private data class LoadTicket(
    val key: ClassroomRequestKey,
    val date: LocalDate,
    val filter: ClassroomFilter,
    val refreshNonce: Long,
)

internal sealed interface ActiveLoad {
    val key: ClassroomRequestKey

    data class Loading(
        override val key: ClassroomRequestKey,
        val progress: ClassroomLoadProgress = ClassroomLoadProgress(),
    ) : ActiveLoad

    data class Ready(
        override val key: ClassroomRequestKey,
        val snapshot: ClassroomDaySnapshot,
    ) : ActiveLoad

    data class Failed(override val key: ClassroomRequestKey) : ActiveLoad
}

internal data class DayProjectionContext(
    val selectedDate: LocalDate,
    val today: LocalDate,
    val currentTime: LocalTime,
    val rawFilter: ClassroomFilter,
    val units: List<ScheduleUnit>,
    val activeLoad: ActiveLoad?,
)

@HiltViewModel
class AvailableClassroomsViewModel @Inject constructor(
    session: ZzuSession,
    private val classrooms: ClassroomRepository,
) : ViewModel() {
    private val queryInputFlow = MutableStateFlow(QueryInput())
    private val searchQueryFlow = MutableStateFlow("")
    private val activeLoadFlow = MutableStateFlow<ActiveLoad?>(null)
    private var consumedRefreshNonce = 0L

    val failure = PendingFailure()

    private val baseState = combine(
        queryInputFlow,
        classrooms.snapshot,
        classrooms.remoteCacheVersion,
        activeLoadFlow,
        campusMinuteTicks(),
    ) { input, cacheSnapshot, _, activeLoad, now ->
        val context = DayProjectionContext(
            selectedDate = input.date,
            today = now.date,
            currentTime = now.time,
            rawFilter = input.filter,
            units = cacheSnapshot.catalog.effectiveUnits,
            activeLoad = activeLoad,
        )
        val dayPages = projectClassroomDayPages(
            context = context,
            cachedDayLookup = { date, effectiveFilter ->
                classrooms.peekCachedDay(date, effectiveFilter)
            },
        )
        AvailableClassroomsState(
            selectedDate = input.date,
            today = now.date,
            currentTime = now.time,
            catalog = cacheSnapshot.catalog,
            rawFilter = input.filter,
            dayPages = dayPages,
        )
    }

    val state: StateFlow<AvailableClassroomsState> = combine(
        baseState,
        searchQueryFlow,
    ) { current, query ->
        val searchGroups = if (query.isBlank() || current.filteredRooms.isEmpty()) {
            emptyList()
        } else {
            searchClassroomsByBuilding(current.filteredRooms, query)
        }
        current.copy(searchResultGroups = searchGroups)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeout = StateStopTimeout),
            initialAvailableClassroomsState(),
        )

    init {
        viewModelScope.launch {
            session.account
                .filter { it != AccountState.Restoring }
                .map { it is AccountState.SignedIn }
                .distinctUntilChanged()
                .collectLatest { isSignedIn ->
                    if (isSignedIn) runSignedIn() else forgetSignIn()
                }
        }
    }

    fun selectDate(date: LocalDate) {
        queryInputFlow.update { current -> current.copy(date = date) }
    }

    fun updateFilter(filter: ClassroomFilter) {
        queryInputFlow.update { current -> current.copy(filter = filter) }
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun refresh() {
        queryInputFlow.update { it.copy(refreshNonce = it.refreshNonce + 1) }
    }

    /** 切日期、改服务端筛选条件或退出登录时，collectLatest 会自动取消上一轮还没拉完的分页请求。 */
    private suspend fun runSignedIn() {
        queryInputFlow
            .map { input ->
                val serverFilter = if (input.filter.hasServerAttributes) input.filter else ClassroomFilter()
                LoadTicket(
                    key = classroomRequestKey(input.date, serverFilter),
                    date = input.date,
                    filter = serverFilter,
                    refreshNonce = input.refreshNonce,
                )
            }
            .distinctUntilChanged()
            .collectLatest { ticket ->
                val force = ticket.refreshNonce > consumedRefreshNonce
                consumedRefreshNonce = ticket.refreshNonce
                runLoad(ticket = ticket, force = force)
            }
    }

    private fun forgetSignIn() {
        consumedRefreshNonce = 0L
        queryInputFlow.value = QueryInput()
        searchQueryFlow.value = ""
        activeLoadFlow.value = null
        failure.clear()
    }

    private suspend fun runLoad(ticket: LoadTicket, force: Boolean) {
        try {
            val loaded = classrooms.loadDay(
                date = ticket.date,
                filter = ticket.filter,
                force = force,
                onProgress = { progress ->
                    activeLoadFlow.value = ActiveLoad.Loading(key = ticket.key, progress = progress)
                },
            )
            activeLoadFlow.value = ActiveLoad.Ready(key = ticket.key, snapshot = loaded)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            val activeKey = classroomRequestKey(queryInputFlow.value.date, queryInputFlow.value.filter)
            if (activeKey == ticket.key) {
                activeLoadFlow.value = ActiveLoad.Failed(key = ticket.key)
                failure.capture { throw cause }
            }
        }
    }
}

private fun initialAvailableClassroomsState(): AvailableClassroomsState {
    val now = Clock.System.now().toLocalDateTime(CampusZone)
    return AvailableClassroomsState(
        selectedDate = now.date,
        today = now.date,
        currentTime = now.time,
    )
}

/**
 * 为当前选中日及前后 [AdjacentProjectedDaysRadius] 天内已有缓存的日期构建页面状态。
 *
 * 邻页只读本地缓存且不触发网络请求，避免跟手左右滑时显示上一页内容或误刷教务接口；
 * 尚未缓存的邻页不写入映射表，由 [AvailableClassroomsState.pageFor] 兜底为加载态。
 */
internal fun projectClassroomDayPages(
    context: DayProjectionContext,
    cachedDayLookup: (date: LocalDate, effectiveFilter: ClassroomFilter) -> ClassroomDaySnapshot?,
): Map<LocalDate, ClassroomDayPageState> = buildMap {
    for (offset in -AdjacentProjectedDaysRadius..AdjacentProjectedDaysRadius) {
        val date = context.selectedDate.plus(offset, DateTimeUnit.DAY)
        val isSelected = offset == 0
        val effectiveFilter = context.rawFilter.effectiveFor(isToday = date == context.today)
        val requestKey = classroomRequestKey(date, effectiveFilter)
        val matchingLoad = context.activeLoad?.takeIf { it.key == requestKey }
        val daySnapshot = (matchingLoad as? ActiveLoad.Ready)?.snapshot
            ?: cachedDayLookup(date, effectiveFilter)
        if (!isSelected && daySnapshot == null && matchingLoad == null) continue
        put(
            date,
            projectClassroomDayPage(
                date = date,
                context = context,
                effectiveFilter = effectiveFilter,
                matchingLoad = matchingLoad,
                daySnapshot = daySnapshot,
            ),
        )
    }
}

private fun projectClassroomDayPage(
    date: LocalDate,
    context: DayProjectionContext,
    effectiveFilter: ClassroomFilter,
    matchingLoad: ActiveLoad?,
    daySnapshot: ClassroomDaySnapshot?,
): ClassroomDayPageState {
    val isRefreshing = matchingLoad is ActiveLoad.Loading
    val progress = (matchingLoad as? ActiveLoad.Loading)?.progress
    val filtered = if (isRefreshing) {
        emptyList()
    } else {
        daySnapshot?.let { day ->
            filterClassrooms(
                items = day.items,
                filter = effectiveFilter,
                currentTime = context.currentTime,
                isToday = date == context.today,
                units = context.units,
                isPreSorted = true,
            )
        }.orEmpty()
    }
    val groups = if (filtered.isEmpty()) emptyList() else groupClassroomsByBuilding(filtered)
    val status = when {
        isRefreshing -> ClassroomBrowserStatus.Loading
        daySnapshot == null && matchingLoad is ActiveLoad.Failed -> ClassroomBrowserStatus.Failed
        daySnapshot == null -> ClassroomBrowserStatus.Loading
        filtered.isEmpty() -> ClassroomBrowserStatus.Empty
        else -> ClassroomBrowserStatus.Ready
    }
    return ClassroomDayPageState(
        date = date,
        effectiveFilter = effectiveFilter,
        filteredRooms = filtered,
        groups = groups,
        status = status,
        isRefreshing = isRefreshing,
        loadProgress = progress,
        lastUpdatedAt = daySnapshot?.fetchedAt,
    )
}

@Composable
fun AvailableClassroomsRoute(onBack: () -> Unit) {
    val viewModel = hiltViewModel<AvailableClassroomsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    FailureToasts(viewModel.failure)

    AvailableClassroomsPage(
        state = state,
        onDateSelect = viewModel::selectDate,
        onFilterChange = viewModel::updateFilter,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onRefresh = viewModel::refresh,
        onBack = onBack,
    )
}

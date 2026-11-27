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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
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

/** 后台最多同时保活的联网日期加载数，兼顾 A↔B 往返不打断与狂滑时不堵死教务并发限流器。 */
internal const val MaxConcurrentClassroomLoads = 3

/** 状态表中最多保留的非在飞历史条目数，避免长时间浏览无限累积。 */
private const val MaxRetainedLoadEntries = 8

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
    val activeLoads: Map<ClassroomRequestKey, ActiveLoad>,
)

/**
 * 协调多天教室数据的并发加载：滑离正在加载的日期时不打断其拉取，
 * 同一请求键自动去重复用，联网在飞数超出 [maxConcurrentLoads] 时按 LRU 淘汰最老的非当前任务。
 */
internal class ClassroomLoadCoordinator(
    private val maxConcurrentLoads: Int = MaxConcurrentClassroomLoads,
    private val currentActiveKey: () -> ClassroomRequestKey,
    private val loadDay: suspend (
        date: LocalDate,
        filter: ClassroomFilter,
        force: Boolean,
        onProgress: (ClassroomLoadProgress) -> Unit,
    ) -> ClassroomDaySnapshot,
    private val onActiveFailure: suspend (Exception) -> Unit,
) {
    private data class InFlightEntry(
        val generation: Long,
        val job: Job,
        val hasStartedNetwork: Boolean = false,
    )

    private val lock = Any()
    private var nextGeneration = 0L
    private val inFlight = LinkedHashMap<ClassroomRequestKey, InFlightEntry>()
    private val loadsFlow = MutableStateFlow<Map<ClassroomRequestKey, ActiveLoad>>(emptyMap())

    val loads: StateFlow<Map<ClassroomRequestKey, ActiveLoad>> = loadsFlow.asStateFlow()

    /** 提交一次按日加载；若同键已有活跃任务且未要求 [force]，直接复用而不重复发请求。 */
    fun enqueue(
        scope: CoroutineScope,
        date: LocalDate,
        filter: ClassroomFilter,
        force: Boolean,
    ) {
        val key = classroomRequestKey(date, filter)
        var jobToCancel: Job? = null
        var jobToStart: Job? = null

        synchronized(lock) {
            val existing = inFlight[key]
            if (!force && existing != null && existing.job.isActive) {
                // 移到链表尾部，刷新最近活跃顺序，避免滑回后被 LRU 误淘汰。
                inFlight.remove(key)
                inFlight[key] = existing
                return
            }
            if (existing != null) {
                inFlight.remove(key)
                jobToCancel = existing.job
            }
            val generation = ++nextGeneration
            // LAZY 注册后再锁外启动，即使调度器同线程立即执行，也能先看到当前代数占位。
            val job = scope.launch(start = CoroutineStart.LAZY) {
                runLoad(
                    key = key,
                    generation = generation,
                    date = date,
                    filter = filter,
                    force = force,
                )
            }
            inFlight[key] = InFlightEntry(generation = generation, job = job)
            jobToStart = job
        }
        jobToCancel?.cancel()
        jobToStart?.start()
    }

    /** 退出登录时清空全部在飞记录与状态表；实际协程由调用方作用域取消。 */
    fun clear() {
        val jobsToCancel = synchronized(lock) {
            val jobs = inFlight.values.map(InFlightEntry::job)
            inFlight.clear()
            nextGeneration++
            loadsFlow.value = emptyMap()
            jobs
        }
        jobsToCancel.forEach(Job::cancel)
    }

    private suspend fun runLoad(
        key: ClassroomRequestKey,
        generation: Long,
        date: LocalDate,
        filter: ClassroomFilter,
        force: Boolean,
    ) {
        try {
            val loaded = loadDay(date, filter, force) { progress ->
                onNetworkProgress(key, generation, progress)
            }
            finishReady(key, generation, loaded)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            finishFailed(key, generation, cause)
        } finally {
            synchronized(lock) {
                if (inFlight[key]?.generation == generation) {
                    inFlight.remove(key)
                }
            }
        }
    }

    private fun onNetworkProgress(
        key: ClassroomRequestKey,
        generation: Long,
        progress: ClassroomLoadProgress,
    ) {
        val evictedJobs = mutableListOf<Job>()
        synchronized(lock) {
            val current = inFlight[key]
            if (current == null || current.generation != generation) return
            if (!current.hasStartedNetwork) {
                inFlight.remove(key)
                inFlight[key] = current.copy(hasStartedNetwork = true)
                evictOverflowNetworkLoadsLocked(protectedKey = key, evictedJobs = evictedJobs)
            }
            loadsFlow.update { map ->
                pruneRetainedLoadsLocked(map + (key to ActiveLoad.Loading(key = key, progress = progress)))
            }
        }
        evictedJobs.forEach(Job::cancel)
    }

    private fun finishReady(
        key: ClassroomRequestKey,
        generation: Long,
        snapshot: ClassroomDaySnapshot,
    ) {
        synchronized(lock) {
            if (inFlight[key]?.generation != generation) return
            inFlight.remove(key)
            loadsFlow.update { map ->
                pruneRetainedLoadsLocked(map + (key to ActiveLoad.Ready(key = key, snapshot = snapshot)))
            }
        }
    }

    private suspend fun finishFailed(
        key: ClassroomRequestKey,
        generation: Long,
        cause: Exception,
    ) {
        val isCurrentActive = synchronized(lock) {
            if (inFlight[key]?.generation != generation) return
            inFlight.remove(key)
            val active = currentActiveKey() == key
            loadsFlow.update { map ->
                if (active) {
                    pruneRetainedLoadsLocked(map + (key to ActiveLoad.Failed(key = key)))
                } else {
                    // 用户已滑离该日期：静默清除加载态且不弹 Toast，下次滑回时自动重新拉取。
                    map - key
                }
            }
            active
        }
        if (isCurrentActive) {
            onActiveFailure(cause)
        }
    }

    private fun evictOverflowNetworkLoadsLocked(
        protectedKey: ClassroomRequestKey,
        evictedJobs: MutableList<Job>,
    ) {
        var activeNetworkCount = inFlight.values.count { it.hasStartedNetwork && it.job.isActive }
        if (activeNetworkCount <= maxConcurrentLoads) return

        val activeKey = currentActiveKey()
        val iterator = inFlight.entries.iterator()
        while (activeNetworkCount > maxConcurrentLoads && iterator.hasNext()) {
            val (candidateKey, entry) = iterator.next()
            if (!entry.hasStartedNetwork || candidateKey == protectedKey || candidateKey == activeKey) continue
            iterator.remove()
            evictedJobs += entry.job
            loadsFlow.update { map ->
                if (map[candidateKey] is ActiveLoad.Loading) map - candidateKey else map
            }
            activeNetworkCount--
        }
    }

    private fun pruneRetainedLoadsLocked(
        map: Map<ClassroomRequestKey, ActiveLoad>,
    ): Map<ClassroomRequestKey, ActiveLoad> {
        if (map.size <= MaxRetainedLoadEntries) return map
        val activeKey = currentActiveKey()
        val retained = LinkedHashMap(map)
        val iterator = retained.entries.iterator()
        while (retained.size > MaxRetainedLoadEntries && iterator.hasNext()) {
            val (candidateKey, _) = iterator.next()
            if (candidateKey == activeKey || candidateKey in inFlight) continue
            iterator.remove()
        }
        return retained
    }
}

@HiltViewModel
class AvailableClassroomsViewModel @Inject constructor(
    session: ZzuSession,
    private val classrooms: ClassroomRepository,
) : ViewModel() {
    private val queryInputFlow = MutableStateFlow(QueryInput())
    private val searchQueryFlow = MutableStateFlow("")
    private var consumedRefreshNonce = 0L

    val failure = PendingFailure()

    private val loadCoordinator = ClassroomLoadCoordinator(
        currentActiveKey = {
            val input = queryInputFlow.value
            classroomRequestKey(input.date, input.filter)
        },
        loadDay = { date, filter, force, onProgress ->
            classrooms.loadDay(
                date = date,
                filter = filter,
                force = force,
                onProgress = onProgress,
            )
        },
        onActiveFailure = { cause ->
            failure.capture { throw cause }
        },
    )

    private val baseState = combine(
        queryInputFlow,
        classrooms.snapshot,
        classrooms.remoteCacheVersion,
        loadCoordinator.loads,
        campusMinuteTicks(),
    ) { input, cacheSnapshot, _, activeLoads, now ->
        val context = DayProjectionContext(
            selectedDate = input.date,
            today = now.date,
            currentTime = now.time,
            rawFilter = input.filter,
            units = cacheSnapshot.catalog.effectiveUnits,
            activeLoads = activeLoads,
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

    /** 切日期时保留最近在飞的加载任务不被打断，退出登录时由外层取消整个作用域统一收尾。 */
    private suspend fun runSignedIn() = coroutineScope {
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
            .collect { ticket ->
                val force = ticket.refreshNonce > consumedRefreshNonce
                consumedRefreshNonce = ticket.refreshNonce
                loadCoordinator.enqueue(
                    scope = this,
                    date = ticket.date,
                    filter = ticket.filter,
                    force = force,
                )
            }
    }

    private fun forgetSignIn() {
        consumedRefreshNonce = 0L
        queryInputFlow.value = QueryInput()
        searchQueryFlow.value = ""
        loadCoordinator.clear()
        failure.clear()
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
        val matchingLoad = context.activeLoads[requestKey]
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

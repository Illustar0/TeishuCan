package com.illustar0.teishucan.data

import androidx.collection.LruCache
import com.illustar0.teishucan.di.ApplicationScope
import com.illustar0.teishucan.domain.CampusFeature
import com.illustar0.teishucan.domain.ClassroomActivityKind
import com.illustar0.teishucan.domain.ClassroomBuilding
import com.illustar0.teishucan.domain.ClassroomCacheSnapshot
import com.illustar0.teishucan.domain.ClassroomCacheTtl
import com.illustar0.teishucan.domain.ClassroomCampus
import com.illustar0.teishucan.domain.ClassroomCatalogData
import com.illustar0.teishucan.domain.ClassroomDaySnapshot
import com.illustar0.teishucan.domain.ClassroomFilter
import com.illustar0.teishucan.domain.ClassroomItem
import com.illustar0.teishucan.domain.ClassroomLoadProgress
import com.illustar0.teishucan.domain.ClassroomOccupation
import com.illustar0.teishucan.domain.ClassroomType
import com.illustar0.teishucan.domain.sortClassrooms
import com.illustar0.zzu.eas.ClassroomCatalog
import com.illustar0.zzu.eas.ClassroomFetchOptions
import com.illustar0.zzu.eas.ClassroomOccupancy
import com.illustar0.zzu.eas.ClassroomPage
import com.illustar0.zzu.eas.ClassroomQuery
import com.illustar0.zzu.eas.CourseUnit
import com.illustar0.zzu.eas.EasClient
import com.illustar0.zzu.eas.RoomActivityType
import com.illustar0.zzu.eas.RoomOccupation
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.serializer

/** 磁盘快照最多留最近查过的 7 天，免得越积越大。 */
internal const val MaxCachedClassroomDays = 7

internal const val MaxRemoteQueryCacheEntries = 16

private val StartOfDay = LocalTime(0, 0)
private val EndOfDay = LocalTime.parse("23:59:59.999999999")

internal data class ClassroomRemoteQueryKey(
    val date: LocalDate,
    val campusId: Long?,
    val buildingIds: Set<Long>,
    val roomTypeIds: Set<Long>,
    val floors: Set<Int>,
    val minSeats: Int?,
    val maxSeats: Int?,
)

internal sealed interface ClassroomRequestKey {
    val date: LocalDate

    data class FullDay(override val date: LocalDate) : ClassroomRequestKey

    data class Filtered(val query: ClassroomRemoteQueryKey) : ClassroomRequestKey {
        override val date: LocalDate
            get() = query.date
    }
}

/**
 * 教室目录与占用数据的仓库。
 *
 * 目录和最近查过的全天数据存进 [SnapshotStore]，12 小时内有效；
 * 勾了教室类型、楼层或座位数的查询走内存 [LruCache]，不落盘。
 */
@Singleton
class ClassroomRepository @Inject constructor(
    private val session: ZzuSession,
    stores: JsonDataStoreFactory,
    @ApplicationScope scope: CoroutineScope,
) {
    private val cache = stores.createSnapshotStore(
        name = "classrooms_snapshot.json",
        initial = ClassroomCacheSnapshot(),
        serializer = serializer<ClassroomCacheSnapshot>(),
        session = session.state,
    )
    private val catalogFetchLock = Mutex()
    private val remoteCache = LruCache<ClassroomRemoteQueryKey, ClassroomDaySnapshot>(MaxRemoteQueryCacheEntries)

    /** 限制同时在飞的分页请求数，切日期重拉时新旧请求短暂重叠也不会冲垮教务接口。 */
    private val pageConcurrencyLimiter = Semaphore(ClassroomFetchOptions.DEFAULT_CONCURRENCY)

    val snapshot: StateFlow<ClassroomCacheSnapshot> = cache.value

    init {
        scope.launch {
            session.state
                .map { it.clients }
                .distinctUntilChanged()
                .collect { remoteCache.evictAll() }
        }
    }

    /** 直接取内存或已读回磁盘的当日数据；就算过了期也照给，刷新失败时还能拿来垫底。 */
    fun peekCachedDay(date: LocalDate, filter: ClassroomFilter): ClassroomDaySnapshot? =
        if (filter.hasServerAttributes) {
            remoteCache[filter.toRemoteKey(date)]
        } else {
            snapshot.value.days[date]
        }

    /**
     * 加载 [date] 的教室数据。缓存还没过期且未传 [force] 时直接返回，不触发 [onProgress]；
     * 需要联网时并发分页拉取，每完成一页通过 [onProgress] 回报当前进度。
     */
    suspend fun loadDay(
        date: LocalDate,
        filter: ClassroomFilter,
        force: Boolean = false,
        onProgress: (ClassroomLoadProgress) -> Unit = {},
    ): ClassroomDaySnapshot {
        val now = Clock.System.now()
        val restored = cache.restoredValue.first()
        val isCatalogFresh = restored.catalog != null &&
            !isStaleTimestamp(restored.catalogFetchedAt, now, ClassroomCacheTtl)
        val remoteKey = if (filter.hasServerAttributes) filter.toRemoteKey(date) else null
        val cached = if (remoteKey != null) remoteCache[remoteKey] else restored.days[date]
        val isDayFresh = cached != null && !isStaleTimestamp(cached.fetchedAt, now, ClassroomCacheTtl)
        if (!force && isCatalogFresh && isDayFresh) return cached

        onProgress(ClassroomLoadProgress(loadedPages = 0, totalPages = null))
        return session.request(CampusFeature.Classrooms) {
            val clients = this
            val eas = clients.eas()
            val query = remoteKey?.toClassroomQuery() ?: ClassroomQuery(date = date)
            val maxLoadedPages = AtomicInteger(0)

            val (catalog, occupancies) = coroutineScope {
                val catalogDeferred = async { ensureCatalogFresh(clients, eas) }
                val pages = fetchAllPages(
                    limiter = pageConcurrencyLimiter,
                    fetchPage = { page ->
                        eas.classroomOccupanciesPage(
                            query = query,
                            page = page,
                            pageSize = ClassroomFetchOptions.DEFAULT_PAGE_SIZE,
                        )
                    },
                    onProgress = { loaded, total ->
                        val monotonicLoaded = maxLoadedPages.accumulateAndGet(loaded, ::maxOf)
                        onProgress(ClassroomLoadProgress(loadedPages = monotonicLoaded, totalPages = total))
                    },
                )
                catalogDeferred.await() to pages
            }

            val daySnapshot = ClassroomDaySnapshot(
                date = date,
                fetchedAt = Clock.System.now(),
                items = sortClassrooms(occupancies.toDomainList(catalog, date)),
            )
            if (remoteKey != null) {
                if (session.state.value.clients === clients) {
                    remoteCache.put(remoteKey, daySnapshot)
                }
            } else {
                cache.amend(clients) { current ->
                    current.copy(days = pruneCachedDays(current.days + (date to daySnapshot)))
                }
            }
            daySnapshot
        }
    }

    /** 加锁拉取教室目录，并发查询时同一份过期目录只请求一次。 */
    private suspend fun ensureCatalogFresh(
        clients: ZzuClients,
        eas: EasClient,
    ): ClassroomCatalogData = catalogFetchLock.withLock {
        val now = Clock.System.now()
        val existing = cache.restoredValue.first()
        val freshCatalog = existing.catalog
        if (freshCatalog != null && !isStaleTimestamp(existing.catalogFetchedAt, now, ClassroomCacheTtl)) {
            return@withLock freshCatalog
        }
        val fetched = eas.classroomCatalog().toDomain()
        val fetchedAt = Clock.System.now()
        cache.amend(clients) { current ->
            current.copy(catalog = fetched, catalogFetchedAt = fetchedAt)
        }
        fetched
    }
}

internal fun ClassroomFilter.toRemoteKey(date: LocalDate): ClassroomRemoteQueryKey = ClassroomRemoteQueryKey(
    date = date,
    campusId = campusId,
    buildingIds = buildingIds,
    roomTypeIds = roomTypeIds,
    floors = floors,
    minSeats = minSeats,
    maxSeats = maxSeats,
)

internal fun classroomRequestKey(date: LocalDate, filter: ClassroomFilter): ClassroomRequestKey =
    if (filter.hasServerAttributes) {
        ClassroomRequestKey.Filtered(filter.toRemoteKey(date))
    } else {
        ClassroomRequestKey.FullDay(date)
    }

private fun ClassroomRemoteQueryKey.toClassroomQuery(): ClassroomQuery = ClassroomQuery(
    date = date,
    campusId = campusId,
    buildingIds = buildingIds.toList(),
    roomTypeIds = roomTypeIds.toList(),
    floors = floors.toList(),
    minSeats = minSeats,
    maxSeats = maxSeats,
)

/** 先拉第 1 页拿到总页数，再按 [limiter] 的并发上限拉完剩下的页，最后按页序合并并按教室 ID 去重。 */
internal suspend fun fetchAllPages(
    limiter: Semaphore,
    fetchPage: suspend (page: Int) -> ClassroomPage,
    onProgress: (loadedPages: Int, totalPages: Int) -> Unit,
): List<ClassroomOccupancy> {
    val firstPage = limiter.withPermit { fetchPage(1) }
    val totalPages = firstPage.totalPages.coerceAtLeast(1)
    onProgress(1, totalPages)
    if (totalPages <= 1) {
        return firstPage.items.distinctBy(ClassroomOccupancy::id)
    }

    val completed = AtomicInteger(1)
    val remainingPages = coroutineScope {
        (2..totalPages).map { page ->
            async {
                limiter.withPermit {
                    fetchPage(page).also {
                        onProgress(completed.incrementAndGet(), totalPages)
                    }
                }
            }
        }.awaitAll()
    }

    return buildList {
        addAll(firstPage.items)
        remainingPages.sortedBy(ClassroomPage::page).forEach { addAll(it.items) }
    }.distinctBy(ClassroomOccupancy::id)
}

/** 只留最近拉取过的 [maxDays] 天数据。 */
internal fun pruneCachedDays(
    days: Map<LocalDate, ClassroomDaySnapshot>,
    maxDays: Int = MaxCachedClassroomDays,
): Map<LocalDate, ClassroomDaySnapshot> {
    if (days.size <= maxDays) return days
    return days.entries
        .sortedByDescending { it.value.fetchedAt }
        .take(maxDays)
        .associate { it.key to it.value }
}

internal fun ClassroomCatalog.toDomain(): ClassroomCatalogData = ClassroomCatalogData(
    campuses = campuses.filter { it.isEnabled }.map { ClassroomCampus(id = it.id, name = it.nameZh) },
    buildings = buildings.filter { it.isEnabled }.map {
        ClassroomBuilding(id = it.id, name = it.nameZh, campusId = it.campusId)
    },
    roomTypes = roomTypes.filter { it.isEnabled }.map { ClassroomType(id = it.id, name = it.nameZh) },
    floors = floors.distinct().sorted(),
    units = units.map(CourseUnit::toDomain),
)

/** 整批转换前先按楼栋和校区建好索引，省得每间教室都扫一遍列表。 */
internal fun List<ClassroomOccupancy>.toDomainList(
    catalog: ClassroomCatalogData,
    queryDate: LocalDate,
): List<ClassroomItem> {
    val buildingsById = catalog.buildings.associateBy(ClassroomBuilding::id)
    val campusesById = catalog.campuses.associateBy(ClassroomCampus::id)
    val campusesByName = catalog.campuses.associateBy(ClassroomCampus::name)
    return map { occupancy ->
        occupancy.toDomain(
            buildingsById = buildingsById,
            campusesById = campusesById,
            campusesByName = campusesByName,
            queryDate = queryDate,
        )
    }
}

private fun ClassroomOccupancy.toDomain(
    buildingsById: Map<Long, ClassroomBuilding>,
    campusesById: Map<Long, ClassroomCampus>,
    campusesByName: Map<String, ClassroomCampus>,
    queryDate: LocalDate?,
): ClassroomItem {
    val building = buildingsById[buildingId]
    val resolvedCampus = building?.let { campusesById[it.campusId] } ?: campusesByName[campusNameZh]
    return ClassroomItem(
        id = id,
        code = code,
        name = nameZh,
        buildingId = buildingId,
        buildingName = building?.name ?: campusNameZh,
        campusId = resolvedCampus?.id,
        campusName = resolvedCampus?.name ?: campusNameZh,
        occupations = occupations
            .mapNotNull { it.toDomainForDate(queryDate ?: it.date) }
            .sortedBy(ClassroomOccupation::startTime),
    )
}

/** 把跨午夜的占用（如 23:30 到次日 00:30）裁进 [targetDate] 当天，免得折算成钟点后出现结束早于开始。 */
private fun RoomOccupation.toDomainForDate(targetDate: LocalDate): ClassroomOccupation? {
    val startDateTime = startTime.toLocalDateTime(CampusZone)
    val endDateTime = endTime.toLocalDateTime(CampusZone)
    if (startDateTime.date > targetDate || endDateTime.date < targetDate) return null
    val clippedStart = if (startDateTime.date < targetDate) StartOfDay else startDateTime.time
    val clippedEnd = if (endDateTime.date > targetDate) EndOfDay else endDateTime.time
    if (clippedStart >= clippedEnd) return null
    return ClassroomOccupation(
        activityKind = when (activityType) {
            RoomActivityType.LESSON -> ClassroomActivityKind.Lesson
            RoomActivityType.EXPERIMENT -> ClassroomActivityKind.Experiment
            RoomActivityType.ROOM_BORROW -> ClassroomActivityKind.RoomBorrow
            RoomActivityType.EXAM -> ClassroomActivityKind.Exam
            RoomActivityType.SCHEDULE_CHANGE -> ClassroomActivityKind.ScheduleChange
            else -> ClassroomActivityKind.Other
        },
        activityName = activityName,
        teacherName = teacherName,
        startTime = clippedStart,
        endTime = clippedEnd,
    )
}

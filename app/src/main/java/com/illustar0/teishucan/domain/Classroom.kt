package com.illustar0.teishucan.domain

import java.math.BigInteger
import java.text.CollationKey
import java.text.Collator
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

val ClassroomCacheTtl: Duration = 12.hours

@Serializable
data class ClassroomCampus(
    val id: Long,
    val name: String,
)

@Serializable
data class ClassroomBuilding(
    val id: Long,
    val name: String,
    val campusId: Long,
)

@Serializable
data class ClassroomType(
    val id: Long,
    val name: String,
)

@Serializable
data class ClassroomCatalogData(
    val campuses: List<ClassroomCampus> = emptyList(),
    val buildings: List<ClassroomBuilding> = emptyList(),
    val roomTypes: List<ClassroomType> = emptyList(),
    val floors: List<Int> = emptyList(),
    val units: List<ScheduleUnit> = emptyList(),
)

/** 目录里的节次作息；目录还没加载或没带作息时回退到 [DefaultScheduleUnits]。 */
val ClassroomCatalogData?.effectiveUnits: List<ScheduleUnit>
    get() = this?.units?.takeIf { it.isNotEmpty() } ?: DefaultScheduleUnits

@Serializable
enum class ClassroomActivityKind {
    Lesson,
    Experiment,
    RoomBorrow,
    Exam,
    ScheduleChange,
    Other,
}

/** 教室当天的一段占用，按半开区间 `[startTime, endTime)` 计算，首尾相接不算冲突。 */
@Serializable
data class ClassroomOccupation(
    val activityKind: ClassroomActivityKind,
    val activityName: String,
    val teacherName: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
) {
    fun overlaps(start: LocalTime, end: LocalTime): Boolean = startTime < end && endTime > start

    fun contains(moment: LocalTime): Boolean = moment >= startTime && moment < endTime
}

@Serializable
data class ClassroomItem(
    val id: Long,
    val code: String,
    val name: String,
    val buildingId: Long,
    val buildingName: String,
    val campusId: Long?,
    val campusName: String,
    val occupations: List<ClassroomOccupation> = emptyList(),
) {
    /** 去掉空白、与教室名重复的编号和 10 位以上的纯数字后台 ID；没有可展示的编号时为空串。 */
    @Transient
    val displayCode: String = meaningfulRoomCode(code = code, roomName = name)

    fun isFreeDuring(start: LocalTime, end: LocalTime): Boolean =
        occupations.none { it.overlaps(start, end) }

    fun isFreeAtMoment(moment: LocalTime): Boolean =
        occupations.none { it.contains(moment) }

    fun isFreeAt(unit: ScheduleUnit): Boolean = isFreeDuring(unit.startTime, unit.endTime)

    /** 作息表里没有第 [periodNumber] 节时按空闲算。 */
    fun isFreeAtPeriodNumber(periodNumber: Int, units: List<ScheduleUnit> = DefaultScheduleUnits): Boolean {
        val effective = units.ifEmpty { DefaultScheduleUnits }
        val unit = effective.firstOrNull { it.index == periodNumber } ?: return true
        return isFreeAt(unit)
    }

    fun isFreeForPeriods(periods: Set<Int>, units: List<ScheduleUnit> = DefaultScheduleUnits): Boolean {
        if (periods.isEmpty()) return true
        val effective = units.ifEmpty { DefaultScheduleUnits }
        return effective.all { unit -> unit.index !in periods || isFreeAt(unit) }
    }

    fun freePeriodCount(units: List<ScheduleUnit> = DefaultScheduleUnits): Int =
        units.ifEmpty { DefaultScheduleUnits }.count(::isFreeAt)
}

@Serializable
data class ClassroomDaySnapshot(
    val date: LocalDate,
    val fetchedAt: Instant,
    val items: List<ClassroomItem>,
)

/** 当前登录期间缓存的教室目录与按日期归档的全天数据。 */
@Serializable
data class ClassroomCacheSnapshot(
    val catalog: ClassroomCatalogData? = null,
    val catalogFetchedAt: Instant? = null,
    val days: Map<LocalDate, ClassroomDaySnapshot> = emptyMap(),
)

sealed interface ClassroomTimeFilter {
    /** 此刻空闲，只在查今天时生效。 */
    data object Now : ClassroomTimeFilter

    /** 选中的节次都空闲；空集表示全天无占用。 */
    data class Periods(val periods: Set<Int> = emptySet()) : ClassroomTimeFilter
}

data class ClassroomFilter(
    val campusId: Long? = null,
    val buildingIds: Set<Long> = emptySet(),
    val roomTypeIds: Set<Long> = emptySet(),
    val floors: Set<Int> = emptySet(),
    val minSeats: Int? = null,
    val maxSeats: Int? = null,
    val timeFilter: ClassroomTimeFilter = ClassroomTimeFilter.Periods(),
    val onlyAvailable: Boolean = true,
) {
    /**
     * 教室类型、楼层和座位数不在日常占用响应里返回，勾上这几项时要带条件请求教务接口；
     * 都没勾时直接筛本地缓存的当日全量数据。
     */
    val hasServerAttributes: Boolean
        get() = roomTypeIds.isNotEmpty() || floors.isNotEmpty() || minSeats != null || maxSeats != null

    val hasAnyActiveFilter: Boolean
        get() = activeFilterCount > 0

    val activeFilterCount: Int
        get() = listOf(
            campusId != null,
            buildingIds.isNotEmpty(),
            roomTypeIds.isNotEmpty(),
            floors.isNotEmpty(),
            minSeats != null || maxSeats != null,
            timeFilter != ClassroomTimeFilter.Periods(),
            !onlyAvailable,
        ).count { it }

    /** 非今天浏览时把「此刻空闲」降级为全天无占用，保留用户原始筛选意图。 */
    fun effectiveFor(isToday: Boolean): ClassroomFilter =
        if (!isToday && timeFilter == ClassroomTimeFilter.Now) {
            copy(timeFilter = ClassroomTimeFilter.Periods())
        } else {
            this
        }
}

private const val PercentageScale = 100

/** 分页拉取进度；拿到第 1 页前还不知道总页数，[totalPages] 为 null。 */
data class ClassroomLoadProgress(
    val loadedPages: Int = 0,
    val totalPages: Int? = null,
) {
    val fraction: Float?
        get() = totalPages?.takeIf { it > 0 }?.let { (loadedPages.toFloat() / it.toFloat()).coerceIn(0f, 1f) }

    val percentage: Int?
        get() = fraction?.let { (it * PercentageScale).toInt() }
}

data class ClassroomBuildingGroup(
    val buildingId: Long,
    val buildingName: String,
    val campusName: String,
    val rooms: List<ClassroomItem>,
)

private val QueryWhitespace = Regex("\\s+")
private val NaturalChunkPattern = Regex("\\d+|\\D+")

/** 按空白拆词，每个词都能在教室名、有效编号、教学楼或校区里忽略大小写搜到才算命中。 */
fun matchesClassroomQuery(query: String, item: ClassroomItem): Boolean {
    val tokens = tokenizeQuery(query)
    return tokens.all(item::matchesToken)
}

/**
 * 教室在 [timeFilter] 下是否算空闲，列表筛选与行内状态共用同一套口径：
 * 查非今天时 [ClassroomTimeFilter.Now] 回退为全天无占用；[ClassroomTimeFilter.Periods] 为空集时同样要求全天无占用。
 */
fun ClassroomItem.matchesAvailability(
    timeFilter: ClassroomTimeFilter,
    currentTime: LocalTime,
    isToday: Boolean,
    units: List<ScheduleUnit> = DefaultScheduleUnits,
): Boolean = when (timeFilter) {
    ClassroomTimeFilter.Now -> if (isToday) isFreeAtMoment(currentTime) else occupations.isEmpty()
    is ClassroomTimeFilter.Periods -> if (timeFilter.periods.isEmpty()) {
        occupations.isEmpty()
    } else {
        isFreeForPeriods(timeFilter.periods, units)
    }
}

/** 按教学楼和教室名称排序：中文按拼音排，连续数字按数值比大小。 */
fun sortClassrooms(items: List<ClassroomItem>): List<ClassroomItem> {
    if (items.size <= 1) return items
    val collator = newChineseNaturalCollator()
    val keyCache = HashMap<String, NaturalSortKey>()
    fun keyOf(text: String): NaturalSortKey = keyCache.getOrPut(text) { buildNaturalSortKey(text, collator) }

    return items
        .map { item -> SortedClassroomEntry(item, keyOf(item.buildingName), keyOf(item.name)) }
        .sorted()
        .map(SortedClassroomEntry::item)
}

/** 按校区、教学楼与空闲条件筛选 [items]；输入已排好序时传 [isPreSorted] 跳过重复排序。 */
fun filterClassrooms(
    items: List<ClassroomItem>,
    filter: ClassroomFilter,
    currentTime: LocalTime,
    isToday: Boolean,
    units: List<ScheduleUnit> = DefaultScheduleUnits,
    isPreSorted: Boolean = false,
): List<ClassroomItem> {
    val matched = items.filter { item ->
        val matchesLocation = (filter.campusId == null || item.campusId == filter.campusId) &&
            (filter.buildingIds.isEmpty() || item.buildingId in filter.buildingIds)
        val matchesAvailability = !filter.onlyAvailable ||
            item.matchesAvailability(filter.timeFilter, currentTime, isToday, units)
        matchesLocation && matchesAvailability
    }
    return if (isPreSorted) matched else sortClassrooms(matched)
}

fun searchClassroomsByBuilding(
    items: List<ClassroomItem>,
    query: String,
): List<ClassroomBuildingGroup> {
    val tokens = tokenizeQuery(query)
    if (tokens.isEmpty()) return emptyList()
    val matched = items.filter { item -> tokens.all(item::matchesToken) }
    return groupClassroomsByBuilding(matched)
}

fun groupClassroomsByBuilding(items: List<ClassroomItem>): List<ClassroomBuildingGroup> = items
    .groupBy { it.buildingId to it.buildingName }
    .map { (key, rooms) ->
        ClassroomBuildingGroup(
            buildingId = key.first,
            buildingName = key.second,
            campusName = rooms.firstOrNull()?.campusName.orEmpty(),
            rooms = rooms,
        )
    }

/**
 * 各时段在 [units] 中的连续下标区间，供 `PeriodStrip` 按实际节次数绘制。
 * 即使教务返回的 [ScheduleUnit.index] 跳号，画出的格数也与 `units.size` 一致。
 */
internal fun unitPositionRanges(units: List<ScheduleUnit>): List<IntRange> {
    val effective = units.ifEmpty { DefaultScheduleUnits }
    var offset = 0
    return groupScheduleUnitsByDayPart(effective).map { group ->
        val range = offset until (offset + group.units.size)
        offset += group.units.size
        range
    }
}

/**
 * 若 [selected] 至少有 2 节且在 [units] 中紧挨着排列，返回首尾两节的短名；否则返回 null。
 */
internal fun contiguousPeriodEndpoints(
    selected: Set<Int>,
    units: List<ScheduleUnit>,
): Pair<String, String>? {
    if (selected.size < 2) return null
    val effective = units.ifEmpty { DefaultScheduleUnits }
    val positions = effective.indices.filter { effective[it].index in selected }
    val isContiguousSlice = positions.size == selected.size &&
        positions.last() - positions.first() + 1 == positions.size
    return if (isContiguousSlice) {
        effective[positions.first()].label to effective[positions.last()].label
    } else {
        null
    }
}

internal fun compareNatural(first: String, second: String): Int {
    if (first == second) return 0
    val collator = newChineseNaturalCollator()
    return buildNaturalSortKey(first, collator).compareTo(buildNaturalSortKey(second, collator))
}

private fun tokenizeQuery(query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    return trimmed.split(QueryWhitespace)
}

private fun ClassroomItem.matchesToken(token: String): Boolean =
    name.contains(token, ignoreCase = true) ||
        displayCode.contains(token, ignoreCase = true) ||
        buildingName.contains(token, ignoreCase = true) ||
        campusName.contains(token, ignoreCase = true)

private fun newChineseNaturalCollator(): Collator =
    Collator.getInstance(Locale.CHINA).apply { strength = Collator.SECONDARY }

private sealed interface NaturalChunk : Comparable<NaturalChunk> {
    data class Digits(val value: BigInteger, val rawLength: Int) : NaturalChunk {
        override fun compareTo(other: NaturalChunk): Int = when (other) {
            is Digits -> value.compareTo(other.value).takeIf { it != 0 }
                ?: rawLength.compareTo(other.rawLength)
            is Text -> -1
        }
    }

    data class Text(val collationKey: CollationKey) : NaturalChunk {
        override fun compareTo(other: NaturalChunk): Int = when (other) {
            is Digits -> 1
            is Text -> collationKey.compareTo(other.collationKey)
        }
    }
}

private class NaturalSortKey(private val chunks: List<NaturalChunk>) : Comparable<NaturalSortKey> {
    override fun compareTo(other: NaturalSortKey): Int {
        val sharedSize = minOf(chunks.size, other.chunks.size)
        for (index in 0 until sharedSize) {
            val diff = chunks[index].compareTo(other.chunks[index])
            if (diff != 0) return diff
        }
        return chunks.size.compareTo(other.chunks.size)
    }
}

private class SortedClassroomEntry(
    val item: ClassroomItem,
    private val buildingKey: NaturalSortKey,
    private val roomKey: NaturalSortKey,
) : Comparable<SortedClassroomEntry> {
    override fun compareTo(other: SortedClassroomEntry): Int =
        buildingKey.compareTo(other.buildingKey).takeIf { it != 0 }
            ?: roomKey.compareTo(other.roomKey).takeIf { it != 0 }
            ?: item.id.compareTo(other.item.id)
}

private fun buildNaturalSortKey(text: String, collator: Collator): NaturalSortKey {
    val chunks = NaturalChunkPattern.findAll(text).map { match ->
        val token = match.value
        if (token[0].isAsciiDigit()) {
            NaturalChunk.Digits(value = token.toBigInteger(), rawLength = token.length)
        } else {
            NaturalChunk.Text(collationKey = collator.getCollationKey(token))
        }
    }.toList()
    return NaturalSortKey(chunks)
}

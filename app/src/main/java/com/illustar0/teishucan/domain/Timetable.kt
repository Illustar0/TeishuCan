package com.illustar0.teishucan.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/** 一天内的作息时段归属。 */
@Serializable
enum class ScheduleDayPart {
    Morning,
    Noon,
    Afternoon,
    Evening,
}

/** 每日作息表中的单节刻度：序号 [index] 从 1 开始，[label] 为不含汉字的短名（如 `"1"`、`"+1"`）。 */
@Serializable
data class ScheduleUnit(
    val index: Int,
    val label: String = index.toString(),
    val dayPart: ScheduleDayPart,
    val startTime: LocalTime,
    val endTime: LocalTime,
) {
    val isExtra: Boolean
        get() = label.startsWith('+')
}

/** 同一时段内连续排列的一组节次。 */
data class ScheduleDayPartGroup(
    val dayPart: ScheduleDayPart,
    val units: List<ScheduleUnit>,
) {
    val range: IntRange
        get() = units.first().index..units.last().index

    val unitIndices: Set<Int> = units.mapTo(linkedSetOf(), ScheduleUnit::index)
}

/** 郑州大学常规 10 节真实作息表，供旧快照或未返回作息表时回退。 */
val DefaultScheduleUnits: List<ScheduleUnit> = listOf(
    ScheduleUnit(
        index = 1,
        dayPart = ScheduleDayPart.Morning,
        startTime = LocalTime.parse("08:00"),
        endTime = LocalTime.parse("08:45"),
    ),
    ScheduleUnit(
        index = 2,
        dayPart = ScheduleDayPart.Morning,
        startTime = LocalTime.parse("08:55"),
        endTime = LocalTime.parse("09:40"),
    ),
    ScheduleUnit(
        index = 3,
        dayPart = ScheduleDayPart.Morning,
        startTime = LocalTime.parse("10:10"),
        endTime = LocalTime.parse("10:55"),
    ),
    ScheduleUnit(
        index = 4,
        dayPart = ScheduleDayPart.Morning,
        startTime = LocalTime.parse("11:05"),
        endTime = LocalTime.parse("11:50"),
    ),
    ScheduleUnit(
        index = 5,
        dayPart = ScheduleDayPart.Afternoon,
        startTime = LocalTime.parse("14:00"),
        endTime = LocalTime.parse("14:45"),
    ),
    ScheduleUnit(
        index = 6,
        dayPart = ScheduleDayPart.Afternoon,
        startTime = LocalTime.parse("14:55"),
        endTime = LocalTime.parse("15:40"),
    ),
    ScheduleUnit(
        index = 7,
        dayPart = ScheduleDayPart.Afternoon,
        startTime = LocalTime.parse("16:00"),
        endTime = LocalTime.parse("16:45"),
    ),
    ScheduleUnit(
        index = 8,
        dayPart = ScheduleDayPart.Afternoon,
        startTime = LocalTime.parse("16:55"),
        endTime = LocalTime.parse("17:40"),
    ),
    ScheduleUnit(
        index = 9,
        dayPart = ScheduleDayPart.Evening,
        startTime = LocalTime.parse("19:00"),
        endTime = LocalTime.parse("19:45"),
    ),
    ScheduleUnit(
        index = 10,
        dayPart = ScheduleDayPart.Evening,
        startTime = LocalTime.parse("19:55"),
        endTime = LocalTime.parse("20:40"),
    ),
)

/** 按相邻节次的 [ScheduleUnit.dayPart] 将作息表切分为连续的时段组；[units] 为空时回退为 [DefaultScheduleUnits]。 */
fun groupScheduleUnitsByDayPart(units: List<ScheduleUnit>): List<ScheduleDayPartGroup> {
    val effective = units.ifEmpty { DefaultScheduleUnits }
    val groups = mutableListOf<ScheduleDayPartGroup>()
    var currentPart = effective.first().dayPart
    var currentUnits = mutableListOf<ScheduleUnit>()
    for (unit in effective) {
        if (unit.dayPart == currentPart) {
            currentUnits += unit
        } else {
            groups += ScheduleDayPartGroup(dayPart = currentPart, units = currentUnits)
            currentPart = unit.dayPart
            currentUnits = mutableListOf(unit)
        }
    }
    groups += ScheduleDayPartGroup(dayPart = currentPart, units = currentUnits)
    return groups
}

/** 查找第 [period] 节的展示短名；未定义时回退为节次序号文本。 */
fun List<ScheduleUnit>.labelOf(period: Int): String =
    firstOrNull { it.index == period }?.label ?: period.toString()

/**
 * 展示到第 [lastPeriod] 节时一天各段的节次。
 *
 * 按 [units] 的时段分段（为空时回退至常规 10 节作息）；教务排课超出作息表末尾节次时，最后一段延伸到 [lastPeriod]。
 */
fun dayPartsThrough(lastPeriod: Int, units: List<ScheduleUnit> = emptyList()): List<IntRange> {
    val ranges = groupScheduleUnitsByDayPart(units).map(ScheduleDayPartGroup::range)
    val evening = ranges.last()
    return ranges.dropLast(1) + listOf(evening.first..maxOf(evening.last, lastPeriod))
}

/** [date] 当天的一节课，属于教学班 [classId]，从第 [startPeriod] 节起连续占用 [periodSpan] 节（节次从 1 数起）。 */
@Serializable
data class TimetableEntry(
    val classId: Long,
    val day: DayOfWeek,
    val date: LocalDate,
    val startPeriod: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val title: String,
    val teacher: String,
    val location: String? = null,
    val periodSpan: Int = 1,
) {
    val lastPeriod: Int
        get() = startPeriod + periodSpan - 1

    val startsAt: LocalDateTime
        get() = date.atTime(startTime)

    val endsAt: LocalDateTime
        get() = date.atTime(endTime)
}

/**
 * 用户在一组时段冲突的课程里选定放在最上层的那门：[classIds] 是这组课的教学班，[frontClassId] 是其中置顶的那个。
 *
 * 选择按教学班记录，不按某一周记录，所以同一组课每周冲突时都沿用它。教学班只属于一个学期，换学期后自然不再匹配。
 */
@Serializable
data class ConflictChoice(val classIds: Set<Long>, val frontClassId: Long)

/**
 * [conflict] 这组冲突课程里置顶的那门，没有适用的选择时为 null。
 *
 * 一个选择要适用于这组课，须涵盖这组课的全部教学班，且它置顶的那门就在这组课里。同一组课在不同的周
 * 可能与更多课程冲突，按这条规则，在更大的冲突里做的选择也覆盖较小的那组。几个选择都适用时，以最后做的为准。
 */
fun List<ConflictChoice>.frontIn(conflict: List<TimetableEntry>): TimetableEntry? {
    val classIds = conflict.mapTo(HashSet()) { it.classId }
    val choice = lastOrNull { it.frontClassId in classIds && it.classIds.containsAll(classIds) } ?: return null
    return conflict.first { it.classId == choice.frontClassId }
}

/** 把 [front] 置顶在 [conflict] 这组课里：替换这组课原有的选择，新选择排在末尾，成为最后做的那个。 */
fun List<ConflictChoice>.withFront(conflict: List<TimetableEntry>, front: TimetableEntry): List<ConflictChoice> {
    val classIds = conflict.mapTo(HashSet()) { it.classId }
    require(front.classId in classIds) { "The front entry must belong to the conflict" }
    return filterNot { it.classIds == classIds } + ConflictChoice(classIds, front.classId)
}

@Serializable
data class TimetableWeek(
    val number: Int,
    val dates: List<LocalDate>,
    val entries: List<TimetableEntry> = emptyList(),
)

/** [isLoaded] 为 false 时 [weeks] 只有日期，课程为空只代表还不知道，不代表没有课。 */
@Serializable
data class TimetableTerm(
    val id: String,
    val schoolYear: String,
    val number: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val weeks: List<TimetableWeek>,
    val units: List<ScheduleUnit> = emptyList(),
    val isLoaded: Boolean = false,
) {
    /** 课表要展示的节次数：完整的一天，有更晚的课时延伸到最晚的那一节。 */
    val periodCount: Int
        get() {
            val latestPeriod = weeks.flatMap { it.entries }.maxOfOrNull { it.lastPeriod } ?: 0
            return dayPartsThrough(latestPeriod, units).last().last
        }
}

/** 截至 [today] 已开始的最新学期，这样学期结束后的假期也有对应学期。 */
fun List<TimetableTerm>.defaultTerm(today: LocalDate): TimetableTerm? =
    filter { it.startDate <= today }.maxByOrNull { it.startDate } ?: firstOrNull()

data class ClassPeriods(val startPeriod: Int, val periodSpan: Int) {
    val lastPeriod: Int
        get() = startPeriod + periodSpan - 1

    operator fun contains(period: Int): Boolean = period in startPeriod..lastPeriod
}

/** 今天和明天接下来的课程安排，有课时是 [NextClass]。 */
sealed interface UpcomingClass {
    /** 课程还没加载，不知道这两天有没有课。 */
    data object NotFetched : UpcomingClass

    data object None : UpcomingClass
}

data class NextClass(
    val name: String,
    val location: String,
    val startTime: LocalTime,
    val periods: ClassPeriods,
    val dayClasses: List<ClassPeriods>,
    val isTomorrow: Boolean,
    val dayParts: List<IntRange> = dayPartsThrough(dayClasses.maxOfOrNull { it.lastPeriod } ?: 0),
) : UpcomingClass

/**
 * 这些学期中今天 [now] 之后和明天全天的第一节课。几门课同时开始时，取用户在 [conflictChoices] 里置顶的那门；
 * 没有置顶，就取教务数据里排在前面的那门。
 */
fun List<TimetableTerm>.upcomingClassAfter(
    now: LocalDateTime,
    conflictChoices: List<ConflictChoice>,
): UpcomingClass {
    val today = now.date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)
    val days = setOf(today, tomorrow)
    val termsOfDays = filter { term -> term.weeks.any { week -> week.dates.any { it in days } } }
    if (isEmpty() || !termsOfDays.all { it.isLoaded }) return UpcomingClass.NotFetched

    val entries = termsOfDays.flatMap { term -> term.weeks.flatMap { it.entries } }
    val laterEntries = entries.filter { it.date in days && it.startsAt > now }
    val firstStart = laterEntries.minOfOrNull { it.startsAt } ?: return UpcomingClass.None
    val firstEntries = laterEntries.filter { it.startsAt == firstStart }
    val upcoming = conflictChoices.frontIn(firstEntries) ?: firstEntries.first()
    val dayClasses = entries.filter { it.date == upcoming.date }.map { it.periods }
    val dayTerm = termsOfDays.find { term -> term.weeks.any { upcoming in it.entries } }
    return NextClass(
        name = upcoming.title,
        location = upcoming.location.orEmpty(),
        startTime = upcoming.startTime,
        periods = upcoming.periods,
        dayClasses = dayClasses,
        isTomorrow = upcoming.date == tomorrow,
        dayParts = dayPartsThrough(
            lastPeriod = dayClasses.maxOf { it.lastPeriod },
            units = dayTerm?.units.orEmpty(),
        ),
    )
}

private val TimetableEntry.periods: ClassPeriods
    get() = ClassPeriods(startPeriod, periodSpan)

internal fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

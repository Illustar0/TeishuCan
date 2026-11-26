package com.illustar0.teishucan.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * 作息表里上午、下午、晚上各自的节次。
 *
 * 教务系统允许把课排到晚上最后一节之后，那些节次也算作晚上。
 */
private val DayParts = listOf(1..4, 5..8, 9..10)

/** 展示到第 [lastPeriod] 节时一天各段的节次。不足一整天时仍给出完整的作息，晚上延伸到 [lastPeriod]。 */
fun dayPartsThrough(lastPeriod: Int): List<IntRange> {
    val evening = DayParts.last()
    return DayParts.dropLast(1) + listOf(evening.first..maxOf(evening.last, lastPeriod))
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
    val isLoaded: Boolean = false,
) {
    /** 课表要展示的节次数：完整的一天，有更晚的课时延伸到最晚的那一节。 */
    val periodCount: Int
        get() {
            val latestPeriod = weeks.flatMap { it.entries }.maxOfOrNull { it.lastPeriod } ?: 0
            return dayPartsThrough(latestPeriod).last().last
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
    return NextClass(
        name = upcoming.title,
        location = upcoming.location.orEmpty(),
        startTime = upcoming.startTime,
        periods = upcoming.periods,
        dayClasses = entries.filter { it.date == upcoming.date }.map { it.periods },
        isTomorrow = upcoming.date == tomorrow,
    )
}

private val TimetableEntry.startsAt: LocalDateTime
    get() = date.atTime(startTime)

private val TimetableEntry.periods: ClassPeriods
    get() = ClassPeriods(startPeriod, periodSpan)

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

/** [date] 当天的一节课，从第 [startPeriod] 节（从 1 开始）起连续占用 [periodSpan] 节。 */
@Serializable
data class TimetableEntry(
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

/** 这些学期中今天 [now] 之后和明天全天的第一节课。 */
fun List<TimetableTerm>.upcomingClassAfter(now: LocalDateTime): UpcomingClass {
    val today = now.date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)
    val days = setOf(today, tomorrow)
    val termsOfDays = filter { term -> term.weeks.any { week -> week.dates.any { it in days } } }
    if (isEmpty() || !termsOfDays.all { it.isLoaded }) return UpcomingClass.NotFetched

    val entries = termsOfDays.flatMap { term -> term.weeks.flatMap { it.entries } }
    val upcoming = entries
        .filter { it.date in days && it.startsAt > now }
        .minByOrNull { it.startsAt }
        ?: return UpcomingClass.None
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

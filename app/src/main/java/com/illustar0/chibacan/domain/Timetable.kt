package com.illustar0.chibacan.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 课表中所有日期与时刻所在的时区，与设备当前所在地无关。 */
val CampusZone: ZoneId = ZoneId.of("Asia/Shanghai")

/** [date] 当天的一节课，从第 [startPeriod] 节（从 1 开始）起连续占用 [periodSpan] 节。 */
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
)

/** 学期的第 [number] 周及其覆盖的 [dates]，每天一个日期。 */
data class TimetableWeek(
    val number: Int,
    val dates: List<LocalDate>,
    val entries: List<TimetableEntry> = emptyList(),
)

/**
 * 可选的学期，即 [schoolYear] 学年的第 [number] 学期，[weeks] 按校历排列。
 *
 * [isLoaded] 为 false 时 [weeks] 只有日期，课程为空只代表还不知道，不代表没有课。
 */
data class TimetableTerm(
    val id: String,
    val schoolYear: String,
    val number: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val weeks: List<TimetableWeek>,
    val isLoaded: Boolean = false,
)

/** 截至 [today] 已开始的最新学期，这样学期结束后的假期也有对应学期。 */
fun List<TimetableTerm>.defaultTerm(today: LocalDate): TimetableTerm? =
    filter { it.startDate <= today }.maxByOrNull { it.startDate } ?: firstOrNull()

/** 一门课占用的节次：从第 [startPeriod] 节（从 1 开始）起连续 [periodSpan] 节。 */
data class ClassPeriods(val startPeriod: Int, val periodSpan: Int) {
    operator fun contains(period: Int): Boolean = period in startPeriod until startPeriod + periodSpan
}

/** 今天和明天接下来的课程安排，有课时是 [NextClass]。 */
sealed interface UpcomingClass {
    /** 课程还没加载，不知道这两天有没有课。 */
    data object NotFetched : UpcomingClass

    /** 这两天都没有课了。 */
    data object None : UpcomingClass
}

/** 下一节课，占用 [periods]，[isTomorrow] 时在明天；[dayClasses] 列出当天所有课程。 */
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
    val today = now.toLocalDate()
    val tomorrow = today.plusDays(1)
    val days = setOf(today, tomorrow)
    val termsOfDays = filter { term -> term.weeks.any { week -> week.dates.any { it in days } } }
    if (isEmpty() || !termsOfDays.all { it.isLoaded }) return UpcomingClass.NotFetched

    val entries = termsOfDays.flatMap { term -> term.weeks.flatMap { it.entries } }
    val upcoming = entries
        .filter { it.date in days && it.startsAt.isAfter(now) }
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

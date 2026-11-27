package com.illustar0.teishucan.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetablePeriodsTest {
    private val monday = LocalDate(2026, 9, 7)

    private val extended14Units: List<ScheduleUnit> = listOf(
        ScheduleUnit(1, "1", ScheduleDayPart.Morning, LocalTime(8, 0), LocalTime(8, 45)),
        ScheduleUnit(2, "2", ScheduleDayPart.Morning, LocalTime(8, 55), LocalTime(9, 40)),
        ScheduleUnit(3, "3", ScheduleDayPart.Morning, LocalTime(10, 10), LocalTime(10, 55)),
        ScheduleUnit(4, "4", ScheduleDayPart.Morning, LocalTime(11, 5), LocalTime(11, 50)),
        ScheduleUnit(5, "+1", ScheduleDayPart.Noon, LocalTime(12, 0), LocalTime(12, 45)),
        ScheduleUnit(6, "+2", ScheduleDayPart.Noon, LocalTime(12, 55), LocalTime(13, 40)),
        ScheduleUnit(7, "5", ScheduleDayPart.Afternoon, LocalTime(14, 0), LocalTime(14, 45)),
        ScheduleUnit(8, "6", ScheduleDayPart.Afternoon, LocalTime(14, 55), LocalTime(15, 40)),
        ScheduleUnit(9, "7", ScheduleDayPart.Afternoon, LocalTime(16, 0), LocalTime(16, 45)),
        ScheduleUnit(10, "8", ScheduleDayPart.Afternoon, LocalTime(16, 55), LocalTime(17, 40)),
        ScheduleUnit(11, "+3", ScheduleDayPart.Afternoon, LocalTime(17, 50), LocalTime(18, 35)),
        ScheduleUnit(12, "9", ScheduleDayPart.Evening, LocalTime(19, 0), LocalTime(19, 45)),
        ScheduleUnit(13, "10", ScheduleDayPart.Evening, LocalTime(19, 55), LocalTime(20, 40)),
        ScheduleUnit(14, "+4", ScheduleDayPart.Evening, LocalTime(20, 50), LocalTime(21, 35)),
    )

    private fun entry(
        startPeriod: Int,
        periodSpan: Int,
        startTime: LocalTime = LocalTime(8, 0),
        endTime: LocalTime = LocalTime(9, 40),
    ) = TimetableEntry(
        classId = startPeriod.toLong(),
        day = DayOfWeek.MONDAY,
        date = monday,
        startPeriod = startPeriod,
        startTime = startTime,
        endTime = endTime,
        title = "Course $startPeriod",
        teacher = "",
        periodSpan = periodSpan,
    )

    private fun term(
        vararg entries: TimetableEntry,
        units: List<ScheduleUnit> = emptyList(),
    ) = TimetableTerm(
        id = "1",
        schoolYear = "2026-2027",
        number = "1",
        startDate = monday,
        endDate = monday.plus(1, DateTimeUnit.WEEK),
        weeks = listOf(TimetableWeek(number = 1, dates = listOf(monday), entries = entries.toList())),
        units = units,
        isLoaded = true,
    )

    @Test
    fun dayPartsThrough_withinStandardDay_keepsWholeDay() {
        assertEquals(listOf(1..4, 5..8, 9..10), dayPartsThrough(lastPeriod = 2))
    }

    @Test
    fun dayPartsThrough_laterPeriod_extendsEvening() {
        assertEquals(listOf(1..4, 5..8, 9..12), dayPartsThrough(lastPeriod = 12))
    }

    @Test
    fun dayPartsThrough_extended14UnitSchedule_splitsMorningNoonAfternoonEvening() {
        assertEquals(
            listOf(1..4, 5..6, 7..11, 12..14),
            dayPartsThrough(lastPeriod = 8, units = extended14Units),
        )
        assertEquals(
            listOf(1..4, 5..6, 7..11, 12..15),
            dayPartsThrough(lastPeriod = 15, units = extended14Units),
        )
    }

    @Test
    fun periodCount_withoutLateClasses_showsWholeDay() {
        assertEquals(10, term().periodCount)
        assertEquals(10, term(entry(startPeriod = 1, periodSpan = 2)).periodCount)
        assertEquals(14, term(entry(startPeriod = 5, periodSpan = 2), units = extended14Units).periodCount)
    }

    @Test
    fun periodCount_classEndingAfterStandardDay_growsToItsLastPeriod() {
        val lateClass = entry(startPeriod = 10, periodSpan = 2)

        assertEquals(11, term(entry(startPeriod = 1, periodSpan = 2), lateClass).periodCount)
    }

    @Test
    fun upcomingClassAfter_termWithNoonUnits_populatesFourSegmentDayParts() {
        val noonClass = entry(
            startPeriod = 5,
            periodSpan = 2,
            startTime = LocalTime(12, 0),
            endTime = LocalTime(13, 40),
        )
        val afternoonClass = entry(
            startPeriod = 7,
            periodSpan = 2,
            startTime = LocalTime(14, 0),
            endTime = LocalTime(15, 40),
        )
        val loadedTerm = term(noonClass, afternoonClass, units = extended14Units)

        val upcoming = listOf(loadedTerm).upcomingClassAfter(
            now = LocalDateTime(2026, 9, 7, 12, 30),
            conflictChoices = emptyList(),
        )

        assertTrue(upcoming is NextClass)
        val next = upcoming as NextClass
        assertEquals("Course 7", next.name)
        assertEquals(listOf(1..4, 5..6, 7..11, 12..14), next.dayParts)
    }
}

package com.illustar0.teishucan.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetablePeriodsTest {
    private val monday = LocalDate.of(2026, 9, 7)

    private fun entry(startPeriod: Int, periodSpan: Int) = TimetableEntry(
        day = DayOfWeek.MONDAY,
        date = monday,
        startPeriod = startPeriod,
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(9, 50),
        title = "Course",
        teacher = "",
        periodSpan = periodSpan,
    )

    private fun term(vararg entries: TimetableEntry) = TimetableTerm(
        id = "1",
        schoolYear = "2026-2027",
        number = "1",
        startDate = monday,
        endDate = monday.plusWeeks(1),
        weeks = listOf(TimetableWeek(number = 1, dates = listOf(monday), entries = entries.toList())),
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
    fun periodCount_withoutLateClasses_showsWholeDay() {
        assertEquals(10, term().periodCount)
        assertEquals(10, term(entry(startPeriod = 1, periodSpan = 2)).periodCount)
    }

    @Test
    fun periodCount_classEndingAfterStandardDay_growsToItsLastPeriod() {
        val lateClass = entry(startPeriod = 10, periodSpan = 2)

        assertEquals(11, term(entry(startPeriod = 1, periodSpan = 2), lateClass).periodCount)
    }
}

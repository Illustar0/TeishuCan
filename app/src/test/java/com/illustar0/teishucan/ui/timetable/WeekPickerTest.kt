package com.illustar0.teishucan.ui.timetable

import com.illustar0.teishucan.domain.TimetableWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPickerTest {
    private fun sampleWeek(number: Int, monday: LocalDate): TimetableWeek = TimetableWeek(
        number = number,
        dates = List(7) { offset -> monday.plus(offset, DateTimeUnit.DAY) },
        entries = emptyList(),
    )

    @Test
    fun formatCompactWeekRange_omitsRepeatedMonthWithinSameMonthAndKeepsCrossMonth() {
        assertEquals(
            "11.23–29",
            formatCompactWeekRange(start = LocalDate(2026, 11, 23), end = LocalDate(2026, 11, 29)),
        )
        assertEquals(
            "11.30–12.6",
            formatCompactWeekRange(start = LocalDate(2026, 11, 30), end = LocalDate(2026, 12, 6)),
        )
        assertEquals(
            "12.28–1.3",
            formatCompactWeekRange(start = LocalDate(2026, 12, 28), end = LocalDate(2027, 1, 3)),
        )
    }

    @Test
    fun intermediateWeekPageForJump_onlyReturnsAdjacentTargetForMultiWeekJump() {
        assertNull(intermediateWeekPageForJump(currentPage = 11, targetPage = 11))
        assertNull(intermediateWeekPageForJump(currentPage = 11, targetPage = 12))
        assertNull(intermediateWeekPageForJump(currentPage = 11, targetPage = 10))

        assertEquals(16, intermediateWeekPageForJump(currentPage = 0, targetPage = 17))
        assertEquals(12, intermediateWeekPageForJump(currentPage = 14, targetPage = 11))
    }

    @Test
    fun shouldShowBackToCurrentWeek_isTrueOnlyWhenViewingDifferentWeekInActiveTerm() {
        val week12 = sampleWeek(number = 12, monday = LocalDate(2026, 11, 23))
        val week15 = sampleWeek(number = 15, monday = LocalDate(2026, 12, 14))

        assertFalse(shouldShowBackToCurrentWeek(selectedWeek = week12, currentWeek = week12))
        assertFalse(shouldShowBackToCurrentWeek(selectedWeek = week15, currentWeek = null))
        assertFalse(shouldShowBackToCurrentWeek(selectedWeek = null, currentWeek = week12))
        assertTrue(shouldShowBackToCurrentWeek(selectedWeek = week15, currentWeek = week12))
    }
}

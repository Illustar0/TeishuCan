package com.illustar0.teishucan.ui.classrooms

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassroomDatePagerTest {
    @Test
    fun pageIndexRoundTrip_alignsWithCalendarRangeAndClampsBounds() {
        assertEquals(0, ClassroomPagerBaseDate.toClassroomPageIndex())
        assertEquals(ClassroomPagerBaseDate, classroomDateAtPage(0))

        assertEquals(TotalClassroomPagerDays - 1, ClassroomPagerEndDate.toClassroomPageIndex())
        assertEquals(ClassroomPagerEndDate, classroomDateAtPage(TotalClassroomPagerDays - 1))

        // 超出边界时安全夹紧到首尾页。
        assertEquals(0, LocalDate(1969, 12, 31).toClassroomPageIndex())
        assertEquals(TotalClassroomPagerDays - 1, LocalDate(2170, 1, 1).toClassroomPageIndex())
        assertEquals(ClassroomPagerBaseDate, classroomDateAtPage(-10))
        assertEquals(ClassroomPagerEndDate, classroomDateAtPage(TotalClassroomPagerDays + 10))
    }

    @Test
    fun pageIndex_remainsContiguousAcrossLeapDayAndYearBoundary() {
        val feb28 = LocalDate(2024, 2, 28)
        val feb29 = LocalDate(2024, 2, 29)
        val mar1 = LocalDate(2024, 3, 1)

        assertEquals(feb28.toClassroomPageIndex() + 1, feb29.toClassroomPageIndex())
        assertEquals(feb29.toClassroomPageIndex() + 1, mar1.toClassroomPageIndex())
        assertEquals(feb29, classroomDateAtPage(feb29.toClassroomPageIndex()))

        val dec31 = LocalDate(2026, 12, 31)
        val jan1 = LocalDate(2027, 1, 1)
        assertEquals(dec31.toClassroomPageIndex() + 1, jan1.toClassroomPageIndex())
        assertEquals(jan1, classroomDateAtPage(jan1.toClassroomPageIndex()))
    }

    @Test
    fun intermediatePageForJump_onlyStepsAdjacentBeforeMultiDaySlide() {
        assertNull(intermediatePageForJump(currentPage = 100, targetPage = 100))
        assertNull(intermediatePageForJump(currentPage = 100, targetPage = 101))
        assertNull(intermediatePageForJump(currentPage = 100, targetPage = 99))

        assertEquals(119, intermediatePageForJump(currentPage = 100, targetPage = 120))
        assertEquals(81, intermediatePageForJump(currentPage = 100, targetPage = 80))
    }
}

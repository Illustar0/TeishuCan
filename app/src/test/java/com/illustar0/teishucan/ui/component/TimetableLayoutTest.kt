package com.illustar0.teishucan.ui.component

import com.illustar0.teishucan.domain.TimetableEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableLayoutTest {
    private val mondayDate = LocalDate.of(2026, 10, 12)

    private fun entry(
        day: DayOfWeek,
        startPeriod: Int,
        periodSpan: Int,
        title: String,
    ) = TimetableEntry(
        day = day,
        date = mondayDate.plusDays(day.ordinal.toLong()),
        startPeriod = startPeriod,
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(9, 40),
        title = title,
        teacher = "Teacher",
        location = "R101",
        periodSpan = periodSpan,
    )

    @Test
    fun nonOverlappingAndAdjacentEntries_renderAsFullWidthSingleCards() {
        val first = entry(DayOfWeek.MONDAY, startPeriod = 1, periodSpan = 2, title = "高等数学")
        val adjacent = entry(DayOfWeek.MONDAY, startPeriod = 3, periodSpan = 2, title = "大学英语")
        val otherDay = entry(DayOfWeek.TUESDAY, startPeriod = 1, periodSpan = 2, title = "线性代数")

        val cards = buildTimetableCards(listOf(adjacent, otherDay, first), periodCount = 10)
        val rects = layoutTimetableCards(
            cards = cards,
            dayWidthPx = 100f,
            rowHeightPx = 60f,
            gapPx = 4,
            stackStepPx = 8,
            cascadeIndentPx = 10,
        )

        assertEquals(3, cards.size)
        assertTrue(cards.all { it.kind == CardLayerKind.Single })
        assertTrue(cards.all { it.showsContent && !it.hasBorder })
        assertEquals(listOf("高等数学", "大学英语", "线性代数"), cards.map { it.entry.title })
        assertEquals(TimetableCardRect(x = 2, y = 2, width = 96, height = 116), rects[0])
        assertEquals(TimetableCardRect(x = 2, y = 122, width = 96, height = 116), rects[1])
        assertEquals(TimetableCardRect(x = 102, y = 2, width = 96, height = 116), rects[2])
    }

    @Test
    fun outOfBoundsEntries_areFilteredAndOverflowingSpanIsClamped() {
        val beforeStart = entry(DayOfWeek.MONDAY, startPeriod = 0, periodSpan = 2, title = "Invalid0")
        val afterEnd = entry(DayOfWeek.MONDAY, startPeriod = 11, periodSpan = 1, title = "Invalid11")
        val zeroSpan = entry(DayOfWeek.MONDAY, startPeriod = 1, periodSpan = 0, title = "InvalidSpan")
        val overflowing = entry(DayOfWeek.MONDAY, startPeriod = 9, periodSpan = 4, title = "晚课")

        val cards = buildTimetableCards(
            listOf(beforeStart, afterEnd, zeroSpan, overflowing),
            periodCount = 10,
        )

        assertEquals(1, cards.size)
        assertEquals("晚课", cards.single().entry.title)
        assertEquals(2, cards.single().clampedSpan)
    }

    @Test
    fun exactTwoCourseOverlap_stacksFrontAtBottomRightWithBackAtTopLeft() {
        val courseA = entry(DayOfWeek.TUESDAY, startPeriod = 3, periodSpan = 2, title = "操作系统")
        val courseB = entry(DayOfWeek.TUESDAY, startPeriod = 3, periodSpan = 2, title = "数据结构")

        val cards = buildTimetableCards(listOf(courseA, courseB), periodCount = 10)
        val rects = layoutTimetableCards(
            cards = cards,
            dayWidthPx = 100f,
            rowHeightPx = 60f,
            gapPx = 4,
            stackStepPx = 8,
            cascadeIndentPx = 10,
        )

        assertEquals(2, cards.size)
        assertFalse(cards[0].showsContent)
        assertTrue(cards[1].showsContent)
        assertEquals(1, cards[0].depthFromFront)
        assertEquals(0, cards[1].depthFromFront)
        assertEquals(listOf(courseA, courseB), cards[1].clickEntries)

        // 背卡贴左上 (102, 122)，前卡向右下步进 8px (110, 130)，两张共享缩减尺寸 88x108。
        assertEquals(TimetableCardRect(x = 102, y = 122, width = 88, height = 108), rects[0])
        assertEquals(TimetableCardRect(x = 110, y = 130, width = 88, height = 108), rects[1])
    }

    @Test
    fun exactThreeCourseOverlap_producesThreeDistinctSteppedCards() {
        val c1 = entry(DayOfWeek.THURSDAY, startPeriod = 5, periodSpan = 2, title = "计算机网络")
        val c2 = entry(DayOfWeek.THURSDAY, startPeriod = 5, periodSpan = 2, title = "创新创业导论")
        val c3 = entry(DayOfWeek.THURSDAY, startPeriod = 5, periodSpan = 2, title = "ACM 集训")

        val cards = buildTimetableCards(listOf(c1, c2, c3), periodCount = 10)
        val rects = layoutTimetableCards(
            cards = cards,
            dayWidthPx = 100f,
            rowHeightPx = 60f,
            gapPx = 4,
            stackStepPx = 8,
            cascadeIndentPx = 10,
        )

        assertEquals(3, cards.size)
        assertEquals(listOf(2, 1, 0), cards.map { it.depthFromFront })
        assertEquals(listOf(false, false, true), cards.map { it.showsContent })

        // 三张卡片共享同一尺寸 (80x100)，依次向右下步进 8px，确保每一张都在左上边缘露出。
        assertEquals(TimetableCardRect(x = 302, y = 242, width = 80, height = 100), rects[0])
        assertEquals(TimetableCardRect(x = 310, y = 250, width = 80, height = 100), rects[1])
        assertEquals(TimetableCardRect(x = 318, y = 258, width = 80, height = 100), rects[2])
    }

    @Test
    fun exactFourCourseOverlap_capsVisualLayersAtThreeWhileKeepingAllClickEntries() {
        val courses = (1..4).map { idx ->
            entry(DayOfWeek.THURSDAY, startPeriod = 5, periodSpan = 2, title = "课程$idx")
        }

        val cards = buildTimetableCards(courses, periodCount = 10)

        assertEquals(3, cards.size)
        assertEquals(courses, cards.last().clickEntries)
    }

    @Test
    fun nestedPartialOverlap_placesLongerBehindAndShorterInFrontWithClickedFirst() {
        val longLab = entry(DayOfWeek.WEDNESDAY, startPeriod = 1, periodSpan = 4, title = "大学物理实验")
        val shortElective = entry(DayOfWeek.WEDNESDAY, startPeriod = 3, periodSpan = 2, title = "形势与政策")

        val cards = buildTimetableCards(listOf(shortElective, longLab), periodCount = 10)
        val rects = layoutTimetableCards(
            cards = cards,
            dayWidthPx = 100f,
            rowHeightPx = 60f,
            gapPx = 4,
            stackStepPx = 8,
            cascadeIndentPx = 12,
        )

        assertEquals(2, cards.size)
        assertTrue(cards.all { it.kind == CardLayerKind.PartialCascade && it.showsContent && it.hasBorder })
        assertEquals("大学物理实验", cards[0].entry.title)
        assertEquals("形势与政策", cards[1].entry.title)
        assertEquals(listOf(longLab, shortElective), cards[0].clickEntries)
        assertEquals(listOf(shortElective, longLab), cards[1].clickEntries)
        assertEquals(TimetableCardRect(x = 202, y = 2, width = 96, height = 236), rects[0])
        assertEquals(TimetableCardRect(x = 214, y = 122, width = 84, height = 116), rects[1])
    }

    @Test
    fun staggeredOverlap_preservesEachCoursePeriodBoundsAndStepsLaterForward() {
        val earlier = entry(DayOfWeek.FRIDAY, startPeriod = 7, periodSpan = 2, title = "软件工程")
        val later = entry(DayOfWeek.FRIDAY, startPeriod = 8, periodSpan = 2, title = "古典音乐鉴赏")

        val cards = buildTimetableCards(listOf(later, earlier), periodCount = 10)
        val rects = layoutTimetableCards(
            cards = cards,
            dayWidthPx = 100f,
            rowHeightPx = 60f,
            gapPx = 4,
            stackStepPx = 8,
            cascadeIndentPx = 12,
        )

        assertEquals(2, cards.size)
        assertEquals("软件工程", cards[0].entry.title)
        assertEquals("古典音乐鉴赏", cards[1].entry.title)
        assertEquals(TimetableCardRect(x = 402, y = 362, width = 96, height = 116), rects[0])
        assertEquals(TimetableCardRect(x = 414, y = 422, width = 84, height = 116), rects[1])
    }

    @Test
    fun disjointSubEntriesOverlappingSharedLongCourse_reuseFirstCascadeLane() {
        val longCourse = entry(DayOfWeek.WEDNESDAY, startPeriod = 1, periodSpan = 4, title = "长实验")
        val topShort = entry(DayOfWeek.WEDNESDAY, startPeriod = 1, periodSpan = 2, title = "前两节")
        val bottomShort = entry(DayOfWeek.WEDNESDAY, startPeriod = 3, periodSpan = 2, title = "后两节")

        val cards = buildTimetableCards(listOf(longCourse, topShort, bottomShort), periodCount = 10)

        assertEquals(3, cards.size)
        assertEquals(0, cards[0].stepIndex)
        assertEquals(1, cards[1].stepIndex)
        assertEquals(1, cards[2].stepIndex)
        assertEquals(1, cards[0].totalSteps)
    }

    @Test
    fun narrowColumn_clampsStackInsetToPreserveReadableCardWidth() {
        val courses = (1..3).map { idx ->
            entry(DayOfWeek.MONDAY, startPeriod = 1, periodSpan = 2, title = "课$idx")
        }
        val cards = buildTimetableCards(courses, periodCount = 10)
        val rects = layoutTimetableCards(
            cards = cards,
            dayWidthPx = 44f,
            rowHeightPx = 60f,
            gapPx = 4,
            stackStepPx = 12,
            cascadeIndentPx = 12,
        )

        // 单列可用宽 40px，25% 上限 = 10px，每层步进 5px，前卡仍保留 30px 宽度。
        assertEquals(TimetableCardRect(x = 2, y = 2, width = 30, height = 106), rects[0])
        assertEquals(TimetableCardRect(x = 7, y = 7, width = 30, height = 106), rects[1])
        assertEquals(TimetableCardRect(x = 12, y = 12, width = 30, height = 106), rects[2])
    }
}

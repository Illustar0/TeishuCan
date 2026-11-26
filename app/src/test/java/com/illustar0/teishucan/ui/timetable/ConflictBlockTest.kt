package com.illustar0.teishucan.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictBlockTest {
    private val corner = 16f

    @Test
    fun conflictBlockTargets_stacksExpandedThenTitleThenRowsInOriginalOrder() {
        val frames = conflictBlockTargets(
            expandedIndex = 1,
            courseHeights = listOf(70, 300, 80),
            titleHeight = 40,
            cornerPx = corner,
        )

        assertEquals(BlockFrame(y = 0, height = 300, topCorner = corner, bottomCorner = corner), frames[1])
        assertEquals(BlockFrame(y = 300, height = 40), frames[ConflictLayoutKey.OthersTitle])
        assertEquals(BlockFrame(y = 340, height = 70, topCorner = corner, bottomCorner = 0f), frames[0])
        assertEquals(BlockFrame(y = 410, height = 80, topCorner = 0f, bottomCorner = corner), frames[2])
        assertEquals(BlockFrame(y = 0, height = 490), frames[ConflictLayoutKey.Whole])
    }

    @Test
    fun conflictBlockTargets_singleRow_isRoundedOnBothEdges() {
        val frames = conflictBlockTargets(
            expandedIndex = 0,
            courseHeights = listOf(300, 70),
            titleHeight = 40,
            cornerPx = corner,
        )

        assertEquals(BlockFrame(y = 340, height = 70, topCorner = corner, bottomCorner = corner), frames[1])
    }

    @Test
    fun morphFrame_movesPositionSizeAndCornersTogether() {
        val expanded = BlockFrame(y = 0, height = 300, topCorner = corner, bottomCorner = corner)
        val firstRow = BlockFrame(y = 340, height = 70, topCorner = corner, bottomCorner = 0f)

        val frame = morphFrame(start = expanded, target = firstRow, progress = 0.5f)

        assertEquals(BlockFrame(y = 170, height = 185, topCorner = corner, bottomCorner = 8f), frame)
    }
}

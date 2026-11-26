package com.illustar0.teishucan.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class RestackTest {
    private val back = CardFrame(
        rect = TimetableCardRect(x = 0, y = 0, width = 80, height = 100),
        zIndex = 0f,
        contentAlpha = 0f,
    )
    private val front = CardFrame(
        rect = TimetableCardRect(x = 8, y = 8, width = 80, height = 100),
        zIndex = 1f,
        contentAlpha = 1f,
    )

    @Test
    fun restackFrame_beforeHalfway_keepsTheOldLayer() {
        val frame = restackFrame(start = back, target = front, progress = 0.49f)

        assertEquals(back.zIndex, frame.zIndex)
    }

    @Test
    fun restackFrame_fromHalfway_takesTheNewLayer() {
        val frame = restackFrame(start = back, target = front, progress = 0.5f)

        assertEquals(front.zIndex, frame.zIndex)
        assertEquals(TimetableCardRect(x = 4, y = 4, width = 80, height = 100), frame.rect)
    }

    @Test
    fun restackAlpha_cardMovingToFront_showsTextOnlyAfterHalfway() {
        assertEquals(0f, restackAlpha(start = 0f, target = 1f, progress = 0.5f))
        assertEquals(1f, restackAlpha(start = 0f, target = 1f, progress = 1f))
    }

    @Test
    fun restackAlpha_cardMovingBack_hidesTextByHalfway() {
        assertEquals(1f, restackAlpha(start = 1f, target = 0f, progress = 0f))
        assertEquals(0f, restackAlpha(start = 1f, target = 0f, progress = 0.5f))
    }

    @Test
    fun restackAlpha_unchangedCard_keepsItsVisibility() {
        assertEquals(1f, restackAlpha(start = 1f, target = 1f, progress = 0.3f))
        assertEquals(0f, restackAlpha(start = 0f, target = 0f, progress = 0.3f))
    }

    @Test
    fun restackAlpha_interruptedFadeIn_continuesFromTheCurrentAlpha() {
        assertEquals(0.7f, restackAlpha(start = 0.4f, target = 1f, progress = 0.75f), 1e-6f)
    }
}

package com.illustar0.teishucan.ui.component.liquid

import org.junit.Assert.assertEquals
import org.junit.Test

class LensTest {
    @Test
    fun clampLensReach_withinHalfShortestSide_keepsRequestedValue() {
        assertEquals(72f, clampLensReach(requested = 72f, minDimension = 192f), 0f)
        assertEquals(72f, clampLensReach(requested = 72f, minDimension = 144f), 0f)
    }

    @Test
    fun clampLensReach_exceedingHalfShortestSide_clampsToHalfMinDimension() {
        assertEquals(42f, clampLensReach(requested = 72f, minDimension = 84f), 0f)
    }

    @Test
    fun clampLensReach_nonPositiveOrUnspecifiedInputs_returnsZero() {
        assertEquals(0f, clampLensReach(requested = 0f, minDimension = 144f), 0f)
        assertEquals(0f, clampLensReach(requested = -12f, minDimension = 144f), 0f)
        assertEquals(0f, clampLensReach(requested = 72f, minDimension = 0f), 0f)
        assertEquals(0f, clampLensReach(requested = 72f, minDimension = -10f), 0f)
        assertEquals(0f, clampLensReach(requested = Float.NaN, minDimension = 144f), 0f)
        assertEquals(0f, clampLensReach(requested = 72f, minDimension = Float.NaN), 0f)
    }
}

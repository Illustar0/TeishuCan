package com.illustar0.teishucan.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CasDeviceIdTest {
    @Test
    fun casDeviceId_androidId_keepsTheReleasedFormat() {
        assertEquals("TeishuCan(0123456789abcdef)", casDeviceId("0123456789abcdef"))
    }
}

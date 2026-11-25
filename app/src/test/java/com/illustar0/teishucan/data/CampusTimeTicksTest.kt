package com.illustar0.teishucan.data

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CampusTimeTicksTest {
    /** 从 [start] 起随虚拟时间前进的时钟，测试不必真的等待。 */
    private fun TestScope.virtualClock(start: LocalDateTime): Clock {
        val startInstant = start.atZone(CampusZone).toInstant()
        return object : Clock() {
            override fun getZone(): ZoneId = CampusZone

            override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException()

            override fun instant(): Instant = startInstant.plusMillis(testScheduler.currentTime)
        }
    }

    @Test
    fun campusTimeTicks_minutes_emitsNowThenAtEachMinuteStart() = runTest {
        val clock = virtualClock(LocalDateTime.of(2026, 9, 7, 8, 59, 30))

        val ticks = campusTimeTicks(ChronoUnit.MINUTES, clock).take(3).toList()

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 9, 7, 8, 59, 30),
                LocalDateTime.of(2026, 9, 7, 9, 0),
                LocalDateTime.of(2026, 9, 7, 9, 1),
            ),
            ticks,
        )
    }

    @Test
    fun campusTimeTicks_days_nextTickIsMidnight() = runTest {
        val clock = virtualClock(LocalDateTime.of(2026, 9, 7, 21, 30, 15))

        val ticks = campusTimeTicks(ChronoUnit.DAYS, clock).take(2).toList()

        assertEquals(LocalDateTime.of(2026, 9, 8, 0, 0), ticks.last())
    }
}

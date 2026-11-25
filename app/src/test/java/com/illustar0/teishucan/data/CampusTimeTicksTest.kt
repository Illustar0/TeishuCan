package com.illustar0.teishucan.data

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CampusTimeTicksTest {
    /** 从 [start] 起随虚拟时间前进的时钟，测试不必真的等待。 */
    private fun TestScope.virtualClock(start: LocalDateTime): Clock {
        val startInstant = start.toInstant(CampusZone)
        return object : Clock {
            override fun now(): Instant = startInstant + testScheduler.currentTime.milliseconds
        }
    }

    @Test
    fun campusMinuteTicks_emitsNowThenAtEachMinuteStart() = runTest {
        val clock = virtualClock(LocalDateTime(2026, 9, 7, 8, 59, 30))

        val ticks = campusMinuteTicks(clock).take(3).toList()

        assertEquals(
            listOf(
                LocalDateTime(2026, 9, 7, 8, 59, 30),
                LocalDateTime(2026, 9, 7, 9, 0),
                LocalDateTime(2026, 9, 7, 9, 1),
            ),
            ticks,
        )
    }

    @Test
    fun campusDateTicks_nextTickIsMidnight() = runTest {
        val clock = virtualClock(LocalDateTime(2026, 9, 7, 21, 30, 15))

        val ticks = campusDateTicks(clock).take(2).toList()

        assertEquals(listOf(LocalDate(2026, 9, 7), LocalDate(2026, 9, 8)), ticks)
        assertEquals(2.hours + 29.minutes + 45.seconds, testScheduler.currentTime.milliseconds)
    }
}

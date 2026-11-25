package com.illustar0.teishucan.data

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CampusTimeTicksTest {
    /** 从 [start] 起随虚拟时间前进的时钟，测试不必真的等待；[setBack] 模拟用户或校时把墙上时钟往回调。 */
    private class VirtualClock(start: LocalDateTime, private val scheduler: TestCoroutineScheduler) : Clock {
        private var origin = start.toInstant(CampusZone)

        override fun now(): Instant = origin + scheduler.currentTime.milliseconds

        fun setBack(amount: Duration) {
            origin -= amount
        }
    }

    private fun TestScope.virtualClock(start: LocalDateTime) = VirtualClock(start, testScheduler)

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
    fun campusMinuteTicks_followsClockSetBackWithinAMinute() = runTest {
        val clock = virtualClock(LocalDateTime(2026, 9, 7, 9, 0))
        val ticks = mutableListOf<LocalDateTime>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { campusMinuteTicks(clock).toList(ticks) }

        clock.setBack(1.hours)
        advanceTimeBy(1.minutes)
        runCurrent()

        assertEquals(listOf(LocalDateTime(2026, 9, 7, 9, 0), LocalDateTime(2026, 9, 7, 8, 1)), ticks)
    }

    @Test
    fun campusDateTicks_nextTickIsMidnight() = runTest {
        val clock = virtualClock(LocalDateTime(2026, 9, 7, 21, 30, 15))

        val ticks = campusDateTicks(clock).take(2).toList()

        assertEquals(listOf(LocalDate(2026, 9, 7), LocalDate(2026, 9, 8)), ticks)
        assertEquals(2.hours + 29.minutes + 45.seconds, testScheduler.currentTime.milliseconds)
    }

    @Test
    fun campusDateTicks_followsClockSetBackWithinAMinute() = runTest {
        val clock = virtualClock(LocalDateTime(2026, 9, 10, 9, 0))
        val dates = mutableListOf<LocalDate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { campusDateTicks(clock).toList(dates) }

        clock.setBack(3.days)
        advanceTimeBy(1.minutes)
        runCurrent()

        assertEquals(listOf(LocalDate(2026, 9, 10), LocalDate(2026, 9, 7)), dates)
    }
}

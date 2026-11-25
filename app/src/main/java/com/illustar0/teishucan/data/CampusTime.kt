package com.illustar0.teishucan.data

import com.illustar0.zzu.ZzuTimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** 校园服务给出的日期与时刻所在的时区，与设备当前所在地无关。 */
val CampusZone: TimeZone = ZzuTimeZone

/**
 * 主线程上的 delay 按开机时长计时，设备休眠时停走；墙上时钟也会被用户或校时往前或往后调整。
 * 两次读时钟最多相隔这么久，醒来或调整后最迟这么久就能跟上。
 */
private val MaxSampleWait = 1.minutes

/** 校园时区的当前时刻：收集时立即发出一次，之后每当所在的分钟变化时再发出。只在收集期间计时。 */
fun campusMinuteTicks(clock: Clock = Clock.System): Flow<LocalDateTime> =
    clockSamples(clock) { now -> now.toLocalDateTime(CampusZone).startOfMinute().toInstant(CampusZone) + 1.minutes }
        .map { it.toLocalDateTime(CampusZone) }
        .distinctUntilChangedBy { it.startOfMinute() }

/** 校园时区的今天：收集时立即发出一次，之后每当日期变化时再发出。只在收集期间计时。 */
fun campusDateTicks(clock: Clock = Clock.System): Flow<LocalDate> =
    clockSamples(clock) { now ->
        now.toLocalDateTime(CampusZone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(CampusZone)
    }.map { it.toLocalDateTime(CampusZone).date }
        .distinctUntilChanged()

/**
 * 立即发出 [clock] 的读数，之后在 [nextBoundaryAfter] 算出的时刻再读一次并发出，最多等 [MaxSampleWait]。
 *
 * 墙上时钟不单调，算出的边界只决定下次什么时候读。结果有没有变要由调用方从每次读数重新推算。
 */
private fun clockSamples(clock: Clock, nextBoundaryAfter: (Instant) -> Instant): Flow<Instant> = flow {
    while (true) {
        val now = clock.now()
        emit(now)
        delay(minOf(nextBoundaryAfter(now) - now, MaxSampleWait))
    }
}

private fun LocalDateTime.startOfMinute(): LocalDateTime = LocalDateTime(date, LocalTime(hour, minute))

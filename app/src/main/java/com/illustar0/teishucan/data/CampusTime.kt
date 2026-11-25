package com.illustar0.teishucan.data

import com.illustar0.zzu.ZzuTimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
 * 主线程上的 delay 按开机时长计时，设备休眠时停走。等待被切成不超过这么长的几段，
 * 醒来后最迟这么久就能发现已经跨过了下一个时间点。
 */
private val MaxTickWait = 1.minutes

/** 校园时区的当前时刻：收集时立即发出一次，之后在每分钟开始时再发出。只在收集期间计时。 */
fun campusMinuteTicks(clock: Clock = Clock.System): Flow<LocalDateTime> =
    ticks(clock) { now ->
        val local = now.toLocalDateTime(CampusZone)
        LocalDateTime(local.date, LocalTime(local.hour, local.minute)).toInstant(CampusZone) + 1.minutes
    }.map { it.toLocalDateTime(CampusZone) }

/** 校园时区的今天：收集时立即发出一次，之后在每天零点再发出。只在收集期间计时。 */
fun campusDateTicks(clock: Clock = Clock.System): Flow<LocalDate> =
    ticks(clock) { now ->
        now.toLocalDateTime(CampusZone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(CampusZone)
    }.map { it.toLocalDateTime(CampusZone).date }

/** 立即发出 [clock] 的当前时刻，之后每到 [nextTickAfter] 算出的时刻再发出。 */
private fun ticks(clock: Clock, nextTickAfter: (Instant) -> Instant): Flow<Instant> = flow {
    var now = clock.now()
    while (true) {
        emit(now)
        val next = nextTickAfter(now)
        do {
            delay(minOf(next - now, MaxTickWait))
            now = clock.now()
        } while (now < next)
    }
}

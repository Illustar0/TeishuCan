package com.illustar0.teishucan.data

import com.illustar0.zzu.ZzuTimeZone
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.toJavaZoneId

/** 校园服务给出的日期与时刻所在的时区，与设备当前所在地无关。 */
val CampusZone: ZoneId = ZzuTimeZone.toJavaZoneId()

/**
 * 主线程上的 delay 按开机时长计时，设备休眠时停走。等待被切成不超过这么长的几段，
 * 醒来后最迟这么久就能发现已经跨过了下一个时间单位。
 */
private val MaxTickWait = Duration.ofMinutes(1)

/**
 * 校园时区的当前时刻：收集时立即发出一次，之后在每个 [unit] 开始时再发出，例如每分钟或每天零点。
 *
 * 只在收集期间计时，停止收集后就不再唤醒。
 */
fun campusTimeTicks(unit: ChronoUnit, clock: Clock = Clock.system(CampusZone)): Flow<LocalDateTime> = flow {
    var now = LocalDateTime.now(clock)
    while (true) {
        emit(now)
        val next = now.truncatedTo(unit).plus(1, unit)
        do {
            delay(minOf(Duration.between(now, next), MaxTickWait).toKotlinDuration())
            now = LocalDateTime.now(clock)
        } while (now < next)
    }
}

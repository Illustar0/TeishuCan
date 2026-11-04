package com.illustar0.chibacan.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

/**
 * 为 ChibaCan 采集 Baseline Profile。
 *
 * 运行 `./gradlew :app:generateBaselineProfile`，需要连接 API 33+ 或已 root 的设备；
 * 结果会写回 app 模块的 `generated/baselineProfiles` 源集，随 release 构建一起打包。
 */
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    /** 冷启动到首帧，同时写入 Startup Profile，供 R8 把启动路径上的类排进主 DEX。 */
    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = "com.illustar0.chibacan",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }
}

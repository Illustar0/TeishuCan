package com.illustar0.teishucan.ui.component.animation

import androidx.compose.animation.core.SpringSpec
import top.yukonga.miuix.kmp.anim.folmeSpring

/**
 * 平稳的过渡：Miuix 的 folmeSpring 取临界阻尼（damping 1.0，不回弹），response 0.4 秒。
 * 课表叠卡换层、详情里换一门课展开都用它，两处的节奏一致。
 */
internal object CalmMotion {
    private const val Damping = 1f
    private const val ResponseSeconds = 0.4f

    /** 弹簧走到只剩 0.1% 位移所需的毫秒数（ωt ≈ 9.23，ω = 2π / 0.4），供按时间安排的淡入淡出使用。 */
    const val DurationMillis = 588

    fun <T> spring(): SpringSpec<T> = folmeSpring(damping = Damping, response = ResponseSeconds)
}

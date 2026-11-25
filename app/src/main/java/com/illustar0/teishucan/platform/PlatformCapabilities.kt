package com.illustar0.teishucan.platform

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

/**
 * 系统版本决定的可选能力。应用自己的代码只在这里判断系统版本：设置页据此隐藏用不了的选项，功能据此降级。
 *
 * 从上游移植的特效组件保持原样，直接调用 Miuix 的 [isRuntimeShaderSupported]，与 [isShaderEffectSupported] 同义。
 */
internal object PlatformCapabilities {
    /** 模糊、液态玻璃与动态背景都靠运行时着色器绘制。 */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val isShaderEffectSupported: Boolean
        get() = isRuntimeShaderSupported()

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val isWallpaperColorSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    val isPredictiveBackSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** 系统密码管理器能通过 Credential Manager 保存和填入密码。 */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    val isSystemPasswordManagerSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
}

package com.illustar0.teishucan.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.illustar0.teishucan.platform.PlatformCapabilities
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val BarBlurRadius = 25f
private const val BarTintAlpha = 0.8f
private const val FloatingBarTintAlpha = 0.6f

// 两个开关随设置翻转，只让读取它们的地方重组，不连带整棵界面。
val LocalIsBlurEnabled = compositionLocalOf { false }

val LocalIsLiquidGlassEnabled = compositionLocalOf { false }

@Composable
fun rememberBlurBackdrop(isEnabled: Boolean = LocalIsBlurEnabled.current): LayerBackdrop? {
    if (!isEnabled || !PlatformCapabilities.isShaderEffectSupported) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** 把此内容录入 [backdrop]，让上层的栏可以模糊它。 */
fun Modifier.blurSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) layerBackdrop(backdrop) else this

@Composable
fun Modifier.floatingBarBlur(backdrop: LayerBackdrop, shape: Shape): Modifier {
    val tintColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = FloatingBarTintAlpha)
    return textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = BarBlurRadius,
        colors = BlurDefaults.blurColors(blendColors = listOf(BlendColorEntry(color = tintColor))),
    )
}

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    content: @Composable (containerColor: Color) -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    Box(
        modifier = if (backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = BarBlurRadius,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(BlendColorEntry(color = surfaceColor.copy(alpha = BarTintAlpha))),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content(if (backdrop != null) Color.Transparent else surfaceColor)
    }
}

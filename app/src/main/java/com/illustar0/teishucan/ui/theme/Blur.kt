package com.illustar0.teishucan.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import com.illustar0.teishucan.TeishuCanApplication
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val BarBlurRadius = 25f
private const val BarTintAlpha = 0.8f
private const val FloatingBarTintAlpha = 0.6f

val LocalIsBlurEnabled = staticCompositionLocalOf { false }

val LocalIsLiquidGlassEnabled = staticCompositionLocalOf { false }

val isBlurSupported: Boolean
    get() = isRuntimeShaderSupported()

@Composable
fun rememberBlurBackdrop(isEnabled: Boolean = LocalIsBlurEnabled.current): LayerBackdrop? {
    if (!isEnabled || !isBlurSupported) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberReleasableBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
internal fun rememberReleasableBackdrop(onDraw: ContentDrawScope.() -> Unit = { drawContent() }): LayerBackdrop {
    val backdrop = rememberLayerBackdrop(onDraw = onDraw)
    val fairMemory = (LocalContext.current.applicationContext as? TeishuCanApplication)?.fairMemory
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    DisposableEffect(fairMemory, backdrop, density, layoutDirection) {
        val layer = backdrop.graphicsLayer
        val release: () -> Unit = {
            // 后台可能没有下一帧，立即清空录制的内容，避免继续持有子图层。
            if (!layer.isReleased) layer.record(density, layoutDirection, IntSize.Zero) {}
        }
        fairMemory?.addReleaseCallback(release)
        onDispose { fairMemory?.removeReleaseCallback(release) }
    }
    return backdrop
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

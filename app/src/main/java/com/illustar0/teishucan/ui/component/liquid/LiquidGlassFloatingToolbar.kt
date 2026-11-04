package com.illustar0.teishucan.ui.component.liquid

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.ui.theme.LocalIsLiquidGlassEnabled
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 外观与 [LiquidGlassNavigationBar] 一致的 [FloatingToolbar]，形态规则见 [floatingPanel]。
 *
 * 阴影也由 [floatingPanel] 绘制。工具栏自带的阴影画在外部 modifier 上层，会压暗半透明的背景。
 */
@Composable
internal fun LiquidGlassFloatingToolbar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FloatingToolbar(
        modifier = modifier
            .padding(FloatingToolbarDefaults.OutSidePadding)
            .floatingPanel(
                backdrop = backdrop,
                isLiquidGlass = LocalIsLiquidGlassEnabled.current,
                shape = CircleShape,
            ),
        color = Color.Transparent,
        outSidePadding = PaddingValues(0.dp),
        shadowElevation = 0.dp,
        content = content,
    )
}

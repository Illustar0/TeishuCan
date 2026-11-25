package com.illustar0.teishucan.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** Material 窗口尺寸类别里 Expanded 与 Medium 的宽度下限。 */
private val ExpandedMinWidth = 840.dp
private val MediumMinWidth = 600.dp

/** Medium 宽度的窗口高宽比小于此值时也算宽窗口。 */
private const val WideWindowMaxAspectRatio = 1.2f

/** 沿用 Miuix 示例应用的分栏断点。 */
@Composable
fun isWideWindow(): Boolean {
    val size = LocalWindowInfo.current.containerDpSize
    return size.width >= ExpandedMinWidth ||
        (size.width >= MediumMinWidth && size.height / size.width < WideWindowMaxAspectRatio)
}

package com.illustar0.teishucan.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

private const val WideWindowMaxAspectRatio = 1.2f

/** 沿用 Miuix 示例应用的分栏断点。 */
@Composable
fun isWideWindow(): Boolean {
    val size = LocalWindowInfo.current.containerDpSize
    return size.width >= 840.dp || (size.width >= 600.dp && size.height / size.width < WideWindowMaxAspectRatio)
}

package com.illustar0.teishucan.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 按 [groups] 分段画出连续的节次，段与段之间用圆点隔开，每一节的颜色由 [periodColor] 决定。 */
@Composable
fun PeriodStrip(
    groups: List<IntRange>,
    periodColor: (period: Int) -> Color,
    modifier: Modifier = Modifier,
    segmentHeight: Dp = PeriodStripDefaults.SegmentHeight,
    segmentGap: Dp = PeriodStripDefaults.SegmentGap,
    separatorGap: Dp = PeriodStripDefaults.SeparatorGap,
    separatorDotSize: Dp = PeriodStripDefaults.SeparatorDotSize,
    separatorColor: Color = PeriodStripDefaults.separatorColor(),
) {
    val canvasHeight = maxOf(segmentHeight, separatorDotSize)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight),
    ) {
        val segHeightPx = segmentHeight.toPx()
        val segGapPx = segmentGap.toPx()
        val sepGapPx = separatorGap.toPx()
        val dotSizePx = separatorDotSize.toPx()
        val dotRadiusPx = dotSizePx / 2f
        val barCorner = CornerRadius(segHeightPx / 2f)
        val barTop = (size.height - segHeightPx) / 2f
        val centerY = size.height / 2f

        val separatorWidth = dotSizePx + 2 * sepGapPx
        val groupsTotalWidth = (size.width - (groups.size - 1) * separatorWidth).coerceAtLeast(0f)
        val totalPeriods = groups.sumOf { it.count() }
        val isRtl = layoutDirection == LayoutDirection.Rtl

        var cursorX = 0f
        groups.forEachIndexed { groupIndex, range ->
            if (groupIndex > 0) {
                cursorX += sepGapPx
                val dotCenterX = if (isRtl) size.width - cursorX - dotRadiusPx else cursorX + dotRadiusPx
                drawCircle(
                    color = separatorColor,
                    radius = dotRadiusPx,
                    center = Offset(dotCenterX, centerY),
                )
                cursorX += dotSizePx + sepGapPx
            }

            val groupSize = range.count()
            val groupWidth = groupsTotalWidth * (groupSize.toFloat() / totalPeriods)
            val segWidth = ((groupWidth - (groupSize - 1) * segGapPx) / groupSize).coerceAtLeast(0f)

            var segX = cursorX
            for (period in range) {
                val drawX = if (isRtl) size.width - segX - segWidth else segX
                drawRoundRect(
                    color = periodColor(period),
                    topLeft = Offset(drawX, barTop),
                    size = Size(segWidth, segHeightPx),
                    cornerRadius = barCorner,
                )
                segX += segWidth + segGapPx
            }
            cursorX += groupWidth
        }
    }
}

/** [PeriodStrip] 的默认尺寸与基础色阶 */
object PeriodStripDefaults {
    val SegmentHeight: Dp = 6.dp
    val SegmentGap: Dp = 3.dp
    val SeparatorGap: Dp = 6.dp
    val SeparatorDotSize: Dp = 4.dp

    private const val EmptySegmentAlpha = 0.14f

    /** 空节次轨道默认色。 */
    @Composable
    @ReadOnlyComposable
    fun emptyColor(): Color = MiuixTheme.colorScheme.onSurface.copy(alpha = EmptySegmentAlpha)

    /** 分组分隔点默认色。 */
    @Composable
    @ReadOnlyComposable
    fun separatorColor(): Color = MiuixTheme.colorScheme.onSurfaceVariantActions
}

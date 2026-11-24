package com.illustar0.teishucan.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val PeriodGroups = listOf(1..4, 5..8, 9..10)

/**
 * 分段可视化条
 */
@Composable
fun PeriodStrip(
    periodColor: (period: Int) -> Color,
    modifier: Modifier = Modifier,
    segmentHeight: Dp = PeriodStripDefaults.SegmentHeight,
    segmentGap: Dp = PeriodStripDefaults.SegmentGap,
    separatorGap: Dp = PeriodStripDefaults.SeparatorGap,
    separatorDotSize: Dp = PeriodStripDefaults.SeparatorDotSize,
    separatorColor: Color = PeriodStripDefaults.separatorColor(),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(separatorGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PeriodGroups.forEachIndexed { groupIndex, range ->
            if (groupIndex > 0) {
                Box(
                    modifier = Modifier
                        .size(separatorDotSize)
                        .squircleSurface(color = separatorColor, cornerRadius = separatorDotSize / 2),
                )
            }
            val groupSize = range.last - range.first + 1
            Row(
                modifier = Modifier.weight(groupSize.toFloat()),
                horizontalArrangement = Arrangement.spacedBy(segmentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (period in range) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(segmentHeight)
                            .squircleSurface(
                                color = periodColor(period),
                                cornerRadius = segmentHeight / 2,
                            ),
                    )
                }
            }
        }
    }
}

/** [PeriodStrip] 的默认尺寸与分隔点配色。 */
object PeriodStripDefaults {
    val SegmentHeight: Dp = 6.dp
    val SegmentGap: Dp = 3.dp
    val SeparatorGap: Dp = 6.dp
    val SeparatorDotSize: Dp = 4.dp

    @Composable
    @ReadOnlyComposable
    fun separatorColor(): Color = MiuixTheme.colorScheme.outline
}

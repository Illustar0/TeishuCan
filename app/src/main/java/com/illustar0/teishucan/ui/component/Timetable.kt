package com.illustar0.teishucan.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.TimetableEntry
import com.illustar0.teishucan.domain.TimetableWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 周课表网格。
 *
 * 起始节次在网格之外的课程不显示，超出最后一节的部分被截断；同日时段冲突的课程按「右下在前、左上露边」叠放：
 * 完全重叠的课程以等尺寸叠卡向右下步进（最多展示 3 层），部分重叠或错位交叉的课程按节次阶梯错位叠放。
 * 表头显示这一周的日期，并在 [today] 落在这一周时突出显示。
 */
@Composable
fun Timetable(
    week: TimetableWeek,
    today: LocalDate,
    onEntriesClick: (List<TimetableEntry>) -> Unit,
    modifier: Modifier = Modifier,
    periodCount: Int = TimetableDefaults.PeriodCount,
    periodHeight: Dp = TimetableDefaults.PeriodHeight,
    colors: TimetableColors = TimetableDefaults.timetableColors(),
) {
    Column(modifier = modifier) {
        DayHeaderRow(week = week, today = today, colors = colors)
        Row {
            PeriodLabelColumn(
                periodCount = periodCount,
                periodHeight = periodHeight,
                colors = colors,
            )
            EntryGrid(
                entries = week.entries,
                onEntriesClick = onEntriesClick,
                periodCount = periodCount,
                periodHeight = periodHeight,
                colors = colors,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayHeaderRow(
    week: TimetableWeek,
    today: LocalDate,
    colors: TimetableColors,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(TimetableDefaults.PeriodLabelWidth))
        DayOfWeek.entries.forEach { day ->
            val date = week.dates.find { it.dayOfWeek == day }
            val isToday = date == today
            val color = if (isToday) colors.highlightedDayColor else colors.dayColor
            val fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, locale),
                    color = color,
                    fontWeight = fontWeight,
                    maxLines = 1,
                    style = MiuixTheme.textStyles.footnote1,
                )
                if (date != null) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        color = color,
                        fontWeight = fontWeight,
                        style = MiuixTheme.textStyles.footnote2,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodLabelColumn(
    periodCount: Int,
    periodHeight: Dp,
    colors: TimetableColors,
) {
    Column(modifier = Modifier.width(TimetableDefaults.PeriodLabelWidth)) {
        repeat(periodCount) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(periodHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (index + 1).toString(),
                    color = colors.periodColor,
                    style = MiuixTheme.textStyles.footnote2,
                )
            }
        }
    }
}

@Composable
private fun EntryGrid(
    entries: List<TimetableEntry>,
    onEntriesClick: (List<TimetableEntry>) -> Unit,
    periodCount: Int,
    periodHeight: Dp,
    colors: TimetableColors,
    modifier: Modifier = Modifier,
) {
    val cards = remember(entries, periodCount) {
        buildTimetableCards(entries = entries, periodCount = periodCount)
    }
    Layout(
        content = {
            cards.forEach { card ->
                val surfaceColor = colors.stackSurfaceColor(card.depthFromFront)
                val borderModifier = if (card.hasBorder) {
                    Modifier.squircleBorder(
                        width = TimetableDefaults.EntryBorderWidth,
                        color = colors.entryBorderColor,
                        cornerRadius = TimetableDefaults.EntryCornerRadius,
                    )
                } else {
                    Modifier
                }
                if (card.showsContent) {
                    EntryCell(
                        entry = card.entry,
                        conflictCount = card.clickEntries.size,
                        surfaceColor = surfaceColor,
                        colors = colors,
                        onClick = { onEntriesClick(card.clickEntries) },
                        modifier = borderModifier,
                    )
                } else {
                    DecorativeStackLayer(
                        surfaceColor = surfaceColor,
                        modifier = borderModifier,
                    )
                }
            }
        },
        modifier = modifier
            .height(periodHeight * periodCount)
            .drawBehind {
                val dayWidth = size.width / DayOfWeek.entries.size
                val rowHeight = size.height / periodCount
                for (period in 0..periodCount) {
                    val y = period * rowHeight
                    drawLine(colors.gridLineColor, Offset(0f, y), Offset(size.width, y))
                }
                for (day in 0..DayOfWeek.entries.size) {
                    val x = day * dayWidth
                    drawLine(colors.gridLineColor, Offset(x, 0f), Offset(x, size.height))
                }
            },
    ) { measurables, constraints ->
        val dayWidth = constraints.maxWidth.toFloat() / DayOfWeek.entries.size
        val rowHeight = constraints.maxHeight.toFloat() / periodCount
        val gap = TimetableDefaults.EntryGap.roundToPx()
        val stackStep = TimetableDefaults.StackStep.roundToPx()
        val cascadeIndent = TimetableDefaults.CascadeIndent.roundToPx()
        val rects = layoutTimetableCards(
            cards = cards,
            dayWidthPx = dayWidth,
            rowHeightPx = rowHeight,
            gapPx = gap,
            stackStepPx = stackStep,
            cascadeIndentPx = cascadeIndent,
        )
        val placeables = measurables.mapIndexed { index, measurable ->
            val rect = rects[index]
            measurable.measure(Constraints.fixed(width = rect.width, height = rect.height))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val rect = rects[index]
                placeable.placeRelative(x = rect.x, y = rect.y)
            }
        }
    }
}

@Composable
private fun EntryCell(
    entry: TimetableEntry,
    conflictCount: Int,
    surfaceColor: Color,
    colors: TimetableColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayName = entry.day.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])
    val position = if (conflictCount > 1) {
        stringResource(R.string.timetable_entry_conflict_position, dayName, entry.startPeriod, conflictCount)
    } else {
        stringResource(R.string.timetable_entry_position, dayName, entry.startPeriod)
    }
    Card(
        modifier = modifier.semantics(mergeDescendants = true) { stateDescription = position },
        cornerRadius = TimetableDefaults.EntryCornerRadius,
        insideMargin = PaddingValues(TimetableDefaults.EntryPadding),
        colors = CardDefaults.defaultColors(color = surfaceColor, contentColor = colors.entryContentColor),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        // 标题占满剩余高度，把地点压到卡片底部；空间不足时先截断标题，保住门牌号。
        Text(
            text = entry.title,
            modifier = Modifier.weight(1f),
            color = colors.entryContentColor,
            fontWeight = FontWeight.Medium,
            overflow = TextOverflow.Ellipsis,
            style = MiuixTheme.textStyles.footnote2,
        )
        if (entry.location != null) {
            Text(
                text = entry.location,
                color = colors.entryLocationColor,
                overflow = TextOverflow.Ellipsis,
                maxLines = TimetableDefaults.LocationMaxLines,
                style = MiuixTheme.textStyles.footnote2,
            )
        }
    }
}

@Composable
private fun DecorativeStackLayer(
    surfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clearAndSetSemantics {},
        cornerRadius = TimetableDefaults.EntryCornerRadius,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = surfaceColor, contentColor = Color.Transparent),
    ) {}
}

/** 网格中需要按顺序绘制的一张卡片（后绘者盖在前绘者之上）。 */
internal data class TimetableCardSpec(
    val entry: TimetableEntry,
    val clampedSpan: Int,
    val clickEntries: List<TimetableEntry>,
    val kind: CardLayerKind,
    val stepIndex: Int,
    val totalSteps: Int,
    val depthFromFront: Int,
    val showsContent: Boolean,
    val hasBorder: Boolean,
)

internal enum class CardLayerKind {
    Single,
    ExactStack,
    PartialCascade,
}

internal data class TimetableCardRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

private data class VisibleEntry(
    val entry: TimetableEntry,
    val clampedSpan: Int,
    val originalIndex: Int,
) {
    val endExclusive: Int get() = entry.startPeriod + clampedSpan
}

/**
 * 将一周课程按天聚类并展开为待绘制的卡片列表，顺序即为从底到顶的绘制层级。
 */
internal fun buildTimetableCards(
    entries: List<TimetableEntry>,
    periodCount: Int,
    maxStackBackLayers: Int = TimetableDefaults.MaxStackBackLayers,
): List<TimetableCardSpec> {
    val visibleByDay = entries
        .mapIndexedNotNull { index, entry ->
            if (entry.startPeriod !in 1..periodCount || entry.periodSpan < 1) {
                null
            } else {
                val clampedSpan = entry.periodSpan.coerceAtMost(periodCount - entry.startPeriod + 1)
                VisibleEntry(entry = entry, clampedSpan = clampedSpan, originalIndex = index)
            }
        }
        .groupBy { it.entry.day }

    val cards = mutableListOf<TimetableCardSpec>()
    DayOfWeek.entries.forEach { day ->
        val dayEntries = visibleByDay[day].orEmpty()
        if (dayEntries.isEmpty()) return@forEach
        clusterOverlappingEntries(dayEntries).forEach { cluster ->
            cards += expandClusterCards(cluster, maxStackBackLayers)
        }
    }
    return cards
}

private fun clusterOverlappingEntries(dayEntries: List<VisibleEntry>): List<List<VisibleEntry>> {
    val sorted = dayEntries.sortedWith(
        compareBy<VisibleEntry> { it.entry.startPeriod }
            .thenByDescending { it.clampedSpan }
            .thenBy { it.originalIndex },
    )
    val clusters = mutableListOf<List<VisibleEntry>>()
    var current = mutableListOf<VisibleEntry>()
    var maxEndExclusive = -1
    for (item in sorted) {
        if (current.isEmpty()) {
            current.add(item)
            maxEndExclusive = item.endExclusive
        } else if (item.entry.startPeriod < maxEndExclusive) {
            current.add(item)
            maxEndExclusive = maxOf(maxEndExclusive, item.endExclusive)
        } else {
            clusters.add(current)
            current = mutableListOf(item)
            maxEndExclusive = item.endExclusive
        }
    }
    if (current.isNotEmpty()) clusters.add(current)
    return clusters
}

private fun expandClusterCards(
    cluster: List<VisibleEntry>,
    maxStackBackLayers: Int,
): List<TimetableCardSpec> {
    if (cluster.size == 1) {
        val single = cluster.single()
        return listOf(
            TimetableCardSpec(
                entry = single.entry,
                clampedSpan = single.clampedSpan,
                clickEntries = listOf(single.entry),
                kind = CardLayerKind.Single,
                stepIndex = 0,
                totalSteps = 0,
                depthFromFront = 0,
                showsContent = true,
                hasBorder = false,
            ),
        )
    }

    val first = cluster.first()
    val allSameRange = cluster.all {
        it.entry.startPeriod == first.entry.startPeriod && it.clampedSpan == first.clampedSpan
    }
    if (allSameRange) {
        return expandExactStack(cluster, maxStackBackLayers)
    }
    return expandPartialCascade(cluster, maxStackBackLayers)
}

/**
 * 完全重叠采用「右下在前、左上露边」：所有层共享同一缩减尺寸，从左上 (0, 0) 向右下步进，
 * 确保 2 门露出 2 张、3 门及以上露出 3 张互不遮死。
 */
private fun expandExactStack(
    cluster: List<VisibleEntry>,
    maxStackBackLayers: Int,
): List<TimetableCardSpec> {
    val backLayers = (cluster.size - 1).coerceAtMost(maxStackBackLayers)
    val front = cluster.first()
    val allEntries = cluster.map(VisibleEntry::entry)
    val result = ArrayList<TimetableCardSpec>(backLayers + 1)

    for (stepIndex in 0 until backLayers) {
        result += TimetableCardSpec(
            entry = front.entry,
            clampedSpan = front.clampedSpan,
            clickEntries = allEntries,
            kind = CardLayerKind.ExactStack,
            stepIndex = stepIndex,
            totalSteps = backLayers,
            depthFromFront = backLayers - stepIndex,
            showsContent = false,
            hasBorder = true,
        )
    }
    result += TimetableCardSpec(
        entry = front.entry,
        clampedSpan = front.clampedSpan,
        clickEntries = allEntries,
        kind = CardLayerKind.ExactStack,
        stepIndex = backLayers,
        totalSteps = backLayers,
        depthFromFront = 0,
        showsContent = true,
        hasBorder = true,
    )
    return result
}

/**
 * 部分重叠或错位交叉按区间贪心分层：早/长的课贴左铺底，晚/短的课向右缩进叠在前方，
 * 保持每门课真实的起止节次边界，并与完全重叠保持「左上在后、右下在前」的统一景深。
 */
private fun expandPartialCascade(
    cluster: List<VisibleEntry>,
    maxStackBackLayers: Int,
): List<TimetableCardSpec> {
    val laneEnds = mutableListOf<Int>()
    val assigned = cluster.map { item ->
        val lane = laneEnds.indexOfFirst { it <= item.entry.startPeriod }
        val resolvedLane = if (lane >= 0) {
            laneEnds[lane] = item.endExclusive
            lane
        } else {
            laneEnds.add(item.endExclusive)
            laneEnds.lastIndex
        }
        item to resolvedLane.coerceAtMost(maxStackBackLayers)
    }
    val maxLane = assigned.maxOf { it.second }
    val allEntries = cluster.map(VisibleEntry::entry)

    return assigned
        .sortedWith(compareBy<Pair<VisibleEntry, Int>> { it.second }.thenBy { it.first.entry.startPeriod })
        .map { (item, lane) ->
            // 点哪张卡就把哪门课放在详情首位，同时保留同冲突组的其余课程。
            val orderedClick = buildList(allEntries.size) {
                add(item.entry)
                allEntries.forEach { other -> if (other !== item.entry) add(other) }
            }
            TimetableCardSpec(
                entry = item.entry,
                clampedSpan = item.clampedSpan,
                clickEntries = orderedClick,
                kind = CardLayerKind.PartialCascade,
                stepIndex = lane,
                totalSteps = maxLane,
                depthFromFront = maxLane - lane,
                showsContent = true,
                hasBorder = true,
            )
        }
}

/** 将卡片规格换算为网格内的像素矩形。 */
internal fun layoutTimetableCards(
    cards: List<TimetableCardSpec>,
    dayWidthPx: Float,
    rowHeightPx: Float,
    gapPx: Int,
    stackStepPx: Int,
    cascadeIndentPx: Int,
): List<TimetableCardRect> {
    val baseWidth = (dayWidthPx.roundToInt() - gapPx).coerceAtLeast(0)
    val maxCascadeInset = (baseWidth * TimetableDefaults.MaxCascadeInsetFraction).roundToInt()

    return cards.map { card ->
        val baseX = (card.entry.day.ordinal * dayWidthPx).roundToInt() + gapPx / 2
        val baseY = ((card.entry.startPeriod - 1) * rowHeightPx).roundToInt() + gapPx / 2
        val baseHeight = ((rowHeightPx * card.clampedSpan).roundToInt() - gapPx).coerceAtLeast(0)

        when (card.kind) {
            CardLayerKind.Single -> TimetableCardRect(
                x = baseX,
                y = baseY,
                width = baseWidth,
                height = baseHeight,
            )

            CardLayerKind.ExactStack -> {
                val totalOffset = (card.totalSteps * stackStepPx).coerceAtMost(maxCascadeInset)
                val step = if (card.totalSteps > 0) totalOffset / card.totalSteps else 0
                val offset = step * card.stepIndex
                val cardWidth = (baseWidth - totalOffset).coerceAtLeast(0)
                val cardHeight = (baseHeight - totalOffset).coerceAtLeast(0)
                TimetableCardRect(
                    x = baseX + offset,
                    y = baseY + offset,
                    width = cardWidth,
                    height = cardHeight,
                )
            }

            CardLayerKind.PartialCascade -> {
                val rawStep = if (card.totalSteps > 0) {
                    minOf(cascadeIndentPx, maxCascadeInset / card.totalSteps)
                } else {
                    0
                }
                val indent = rawStep * card.stepIndex
                TimetableCardRect(
                    x = baseX + indent,
                    y = baseY,
                    width = (baseWidth - indent).coerceAtLeast(0),
                    height = baseHeight,
                )
            }
        }
    }
}

private fun TimetableColors.stackSurfaceColor(depthFromFront: Int): Color = when (depthFromFront) {
    0 -> entryColor
    1 -> entryStackColor
    else -> entryDeepStackColor
}

object TimetableDefaults {
    const val PeriodCount = 10
    val PeriodHeight = 64.dp
    val PeriodLabelWidth = 24.dp
    val EntryGap = 2.dp
    val EntryCornerRadius = 6.dp
    val EntryPadding = 4.dp
    val EntryBorderWidth = 1.5.dp
    val StackStep = 4.dp
    val CascadeIndent = 5.dp

    /** 冲突叠卡最多在底层额外露出 2 张（总计最多 3 张），避免前卡在窄列被过度挤压。 */
    const val MaxStackBackLayers = 2

    /** 叠层缩进上限占单列宽度的比例，确保极窄屏幕下前卡仍保留足够可读宽度。 */
    internal const val MaxCascadeInsetFraction = 0.25f

    /** 两行足以放下“楼名_门牌号”而不截掉门牌号。 */
    const val LocationMaxLines = 2

    /**
     * Miuix 没有与 tertiaryContainer 配对的次要文字 token，这里沿用浅色主题下
     * onSurfaceVariantSummary 相对正文的 60% 不透明度。
     */
    private const val LocationAlpha = 0.6f
    private const val LightSurfaceLuminanceThreshold = 0.5f
    private const val LightMiddleStackTintFraction = 0.13f
    private const val LightDeepStackTintFraction = 0.26f
    private const val DarkMiddleStackShadeFraction = 0.22f
    private const val DarkDeepStackShadeFraction = 0.40f

    @Composable
    fun timetableColors(
        dayColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        highlightedDayColor: Color = MiuixTheme.colorScheme.primary,
        periodColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        entryColor: Color = MiuixTheme.colorScheme.tertiaryContainer,
        entryContentColor: Color = MiuixTheme.colorScheme.onTertiaryContainer,
        entryLocationColor: Color = entryContentColor.copy(alpha = LocationAlpha),
        entryStackColor: Color = defaultStackLayerColor(
            entryColor = entryColor,
            entryContentColor = entryContentColor,
            isDeep = false,
        ),
        entryDeepStackColor: Color = defaultStackLayerColor(
            entryColor = entryColor,
            entryContentColor = entryContentColor,
            isDeep = true,
        ),
        entryBorderColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        gridLineColor: Color = MiuixTheme.colorScheme.dividerLine,
    ): TimetableColors = remember(
        dayColor,
        highlightedDayColor,
        periodColor,
        entryColor,
        entryContentColor,
        entryLocationColor,
        entryStackColor,
        entryDeepStackColor,
        entryBorderColor,
        gridLineColor,
    ) {
        TimetableColors(
            dayColor = dayColor,
            highlightedDayColor = highlightedDayColor,
            periodColor = periodColor,
            entryColor = entryColor,
            entryContentColor = entryContentColor,
            entryLocationColor = entryLocationColor,
            entryStackColor = entryStackColor,
            entryDeepStackColor = entryDeepStackColor,
            entryBorderColor = entryBorderColor,
            gridLineColor = gridLineColor,
        )
    }

    private fun defaultStackLayerColor(
        entryColor: Color,
        entryContentColor: Color,
        isDeep: Boolean,
    ): Color = if (entryColor.luminance() >= LightSurfaceLuminanceThreshold) {
        val fraction = if (isDeep) LightDeepStackTintFraction else LightMiddleStackTintFraction
        lerp(entryColor, entryContentColor, fraction)
    } else {
        val fraction = if (isDeep) DarkDeepStackShadeFraction else DarkMiddleStackShadeFraction
        lerp(entryColor, Color.Black, fraction)
    }
}

@Immutable
data class TimetableColors(
    val dayColor: Color,
    val highlightedDayColor: Color,
    val periodColor: Color,
    val entryColor: Color,
    val entryContentColor: Color,
    val entryLocationColor: Color,
    val entryStackColor: Color,
    val entryDeepStackColor: Color,
    val entryBorderColor: Color,
    val gridLineColor: Color,
)

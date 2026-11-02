package com.illustar0.chibacan.ui.component

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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.illustar0.chibacan.R
import com.illustar0.chibacan.domain.TimetableEntry
import com.illustar0.chibacan.domain.TimetableWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * [week] 周一到周日的课表网格，每天 [periodCount] 节。
 *
 * 起始节次在网格之外的课程不显示，超出最后一节的部分被截断，占用同一格的课程按列表顺序重叠。
 * 表头显示这一周的日期，并在 [today] 落在这一周时突出显示。点击课程时调用 [onEntryClick]。
 */
@Composable
fun Timetable(
    week: TimetableWeek,
    today: LocalDate,
    onEntryClick: (TimetableEntry) -> Unit,
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
                onEntryClick = onEntryClick,
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
    onEntryClick: (TimetableEntry) -> Unit,
    periodCount: Int,
    periodHeight: Dp,
    colors: TimetableColors,
    modifier: Modifier = Modifier,
) {
    val visibleEntries = remember(entries, periodCount) {
        entries.filter { it.startPeriod in 1..periodCount && it.periodSpan >= 1 }
    }
    Layout(
        content = {
            visibleEntries.forEach { entry ->
                EntryCell(entry = entry, onClick = { onEntryClick(entry) }, colors = colors)
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
        val placeables = measurables.mapIndexed { index, measurable ->
            val entry = visibleEntries[index]
            val span = entry.periodSpan.coerceAtMost(periodCount - entry.startPeriod + 1)
            measurable.measure(
                Constraints.fixed(
                    width = (dayWidth.roundToInt() - gap).coerceAtLeast(0),
                    height = ((rowHeight * span).roundToInt() - gap).coerceAtLeast(0),
                ),
            )
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val entry = visibleEntries[index]
                placeable.placeRelative(
                    x = (entry.day.ordinal * dayWidth).roundToInt() + gap / 2,
                    y = ((entry.startPeriod - 1) * rowHeight).roundToInt() + gap / 2,
                )
            }
        }
    }
}

@Composable
private fun EntryCell(
    entry: TimetableEntry,
    onClick: () -> Unit,
    colors: TimetableColors,
) {
    val dayName = entry.day.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])
    val position = stringResource(R.string.timetable_entry_position, dayName, entry.startPeriod)
    Card(
        modifier = Modifier.semantics(mergeDescendants = true) { stateDescription = position },
        cornerRadius = TimetableDefaults.EntryCornerRadius,
        insideMargin = PaddingValues(TimetableDefaults.EntryPadding),
        colors = CardDefaults.defaultColors(color = colors.entryColor, contentColor = colors.entryContentColor),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        Text(
            text = entry.title,
            modifier = Modifier.weight(1f, fill = false),
            color = colors.entryContentColor,
            overflow = TextOverflow.Ellipsis,
            style = MiuixTheme.textStyles.footnote2,
        )
        if (entry.location != null) {
            Text(
                text = entry.location,
                color = colors.entryContentColor,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MiuixTheme.textStyles.footnote2,
            )
        }
    }
}

/** [Timetable] 使用的默认值。 */
object TimetableDefaults {
    /** 每天的默认节数。 */
    const val PeriodCount = 10

    /** 一节课的默认高度。 */
    val PeriodHeight = 64.dp

    /** 节次序号列的宽度。 */
    val PeriodLabelWidth = 24.dp

    /** 课程与周围网格线之间的间隙。 */
    val EntryGap = 2.dp

    /** 课程卡片的圆角半径。 */
    val EntryCornerRadius = 6.dp

    /** 课程卡片的内边距。 */
    val EntryPadding = 4.dp

    /** [Timetable] 的默认颜色。 */
    @Composable
    fun timetableColors(
        dayColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        highlightedDayColor: Color = MiuixTheme.colorScheme.primary,
        periodColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        entryColor: Color = MiuixTheme.colorScheme.tertiaryContainer,
        entryContentColor: Color = MiuixTheme.colorScheme.onTertiaryContainer,
        gridLineColor: Color = MiuixTheme.colorScheme.dividerLine,
    ): TimetableColors = remember(
        dayColor,
        highlightedDayColor,
        periodColor,
        entryColor,
        entryContentColor,
        gridLineColor,
    ) {
        TimetableColors(
            dayColor = dayColor,
            highlightedDayColor = highlightedDayColor,
            periodColor = periodColor,
            entryColor = entryColor,
            entryContentColor = entryContentColor,
            gridLineColor = gridLineColor,
        )
    }
}

@Immutable
data class TimetableColors(
    val dayColor: Color,
    val highlightedDayColor: Color,
    val periodColor: Color,
    val entryColor: Color,
    val entryContentColor: Color,
    val gridLineColor: Color,
)

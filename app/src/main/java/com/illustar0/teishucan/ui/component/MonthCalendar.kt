package com.illustar0.teishucan.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.CalendarGridDaysPerWeek
import com.illustar0.teishucan.domain.CalendarGridRows
import com.illustar0.teishucan.domain.buildCalendarMonthGrid
import com.illustar0.teishucan.ui.theme.CardSpacing
import java.time.format.TextStyle
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number
import kotlinx.datetime.yearMonth
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.springAnimateToPage

private const val BaseCalendarYear = 1970
private const val MonthsPerYear = 12
private const val TotalCalendarMonths = 2400
private const val OutsideMonthAlpha = 0.62f
private const val OutsideMonthHighlightedAlpha = 0.5f

private val DayCornerRadius = 10.dp
private val DayCellHeight = 40.dp
private val SelectedBorderWidth = 1.75.dp
private val GridGap = 4.dp
private val HeaderVerticalSpacing = 6.dp
private val CalendarSheetBottomSpacing = CardSpacing
private val CalendarCardInsideMargin = PaddingValues(CardSpacing)

/**
 * 固定 6 行 × 7 列的月历，用 [HorizontalPager] 左右翻月。
 *
 * [today] 始终涂成主色实心块，选中的其他日期 [selectedDate] 只画主色描边，两者同时出现时也能一眼分清。
 */
@Composable
fun MonthCalendar(
    selectedDate: LocalDate,
    today: LocalDate,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberMonthPagerState(selectedDate.yearMonth),
) {
    val coroutineScope = rememberCoroutineScope()
    val displayedMonth = monthAtPage(pagerState.targetPage)
    val canGoPrevious = pagerState.targetPage > 0
    val canGoNext = pagerState.targetPage < TotalCalendarMonths - 1
    val titleColor = MiuixTheme.colorScheme.onSurfaceContainer

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.springAnimateToPage((pagerState.targetPage - 1).coerceAtLeast(0))
                    }
                },
                enabled = canGoPrevious,
            ) {
                Icon(
                    imageVector = MiuixIcons.ChevronBackward,
                    contentDescription = stringResource(R.string.calendar_previous_month),
                    tint = if (canGoPrevious) titleColor else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                )
            }
            Text(
                text = stringResource(
                    R.string.calendar_year_month,
                    displayedMonth.year,
                    displayedMonth.month.number,
                ),
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        pagerState.springAnimateToPage(
                            (pagerState.targetPage + 1).coerceAtMost(TotalCalendarMonths - 1),
                        )
                    }
                },
                enabled = canGoNext,
            ) {
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = stringResource(R.string.calendar_next_month),
                    tint = if (canGoNext) titleColor else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(HeaderVerticalSpacing))
        Row(modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dayOfWeek ->
                Text(
                    text = localizedDayName(dayOfWeek, TextStyle.NARROW),
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(modifier = Modifier.height(HeaderVerticalSpacing))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            CalendarMonthGrid(
                month = monthAtPage(page),
                selectedDate = selectedDate,
                today = today,
                onDateSelect = onDateSelect,
            )
        }
    }
}

@Composable
fun DatePickerSheet(
    show: Boolean,
    selectedDate: LocalDate,
    today: LocalDate,
    onDateConfirm: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit,
    title: String = stringResource(R.string.calendar_select_date),
) {
    val pagerState = rememberMonthPagerState(selectedDate.yearMonth)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedDate) {
        pagerState.scrollToPage(selectedDate.yearMonth.toPageIndex())
    }

    OverlayBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        onDismissFinished = {
            coroutineScope.launch {
                pagerState.scrollToPage(selectedDate.yearMonth.toPageIndex())
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = CalendarSheetBottomSpacing),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
                insideMargin = CalendarCardInsideMargin,
            ) {
                MonthCalendar(
                    selectedDate = selectedDate,
                    today = today,
                    onDateSelect = onDateConfirm,
                    pagerState = pagerState,
                )
            }
        }
    }
}

@Composable
private fun rememberMonthPagerState(initialMonth: YearMonth): PagerState = rememberPagerState(
    initialPage = initialMonth.toPageIndex(),
    pageCount = { TotalCalendarMonths },
)

private fun YearMonth.toPageIndex(): Int =
    ((year - BaseCalendarYear) * MonthsPerYear + (month.number - 1))
        .coerceIn(0, TotalCalendarMonths - 1)

private fun monthAtPage(page: Int): YearMonth {
    val clamped = page.coerceIn(0, TotalCalendarMonths - 1)
    val year = BaseCalendarYear + clamped / MonthsPerYear
    val monthNumber = (clamped % MonthsPerYear) + 1
    return YearMonth(year, monthNumber)
}

@Composable
private fun CalendarMonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    onDateSelect: (LocalDate) -> Unit,
) {
    val cells = remember(month) { buildCalendarMonthGrid(month) }

    Column(verticalArrangement = Arrangement.spacedBy(GridGap)) {
        repeat(CalendarGridRows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GridGap),
            ) {
                repeat(CalendarGridDaysPerWeek) { column ->
                    val cell = cells[row * CalendarGridDaysPerWeek + column]
                    CalendarDayCell(
                        date = cell.date,
                        isInDisplayedMonth = cell.isInDisplayedMonth,
                        isSelected = cell.date == selectedDate,
                        isToday = cell.date == today,
                        onClick = { onDateSelect(cell.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    isInDisplayedMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val dayOfWeekText = localizedDayName(date.dayOfWeek, TextStyle.FULL)
    val baseDescription = stringResource(
        R.string.calendar_date_display,
        date.month.number,
        date.day,
        dayOfWeekText,
    )
    val todayTag = stringResource(R.string.calendar_today)
    val fullDescription = if (isToday) "$baseDescription · $todayTag" else baseDescription
    val container = if (isToday) scheme.primary else Color.Transparent
    val textColor = when {
        isToday -> scheme.onPrimary
        isInDisplayedMonth -> scheme.onSurfaceContainer
        else -> scheme.onSurfaceVariantActions
    }
    val cellAlpha = dayCellAlpha(
        isInDisplayedMonth = isInDisplayedMonth,
        isHighlighted = isToday || isSelected,
    )
    val borderModifier = if (isSelected && !isToday) {
        Modifier.squircleBorder(
            width = SelectedBorderWidth,
            color = scheme.primary,
            cornerRadius = DayCornerRadius,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(DayCellHeight)
            .alpha(cellAlpha)
            .then(borderModifier)
            .squircleSurface(color = container, cornerRadius = DayCornerRadius)
            .selectable(
                selected = isSelected,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = fullDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.day.toString(),
            style = MiuixTheme.textStyles.body2,
            fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
        )
    }
}

private fun dayCellAlpha(
    isInDisplayedMonth: Boolean,
    isHighlighted: Boolean,
): Float = when {
    isInDisplayedMonth -> 1f
    isHighlighted -> OutsideMonthHighlightedAlpha
    else -> OutsideMonthAlpha
}

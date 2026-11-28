package com.illustar0.teishucan.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.TimetableWeek
import com.illustar0.teishucan.ui.theme.CardSpacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private const val WeeksPerRow = 4
private const val CurrentWeekRangeAlpha = 0.88f

private val GridSpacing = 8.dp
private val CellHeight = 48.dp
private val CellCornerRadius = 10.dp
private val SelectedBorderWidth = 1.75.dp
private val CellHorizontalPadding = 4.dp
private val CellLineGap = 2.dp

/**
 * 周次选择底栏，按 4 列网格列出 [weeks]。
 *
 * [currentWeek] 始终涂成主色实心块；选中的其他周 [selectedWeek] 只画主色描边，文字保持默认色。
 */
@Composable
internal fun WeekPickerSheet(
    show: Boolean,
    weeks: List<TimetableWeek>,
    selectedWeek: TimetableWeek?,
    currentWeek: TimetableWeek?,
    onWeekSelect: (TimetableWeek) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val rows = remember(weeks) { weeks.chunked(WeeksPerRow) }

    OverlayBottomSheet(
        show = show && weeks.isNotEmpty(),
        title = stringResource(R.string.timetable_select_week),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = CardSpacing),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
                insideMargin = PaddingValues(CardSpacing),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(GridSpacing)) {
                    rows.forEach { rowWeeks ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                        ) {
                            rowWeeks.forEach { week ->
                                WeekPickerCell(
                                    week = week,
                                    isSelected = week == selectedWeek,
                                    isCurrent = week == currentWeek,
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onWeekSelect(week)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(WeeksPerRow - rowWeeks.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekPickerCell(
    week: TimetableWeek,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val weekTitle = stringResource(R.string.timetable_week_number, week.number)
    val compactRange = remember(week) {
        formatCompactWeekRange(start = week.dates.first(), end = week.dates.last())
    }
    val currentWeekTag = stringResource(R.string.timetable_current_week)
    val containerColor = if (isCurrent) scheme.primary else scheme.surfaceContainer
    val titleColor = if (isCurrent) scheme.onPrimary else scheme.onSurfaceContainer
    val rangeColor = if (isCurrent) {
        scheme.onPrimary.copy(alpha = CurrentWeekRangeAlpha)
    } else {
        scheme.onSurfaceVariantSummary
    }
    val borderModifier = if (isSelected && !isCurrent) {
        Modifier.squircleBorder(
            width = SelectedBorderWidth,
            color = scheme.primary,
            cornerRadius = CellCornerRadius,
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .height(CellHeight)
            // squircleBorder 在内容之前绘制，必须放在不透明底色内层，否则会被底色盖住。
            .squircleSurface(color = containerColor, cornerRadius = CellCornerRadius)
            .then(borderModifier)
            .selectable(
                selected = isSelected,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = CellHorizontalPadding)
            .semantics {
                if (isCurrent) {
                    stateDescription = currentWeekTag
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = weekTitle,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(CellLineGap))
        Text(
            text = compactRange,
            style = MiuixTheme.textStyles.footnote2,
            color = rangeColor,
            maxLines = 1,
        )
    }
}

/**
 * 把 [start] 至 [end] 拼成选周格里的短日期。
 *
 * 同月省去后一个月份，如 `11.23–29`；跨月保留两端月份，如 `11.30–12.6`。
 */
internal fun formatCompactWeekRange(start: LocalDate, end: LocalDate): String =
    if (start.month == end.month) {
        "${start.month.number}.${start.day}–${end.day}"
    } else {
        "${start.month.number}.${start.day}–${end.month.number}.${end.day}"
    }

/** 跨多周跳转时返回紧邻 [targetPage] 的过渡页，供分页器先跳到该页再滑入目标周，避免滚过中间各周。 */
internal fun intermediateWeekPageForJump(currentPage: Int, targetPage: Int): Int? = when {
    targetPage - currentPage > 1 -> targetPage - 1
    currentPage - targetPage > 1 -> targetPage + 1
    else -> null
}

/** 本学期包含本周 [currentWeek]，且当前所看 [selectedWeek] 不是本周时，显示「回到本周」按钮。 */
internal fun shouldShowBackToCurrentWeek(
    selectedWeek: TimetableWeek?,
    currentWeek: TimetableWeek?,
): Boolean = currentWeek != null && selectedWeek != null && selectedWeek != currentWeek

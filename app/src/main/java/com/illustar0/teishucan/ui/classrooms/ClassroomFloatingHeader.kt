package com.illustar0.teishucan.ui.classrooms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.ClassroomCatalogData
import com.illustar0.teishucan.domain.ClassroomFilter
import com.illustar0.teishucan.domain.ClassroomTimeFilter
import com.illustar0.teishucan.domain.ScheduleUnit
import com.illustar0.teishucan.domain.contiguousPeriodEndpoints
import com.illustar0.teishucan.domain.effectiveUnits
import com.illustar0.teishucan.domain.groupScheduleUnitsByDayPart
import com.illustar0.teishucan.domain.labelOf
import com.illustar0.teishucan.ui.component.liquid.LiquidGlassFloatingToolbar
import com.illustar0.teishucan.ui.component.liquid.floatingPanel
import com.illustar0.teishucan.ui.component.localizedDayName
import com.illustar0.teishucan.ui.theme.LocalIsLiquidGlassEnabled
import java.time.format.TextStyle
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 教室列表顶部的悬浮栏：上面是日期切换胶囊，下面是当前生效的筛选胶囊行。
 * 直接采样列表的 [backdrop]，不走 `Scaffold.floatingToolbar`，免得重复叠一层状态栏内边距。
 */
@Composable
internal fun ClassroomFloatingHeader(
    selectedDate: LocalDate,
    today: LocalDate,
    filter: ClassroomFilter,
    catalog: ClassroomCatalogData?,
    backdrop: LayerBackdrop?,
    onDateSelect: (LocalDate) -> Unit,
    onOpenDatePicker: () -> Unit,
    onFilterChange: (ClassroomFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ClassroomDateToolbar(
            selectedDate = selectedDate,
            today = today,
            backdrop = backdrop,
            onDateSelect = onDateSelect,
            onOpenPicker = onOpenDatePicker,
        )
        AnimatedVisibility(
            visible = filter.hasAnyActiveFilter,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            FloatingActiveFilterChipsRow(
                filter = filter,
                catalog = catalog,
                backdrop = backdrop,
                onFilterChange = onFilterChange,
            )
        }
    }
}

private const val YesterdayOffsetDays = -1
private const val TodayOffsetDays = 0
private const val TomorrowOffsetDays = 1
private const val DayAfterTomorrowOffsetDays = 2
private val DateToolbarInnerPadding = 4.dp
private val DateCenterHorizontalPadding = 8.dp

@Composable
private fun ClassroomDateToolbar(
    selectedDate: LocalDate,
    today: LocalDate,
    backdrop: LayerBackdrop?,
    onDateSelect: (LocalDate) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val dayOfWeekText = localizedDayName(selectedDate.dayOfWeek, TextStyle.SHORT)
    val primaryText = stringResource(
        R.string.calendar_date_display,
        selectedDate.month.number,
        selectedDate.day,
        dayOfWeekText,
    )
    val secondaryText = when (today.daysUntil(selectedDate)) {
        TodayOffsetDays -> stringResource(R.string.calendar_today)
        TomorrowOffsetDays -> stringResource(R.string.classrooms_date_tomorrow)
        YesterdayOffsetDays -> stringResource(R.string.classrooms_date_yesterday)
        DayAfterTomorrowOffsetDays -> stringResource(R.string.classrooms_date_day_after_tomorrow)
        else -> stringResource(R.string.classrooms_date_year, selectedDate.year)
    }

    LiquidGlassFloatingToolbar(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DateToolbarInnerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onDateSelect(selectedDate.minus(1, DateTimeUnit.DAY)) }) {
                Icon(
                    imageVector = MiuixIcons.ChevronBackward,
                    contentDescription = stringResource(R.string.classrooms_previous_day),
                    tint = MiuixTheme.colorScheme.onSurfaceContainer,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable(
                        onClickLabel = stringResource(R.string.calendar_select_date),
                        role = Role.Button,
                        onClick = onOpenPicker,
                    )
                    .padding(horizontal = DateCenterHorizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = primaryText,
                    style = MiuixTheme.textStyles.headline1,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = secondaryText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { onDateSelect(selectedDate.plus(1, DateTimeUnit.DAY)) }) {
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = stringResource(R.string.classrooms_next_day),
                    tint = MiuixTheme.colorScheme.onSurfaceContainer,
                )
            }
        }
    }
}

private val FloatingChipsBottomPadding = 8.dp

@Composable
private fun FloatingActiveFilterChipsRow(
    filter: ClassroomFilter,
    catalog: ClassroomCatalogData?,
    backdrop: LayerBackdrop?,
    onFilterChange: (ClassroomFilter) -> Unit,
) {
    val selectedCampusName = remember(filter.campusId, catalog) {
        filter.campusId?.let { id -> catalog?.campuses?.find { it.id == id }?.name }
    }
    val selectedBuildingLabel = remember(filter.buildingIds, catalog) {
        filter.buildingIds.singleOrNull()?.let { id -> catalog?.buildings?.find { it.id == id }?.name }
    }
    val selectedRoomTypeLabel = remember(filter.roomTypeIds, catalog) {
        filter.roomTypeIds.singleOrNull()?.let { id -> catalog?.roomTypes?.find { it.id == id }?.name }
    }
    val timeLabel = activeTimeFilterLabel(filter.timeFilter, catalog.effectiveUnits)
    val seatsLabel = activeSeatsLabel(filter.minSeats, filter.maxSeats)

    // 不加 fillMaxWidth：只有一两枚胶囊时右侧留空，点按和上下滑动都能直接落到底层列表上。
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(
                start = ClassroomBrowserDefaults.HorizontalPadding,
                end = ClassroomBrowserDefaults.HorizontalPadding,
                bottom = FloatingChipsBottomPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.ChipSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!filter.onlyAvailable) {
            FloatingRemovableFilterChip(
                label = stringResource(R.string.classrooms_filter_show_all),
                backdrop = backdrop,
                onRemove = { onFilterChange(filter.copy(onlyAvailable = true)) },
            )
        }
        if (timeLabel != null) {
            FloatingRemovableFilterChip(
                label = timeLabel,
                backdrop = backdrop,
                onRemove = { onFilterChange(filter.copy(timeFilter = ClassroomTimeFilter.Periods())) },
            )
        }
        if (selectedCampusName != null) {
            FloatingRemovableFilterChip(
                label = selectedCampusName,
                backdrop = backdrop,
                onRemove = { onFilterChange(filter.copy(campusId = null)) },
            )
        }
        if (filter.buildingIds.isNotEmpty()) {
            FloatingRemovableFilterChip(
                label = selectedBuildingLabel
                    ?: stringResource(R.string.classrooms_filter_buildings_count, filter.buildingIds.size),
                backdrop = backdrop,
                onRemove = { onFilterChange(filter.copy(buildingIds = emptySet())) },
            )
        }
        if (filter.roomTypeIds.isNotEmpty()) {
            FloatingRemovableFilterChip(
                label = selectedRoomTypeLabel
                    ?: stringResource(R.string.classrooms_filter_room_types_count, filter.roomTypeIds.size),
                backdrop = backdrop,
                onRemove = { onFilterChange(filter.copy(roomTypeIds = emptySet())) },
            )
        }
        if (filter.floors.isNotEmpty()) {
            val singleFloor = filter.floors.singleOrNull()
            val floorLabel = if (singleFloor != null) {
                stringResource(R.string.classrooms_floor_label, singleFloor)
            } else {
                stringResource(R.string.classrooms_filter_floors_count, filter.floors.size)
            }
            FloatingRemovableFilterChip(
                label = floorLabel,
                backdrop = backdrop,
                onRemove = { onFilterChange(filter.copy(floors = emptySet())) },
            )
        }
        if (seatsLabel != null) {
            FloatingRemovableFilterChip(
                label = seatsLabel,
                backdrop = backdrop,
                onRemove = { onFilterChange(filter.copy(minSeats = null, maxSeats = null)) },
            )
        }
    }
}

private const val FloatingChipTintAlpha = 0.76f
private val RemoveChipIconSize = 13.dp

@Composable
private fun FloatingRemovableFilterChip(
    label: String,
    backdrop: LayerBackdrop?,
    onRemove: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnRemove by rememberUpdatedState(onRemove)
    val removeLabel = stringResource(R.string.classrooms_filter_remove)
    val isLiquidGlass = LocalIsLiquidGlassEnabled.current
    val tintAlpha = if (backdrop != null) FloatingChipTintAlpha else 1f
    val contentColor = MiuixTheme.colorScheme.onTertiaryContainer

    Row(
        modifier = Modifier
            .floatingPanel(
                backdrop = backdrop,
                isLiquidGlass = isLiquidGlass,
                shape = CircleShape,
            )
            .background(
                color = MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = tintAlpha),
                shape = CircleShape,
            )
            .clip(CircleShape)
            .clickable(
                onClickLabel = removeLabel,
                role = Role.Button,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                    currentOnRemove()
                },
            )
            .padding(
                horizontal = ClassroomBrowserDefaults.ChipHorizontalPadding,
                vertical = ClassroomBrowserDefaults.ChipVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
        Icon(
            imageVector = MiuixIcons.Close,
            contentDescription = null,
            modifier = Modifier.size(RemoveChipIconSize),
            tint = contentColor,
        )
    }
}

@Composable
@ReadOnlyComposable
private fun activeTimeFilterLabel(
    timeFilter: ClassroomTimeFilter,
    units: List<ScheduleUnit>,
): String? = when (timeFilter) {
    ClassroomTimeFilter.Now -> stringResource(R.string.classrooms_time_now)
    is ClassroomTimeFilter.Periods -> {
        val selected = timeFilter.periods
        if (selected.isEmpty()) {
            null
        } else {
            val dayPartGroups = groupScheduleUnitsByDayPart(units)
            val fullDayIndices = units.mapTo(HashSet(units.size), ScheduleUnit::index)
            val matchedDayPart = dayPartGroups.find { it.unitIndices == selected }
            val contiguousEndpoints = contiguousPeriodEndpoints(selected, units)
            when {
                selected == fullDayIndices -> if (units.size == 1) {
                    stringResource(R.string.classrooms_period_short, units.first().label)
                } else {
                    stringResource(R.string.classrooms_time_full_day, units.first().label, units.last().label)
                }

                matchedDayPart != null -> {
                    val partName = stringResource(matchedDayPart.dayPart.labelRes)
                    if (matchedDayPart.units.size == 1) {
                        stringResource(
                            R.string.classrooms_time_daypart_single,
                            partName,
                            matchedDayPart.units.single().label,
                        )
                    } else {
                        stringResource(
                            R.string.classrooms_time_daypart_range,
                            partName,
                            matchedDayPart.units.first().label,
                            matchedDayPart.units.last().label,
                        )
                    }
                }

                selected.size == 1 -> stringResource(
                    R.string.classrooms_period_short,
                    units.labelOf(selected.single()),
                )

                contiguousEndpoints != null -> stringResource(
                    R.string.classrooms_period_range,
                    contiguousEndpoints.first,
                    contiguousEndpoints.second,
                )

                else -> stringResource(
                    R.string.classrooms_period_list,
                    selected.sorted().joinToString(",") { units.labelOf(it) },
                )
            }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun activeSeatsLabel(minSeats: Int?, maxSeats: Int?): String? = when {
    minSeats != null && maxSeats != null -> stringResource(R.string.classrooms_filter_seats_range, minSeats, maxSeats)
    minSeats != null -> stringResource(R.string.classrooms_filter_seats_min_only, minSeats)
    maxSeats != null -> stringResource(R.string.classrooms_filter_seats_max_only, maxSeats)
    else -> null
}

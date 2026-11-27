package com.illustar0.teishucan.ui.classrooms

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.ClassroomCatalogData
import com.illustar0.teishucan.domain.ClassroomFilter
import com.illustar0.teishucan.domain.ClassroomTimeFilter
import com.illustar0.teishucan.domain.ScheduleUnit
import com.illustar0.teishucan.domain.effectiveUnits
import com.illustar0.teishucan.domain.groupScheduleUnitsByDayPart
import com.illustar0.teishucan.ui.component.SheetActionButton
import com.illustar0.teishucan.ui.component.positiveIntInput
import com.illustar0.teishucan.ui.theme.ActionButtonSpacing
import com.illustar0.teishucan.ui.theme.CardSpacing
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val MaxSeatDigits = 4
private val SeatCountInput = positiveIntInput(maxDigits = MaxSeatDigits)

/** 教室筛选弹层：编辑期间先改草稿，点底部「应用」或「重置」时才真正提交。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClassroomFilterSheet(
    show: Boolean,
    filter: ClassroomFilter,
    catalog: ClassroomCatalogData?,
    isToday: Boolean,
    onApply: (ClassroomFilter) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var draft by remember(show, filter) { mutableStateOf(filter) }
    val minSeatsState = remember(show, filter) { TextFieldState(filter.minSeats?.toString().orEmpty()) }
    val maxSeatsState = remember(show, filter) { TextFieldState(filter.maxSeats?.toString().orEmpty()) }
    val minSeats = minSeatsState.text.toString().toIntOrNull()?.takeIf { it > 0 }
    val maxSeats = maxSeatsState.text.toString().toIntOrNull()?.takeIf { it > 0 }
    val isSeatsRangeInvalid = minSeats != null && maxSeats != null && minSeats > maxSeats

    val availableBuildings = remember(catalog, draft.campusId) {
        val allBuildings = catalog?.buildings.orEmpty()
        val campusId = draft.campusId
        if (campusId == null) allBuildings else allBuildings.filter { it.campusId == campusId }
    }

    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.classrooms_filter_title),
        startAction = {
            SheetActionButton(
                icon = MiuixIcons.Close,
                contentDescription = stringResource(R.string.electricity_meter_close),
                onClick = onDismissRequest,
            )
        },
        onDismissRequest = onDismissRequest,
        onDismissFinished = { draft = filter },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = ClassroomBrowserDefaults.SheetBottomSpacing),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .scrollEndHaptic()
                    .overScrollVertical(),
                overscrollEffect = null,
            ) {
                item(key = "only-available") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.classrooms_filter_only_available),
                            summary = stringResource(R.string.classrooms_filter_only_available_summary),
                            checked = draft.onlyAvailable,
                            onCheckedChange = { draft = draft.copy(onlyAvailable = it) },
                        )
                    }
                }

                item(key = "periods") {
                    FilterSectionCard(titleRes = R.string.classrooms_filter_periods) {
                        PeriodFilterSelector(
                            timeFilter = draft.timeFilter,
                            isToday = isToday,
                            units = catalog.effectiveUnits,
                            onTimeFilterChange = { draft = draft.copy(timeFilter = it) },
                        )
                    }
                }

                if (!catalog?.campuses.isNullOrEmpty()) {
                    item(key = "campuses") {
                        FilterSectionCard(titleRes = R.string.classrooms_filter_campus) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.ChipSpacing),
                                verticalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.ChipSpacing),
                            ) {
                                FilterChip(
                                    label = stringResource(R.string.classrooms_filter_all),
                                    selected = draft.campusId == null,
                                    selectionMode = ChipSelectionMode.SingleSelect,
                                    onClick = { draft = draft.copy(campusId = null) },
                                )
                                catalog.campuses.forEach { campus ->
                                    val isSelected = draft.campusId == campus.id
                                    FilterChip(
                                        label = campus.name,
                                        selected = isSelected,
                                        selectionMode = ChipSelectionMode.SingleSelect,
                                        onClick = {
                                            val nextCampus = if (isSelected) null else campus.id
                                            val validBuildingIds = if (nextCampus == null) {
                                                draft.buildingIds
                                            } else {
                                                val campusBuildings = catalog.buildings
                                                    .filter { it.campusId == nextCampus }
                                                    .map { it.id }
                                                    .toSet()
                                                draft.buildingIds.intersect(campusBuildings)
                                            }
                                            draft = draft.copy(campusId = nextCampus, buildingIds = validBuildingIds)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (availableBuildings.isNotEmpty()) {
                    item(key = "buildings") {
                        FilterSectionCard(titleRes = R.string.classrooms_filter_building) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.ChipSpacing),
                                verticalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.ChipSpacing),
                            ) {
                                availableBuildings.forEach { building ->
                                    val isSelected = building.id in draft.buildingIds
                                    FilterChip(
                                        label = building.name,
                                        selected = isSelected,
                                        selectionMode = ChipSelectionMode.Toggle,
                                        onClick = {
                                            val next = if (isSelected) {
                                                draft.buildingIds - building.id
                                            } else {
                                                draft.buildingIds + building.id
                                            }
                                            draft = draft.copy(buildingIds = next)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (!catalog?.roomTypes.isNullOrEmpty()) {
                    item(key = "room-types") {
                        FilterSectionCard(titleRes = R.string.classrooms_filter_room_type) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.ChipSpacing),
                                verticalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.ChipSpacing),
                            ) {
                                catalog.roomTypes.forEach { roomType ->
                                    val isSelected = roomType.id in draft.roomTypeIds
                                    FilterChip(
                                        label = roomType.name,
                                        selected = isSelected,
                                        selectionMode = ChipSelectionMode.Toggle,
                                        onClick = {
                                            val next = if (isSelected) {
                                                draft.roomTypeIds - roomType.id
                                            } else {
                                                draft.roomTypeIds + roomType.id
                                            }
                                            draft = draft.copy(roomTypeIds = next)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (!catalog?.floors.isNullOrEmpty()) {
                    item(key = "floors") {
                        FilterSectionCard(titleRes = R.string.classrooms_filter_floor) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.ChipSpacing),
                                verticalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.ChipSpacing),
                            ) {
                                catalog.floors.forEach { floor ->
                                    val isSelected = floor in draft.floors
                                    FilterChip(
                                        label = stringResource(R.string.classrooms_floor_label, floor),
                                        selected = isSelected,
                                        selectionMode = ChipSelectionMode.Toggle,
                                        onClick = {
                                            val next = if (isSelected) draft.floors - floor else draft.floors + floor
                                            draft = draft.copy(floors = next)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "seats") {
                    FilterSectionCard(titleRes = R.string.classrooms_filter_seats) {
                        SeatsRangeFields(
                            minSeatsState = minSeatsState,
                            maxSeatsState = maxSeatsState,
                            isRangeInvalid = isSeatsRangeInvalid,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ClassroomBrowserDefaults.SheetSectionSpacing),
                horizontalArrangement = Arrangement.spacedBy(ActionButtonSpacing),
            ) {
                TextButton(
                    text = stringResource(R.string.classrooms_filter_reset),
                    onClick = { onApply(ClassroomFilter()) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.classrooms_filter_apply),
                    onClick = { onApply(draft.copy(minSeats = minSeats, maxSeats = maxSeats)) },
                    modifier = Modifier.weight(1f),
                    enabled = !isSeatsRangeInvalid,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun FilterSectionCard(
    @StringRes titleRes: Int,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(
            text = stringResource(titleRes),
            modifier = Modifier.padding(top = ClassroomBrowserDefaults.SheetSectionSpacing),
            insideMargin = ClassroomBrowserDefaults.SheetTitleMargin,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
            insideMargin = ClassroomBrowserDefaults.SheetCardInsideMargin,
        ) {
            content()
        }
    }
}

private enum class TimeFilterTab(@param:StringRes val labelRes: Int) {
    AllDay(R.string.classrooms_status_all_day_free),
    Now(R.string.classrooms_time_now),
    CustomPeriods(R.string.classrooms_time_custom_periods),
}

private const val DimmedGridAlpha = 0.45f
private val PeriodPickVerticalPadding = 8.dp
private val DayPartButtonHorizontalPadding = 4.dp

@Composable
private fun PeriodFilterSelector(
    timeFilter: ClassroomTimeFilter,
    isToday: Boolean,
    units: List<ScheduleUnit>,
    onTimeFilterChange: (ClassroomTimeFilter) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val dayPartGroups = remember(units) { groupScheduleUnitsByDayPart(units) }
    val selectedPeriods = (timeFilter as? ClassroomTimeFilter.Periods)?.periods.orEmpty()
    val isCustomPeriods = timeFilter is ClassroomTimeFilter.Periods && selectedPeriods.isNotEmpty()
    var rememberedCustomPeriods by remember(units) {
        mutableStateOf(selectedPeriods.ifEmpty { dayPartGroups.first().unitIndices })
    }
    if (selectedPeriods.isNotEmpty()) {
        rememberedCustomPeriods = selectedPeriods
    }
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnTimeFilterChange by rememberUpdatedState(onTimeFilterChange)

    val tabs = if (isToday) {
        TimeFilterTab.entries
    } else {
        listOf(TimeFilterTab.AllDay, TimeFilterTab.CustomPeriods)
    }
    val activeTab = when {
        isToday && timeFilter == ClassroomTimeFilter.Now -> TimeFilterTab.Now
        isCustomPeriods -> TimeFilterTab.CustomPeriods
        else -> TimeFilterTab.AllDay
    }
    val selectedTabIndex = tabs.indexOf(activeTab).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.SheetSectionSpacing)) {
        TabRowWithContour(
            tabs = tabs.map { tab -> stringResource(tab.labelRes) },
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { index ->
                val chosenTab = tabs.getOrNull(index) ?: return@TabRowWithContour
                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                val nextFilter = when (chosenTab) {
                    TimeFilterTab.AllDay -> ClassroomTimeFilter.Periods()
                    TimeFilterTab.Now -> ClassroomTimeFilter.Now
                    TimeFilterTab.CustomPeriods -> ClassroomTimeFilter.Periods(rememberedCustomPeriods)
                }
                currentOnTimeFilterChange(nextFilter)
            },
            colors = TabRowDefaults.tabRowColors(
                backgroundColor = scheme.surfaceContainer,
                contentColor = scheme.onSurfaceVariantSummary,
                selectedBackgroundColor = scheme.tertiaryContainer,
                selectedContentColor = scheme.onTertiaryContainer,
            ),
        )

        ScheduleUnitGrid(
            dayPartGroups = dayPartGroups,
            dayPartLabel = { dayPartGroup ->
                val isDayPartAllPicked = isCustomPeriods && dayPartGroup.unitIndices.all { it in selectedPeriods }
                Box(
                    modifier = Modifier
                        .width(ClassroomBrowserDefaults.DayPartLabelWidth)
                        .squircleClip(ClassroomBrowserDefaults.PeriodGridCellCornerRadius)
                        .toggleable(
                            value = isDayPartAllPicked,
                            role = Role.Checkbox,
                            onValueChange = {
                                val feedback = if (isDayPartAllPicked) {
                                    HapticFeedbackType.ToggleOff
                                } else {
                                    HapticFeedbackType.ToggleOn
                                }
                                hapticFeedback.performHapticFeedback(feedback)
                                val base = if (isCustomPeriods) selectedPeriods else emptySet()
                                val next = if (isDayPartAllPicked) {
                                    base - dayPartGroup.unitIndices
                                } else {
                                    base + dayPartGroup.unitIndices
                                }
                                currentOnTimeFilterChange(ClassroomTimeFilter.Periods(next))
                            },
                        )
                        .padding(
                            vertical = PeriodPickVerticalPadding,
                            horizontal = DayPartButtonHorizontalPadding,
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(dayPartGroup.dayPart.labelRes),
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDayPartAllPicked) scheme.primary else scheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
            },
            modifier = Modifier.alpha(if (isCustomPeriods) 1f else DimmedGridAlpha),
        ) { unit ->
            val periodNumber = unit.index
            val isPicked = isCustomPeriods && periodNumber in selectedPeriods
            Box(
                modifier = Modifier
                    .weight(1f)
                    .squircleSurface(
                        color = ClassroomBrowserDefaults.chipContainerColor(isPicked),
                        cornerRadius = ClassroomBrowserDefaults.PeriodGridCellCornerRadius,
                    )
                    .toggleable(
                        value = isPicked,
                        role = Role.Checkbox,
                        onValueChange = {
                            hapticFeedback.performHapticFeedback(
                                if (isPicked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                            )
                            val base = if (isCustomPeriods) selectedPeriods else emptySet()
                            val next = if (isPicked) base - periodNumber else base + periodNumber
                            currentOnTimeFilterChange(ClassroomTimeFilter.Periods(next))
                        },
                    )
                    .padding(vertical = PeriodPickVerticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.classrooms_period_short, unit.label),
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = if (isPicked) FontWeight.SemiBold else FontWeight.Normal,
                    color = ClassroomBrowserDefaults.chipContentColor(isPicked),
                    maxLines = 1,
                )
            }
        }
    }
}

private enum class ChipSelectionMode {
    Toggle,
    SingleSelect,
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    selectionMode: ChipSelectionMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentSelected by rememberUpdatedState(selected)

    val interactiveModifier = when (selectionMode) {
        ChipSelectionMode.Toggle -> Modifier.toggleable(
            value = selected,
            role = Role.Checkbox,
            onValueChange = {
                hapticFeedback.performHapticFeedback(
                    if (currentSelected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                )
                currentOnClick()
            },
        )

        ChipSelectionMode.SingleSelect -> Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = {
                hapticFeedback.performHapticFeedback(
                    if (currentSelected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                )
                currentOnClick()
            },
        )
    }

    Row(
        modifier = modifier
            .squircleSurface(
                color = ClassroomBrowserDefaults.chipContainerColor(selected),
                cornerRadius = ClassroomBrowserDefaults.ChipCornerRadius,
            )
            .then(interactiveModifier)
            .padding(
                horizontal = ClassroomBrowserDefaults.ChipHorizontalPadding,
                vertical = ClassroomBrowserDefaults.ChipVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = ClassroomBrowserDefaults.chipContentColor(selected),
            maxLines = 1,
        )
    }
}

private val NestedFieldCornerInset = 4.dp

/** 卡片圆角向内缩 4 dp（16 dp − 4 dp = 12 dp），让嵌在卡片里的输入框保持同心圆角。 */
private val NestedFieldCornerRadius = (CardDefaults.CornerRadius - NestedFieldCornerInset).coerceAtLeast(0.dp)
private val SeatsErrorTopPadding = 8.dp

@Composable
private fun SeatsRangeFields(
    minSeatsState: TextFieldState,
    maxSeatsState: TextFieldState,
    isRangeInvalid: Boolean,
) {
    val errorColor = MiuixTheme.colorScheme.error
    val containerColor = MiuixTheme.colorScheme.surfaceContainer
    val fieldColors = if (isRangeInvalid) {
        TextFieldDefaults.textFieldColors(
            backgroundColor = containerColor,
            labelColor = errorColor,
            borderColor = errorColor,
        )
    } else {
        TextFieldDefaults.textFieldColors(backgroundColor = containerColor)
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
        ) {
            TextField(
                state = minSeatsState,
                modifier = Modifier.weight(1f),
                inputTransformation = SeatCountInput,
                label = stringResource(R.string.classrooms_filter_min_seats),
                colors = fieldColors,
                cornerRadius = NestedFieldCornerRadius,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            TextField(
                state = maxSeatsState,
                modifier = Modifier.weight(1f),
                inputTransformation = SeatCountInput,
                label = stringResource(R.string.classrooms_filter_max_seats),
                colors = fieldColors,
                cornerRadius = NestedFieldCornerRadius,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        }
        if (isRangeInvalid) {
            Text(
                text = stringResource(R.string.classrooms_filter_seats_invalid),
                modifier = Modifier.padding(
                    start = TextFieldDefaults.InsideMargin.width,
                    end = TextFieldDefaults.InsideMargin.width,
                    top = SeatsErrorTopPadding,
                ),
                style = MiuixTheme.textStyles.footnote1,
                color = errorColor,
            )
        }
    }
}

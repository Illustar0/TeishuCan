package com.illustar0.teishucan.ui.classrooms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.ClassroomActivityKind
import com.illustar0.teishucan.domain.ClassroomBuilding
import com.illustar0.teishucan.domain.ClassroomBuildingGroup
import com.illustar0.teishucan.domain.ClassroomCampus
import com.illustar0.teishucan.domain.ClassroomCatalogData
import com.illustar0.teishucan.domain.ClassroomFilter
import com.illustar0.teishucan.domain.ClassroomItem
import com.illustar0.teishucan.domain.ClassroomLoadProgress
import com.illustar0.teishucan.domain.ClassroomOccupation
import com.illustar0.teishucan.domain.ClassroomTimeFilter
import com.illustar0.teishucan.domain.ClassroomType
import com.illustar0.teishucan.domain.ScheduleUnit
import com.illustar0.teishucan.domain.ThemeSettings
import com.illustar0.teishucan.domain.effectiveUnits
import com.illustar0.teishucan.domain.groupClassroomsByBuilding
import com.illustar0.teishucan.ui.component.DatePickerSheet
import com.illustar0.teishucan.ui.component.SearchBarTrigger
import com.illustar0.teishucan.ui.component.SearchPager
import com.illustar0.teishucan.ui.component.SearchStatus
import com.illustar0.teishucan.ui.component.rememberSearchStatus
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.CardSpacing
import com.illustar0.teishucan.ui.theme.LocalIsBlurEnabled
import com.illustar0.teishucan.ui.theme.LocalIsLiquidGlassEnabled
import com.illustar0.teishucan.ui.theme.TeishuCanTheme
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val SearchBarMaxTopPadding = CardSpacing

private sealed interface ClassroomSheet {
    data object DatePicker : ClassroomSheet
    data object Filter : ClassroomSheet
    data class Detail(val room: ClassroomItem) : ClassroomSheet
}

@Composable
fun AvailableClassroomsPage(
    state: AvailableClassroomsState,
    onDateSelect: (LocalDate) -> Unit,
    onFilterChange: (ClassroomFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val isBarBlurEnabled = LocalIsBlurEnabled.current
    val isLiquidGlass = LocalIsLiquidGlassEnabled.current
    // 液态玻璃的悬浮胶囊也采样列表图层，但顶栏只看模糊开关。
    val backdrop = rememberBlurBackdrop(isBarBlurEnabled || isLiquidGlass)
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val searchPlaceholder = stringResource(R.string.classrooms_search_placeholder)
    var searchStatus by rememberSearchStatus()
    val currentOnSearchQueryChange by rememberUpdatedState(onSearchQueryChange)
    var scaffoldWindowY by remember { mutableFloatStateOf(0f) }
    var triggerWindowY by remember { mutableFloatStateOf(0f) }
    var activeSheet by remember { mutableStateOf<ClassroomSheet?>(null) }
    val activeFilterCount = state.filter.activeFilterCount
    val isToday = state.selectedDate == state.today
    val units = state.catalog.effectiveUnits
    val selectedPeriods = (state.filter.timeFilter as? ClassroomTimeFilter.Periods)?.periods.orEmpty()
    val searchBarTopPadding = { SearchBarMaxTopPadding * (1f - scrollBehavior.state.collapsedFraction) }
    val collapsedOffsetY = with(density) { (triggerWindowY - scaffoldWindowY).toDp() }
    val isSearching = searchStatus.query.isNotBlank()

    LaunchedEffect(searchStatus.query) {
        currentOnSearchQueryChange(searchStatus.query)
    }

    Scaffold(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            scaffoldWindowY = coordinates.positionInWindow().y
        },
        topBar = {
            BlurredBar(backdrop.takeIf { isBarBlurEnabled }) { containerColor ->
                TopAppBar(
                    title = stringResource(R.string.service_available_classrooms),
                    color = containerColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.navigate_back),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !state.isRefreshing,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = stringResource(R.string.classrooms_refresh),
                                tint = if (state.isRefreshing) {
                                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                } else {
                                    MiuixTheme.colorScheme.onBackground
                                },
                            )
                        }
                        IconButton(onClick = { activeSheet = ClassroomSheet.Filter }) {
                            BadgedBox(
                                badge = {
                                    if (activeFilterCount > 0) {
                                        Badge(
                                            containerColor = MiuixTheme.colorScheme.primary,
                                            contentColor = MiuixTheme.colorScheme.onPrimary,
                                        ) {
                                            Text(text = activeFilterCount.toString())
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Filter,
                                    contentDescription = stringResource(R.string.classrooms_filter_button),
                                    tint = if (activeFilterCount > 0) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onBackground
                                    },
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    bottomContent = {
                        SearchBarTrigger(
                            placeholder = searchPlaceholder,
                            isCollapsed = searchStatus.isCollapsed,
                            topPadding = searchBarTopPadding,
                            onPositionedWindowY = { triggerWindowY = it },
                            onExpand = {
                                searchStatus = searchStatus.copy(phase = SearchStatus.Phase.Expanding)
                            },
                        )
                    },
                )
            }
        },
        popupHost = {
            SearchPager(
                status = searchStatus,
                placeholder = searchPlaceholder,
                collapsedOffsetY = collapsedOffsetY,
                searchBarTopPadding = searchBarTopPadding,
                onStatusChange = { searchStatus = it },
            ) {
                if (isSearching) {
                    ClassroomSearchResults(
                        groups = state.searchResultGroups,
                        isRefreshing = state.isRefreshing,
                        loadProgress = state.loadProgress,
                        timeFilter = state.filter.timeFilter,
                        currentTime = state.currentTime,
                        isToday = isToday,
                        units = units,
                        onRoomClick = { room ->
                            focusManager.clearFocus()
                            activeSheet = ClassroomSheet.Detail(room)
                        },
                    )
                }
            }
            MiuixPopupHost()
        },
    ) { innerPadding ->
        ClassroomBrowser(
            selectedDate = state.selectedDate,
            today = state.today,
            currentTime = state.currentTime,
            catalog = state.catalog,
            filter = state.filter,
            groups = state.groups,
            totalMatchedCount = state.filteredRooms.size,
            status = state.status,
            loadProgress = state.loadProgress,
            lastUpdatedAt = state.lastUpdatedAt,
            onDateSelect = onDateSelect,
            onOpenDatePicker = { activeSheet = ClassroomSheet.DatePicker },
            onFilterChange = onFilterChange,
            onRoomClick = { activeSheet = ClassroomSheet.Detail(it) },
            onRetry = onRefresh,
            modifier = Modifier.fillMaxSize(),
            backdrop = backdrop,
            contentPadding = innerPadding,
            nestedScrollConnection = scrollBehavior.nestedScrollConnection,
        )
        DatePickerSheet(
            show = activeSheet is ClassroomSheet.DatePicker,
            selectedDate = state.selectedDate,
            today = state.today,
            onDateConfirm = { date ->
                onDateSelect(date)
                activeSheet = null
            },
            onDismissRequest = {
                if (activeSheet is ClassroomSheet.DatePicker) activeSheet = null
            },
        )
        ClassroomFilterSheet(
            show = activeSheet is ClassroomSheet.Filter,
            filter = state.filter,
            catalog = state.catalog,
            isToday = isToday,
            onApply = { updated ->
                onFilterChange(updated)
                activeSheet = null
            },
            onDismissRequest = {
                if (activeSheet is ClassroomSheet.Filter) activeSheet = null
            },
        )
        ClassroomDetailSheet(
            room = (activeSheet as? ClassroomSheet.Detail)?.room,
            selectedPeriods = selectedPeriods,
            onDismissRequest = {
                if (activeSheet is ClassroomSheet.Detail) activeSheet = null
            },
            units = units,
        )
    }
}

private val SearchStateHorizontalPadding = 24.dp
private val SearchStateVerticalPadding = 48.dp

@Composable
private fun ClassroomSearchResults(
    groups: List<ClassroomBuildingGroup>,
    isRefreshing: Boolean,
    loadProgress: ClassroomLoadProgress?,
    timeFilter: ClassroomTimeFilter,
    currentTime: LocalTime,
    isToday: Boolean,
    units: List<ScheduleUnit>,
    onRoomClick: (ClassroomItem) -> Unit,
) {
    when {
        isRefreshing -> {
            ClassroomLoadingIndicator(
                loadProgress = loadProgress,
                summaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(
                    horizontal = SearchStateHorizontalPadding,
                    vertical = SearchStateVerticalPadding,
                ),
            )
        }
        groups.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SearchStateHorizontalPadding,
                        vertical = SearchStateVerticalPadding,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.classrooms_search_empty),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        else -> {
            val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical(),
                contentPadding = PaddingValues(
                    bottom = maxOf(imeBottom, navBottom) + CardSpacing,
                ),
                overscrollEffect = null,
            ) {
                classroomBuildingGroups(
                    groups = groups,
                    timeFilter = timeFilter,
                    currentTime = currentTime,
                    isToday = isToday,
                    units = units,
                    onRoomClick = onRoomClick,
                    showCampusInHeader = false,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AvailableClassroomsPagePreview() {
    val rooms = listOf(
        ClassroomItem(
            id = 1,
            code = "N1-101",
            name = "南核心101",
            buildingId = 10,
            buildingName = "南核心教学楼",
            campusId = 1,
            campusName = "主校区",
            occupations = emptyList(),
        ),
        ClassroomItem(
            id = 2,
            code = "N1-203",
            name = "南核心203",
            buildingId = 10,
            buildingName = "南核心教学楼",
            campusId = 1,
            campusName = "主校区",
            occupations = listOf(
                ClassroomOccupation(
                    activityKind = ClassroomActivityKind.Lesson,
                    activityName = "高等数学",
                    teacherName = "王老师",
                    startTime = LocalTime(8, 0),
                    endTime = LocalTime(9, 50),
                ),
            ),
        ),
    )
    val previewDate = LocalDate(2026, 11, 24)
    TeishuCanTheme(settings = ThemeSettings(isBlurEnabled = false), isDark = false) {
        AvailableClassroomsPage(
            state = AvailableClassroomsState(
                selectedDate = previewDate,
                today = previewDate,
                currentTime = LocalTime(10, 30),
                catalog = ClassroomCatalogData(
                    campuses = listOf(ClassroomCampus(1, "主校区")),
                    buildings = listOf(ClassroomBuilding(10, "南核心教学楼", 1)),
                    roomTypes = listOf(ClassroomType(1, "多媒体教室")),
                    floors = listOf(1, 2, 3),
                ),
                dayPages = mapOf(
                    previewDate to ClassroomDayPageState(
                        date = previewDate,
                        filteredRooms = rooms,
                        groups = groupClassroomsByBuilding(rooms),
                        status = ClassroomBrowserStatus.Ready,
                    ),
                ),
            ),
            onDateSelect = {},
            onFilterChange = {},
            onSearchQueryChange = {},
            onRefresh = {},
            onBack = {},
        )
    }
}

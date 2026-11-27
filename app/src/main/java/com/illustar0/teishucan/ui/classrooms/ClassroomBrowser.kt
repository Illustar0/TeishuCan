package com.illustar0.teishucan.ui.classrooms

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.data.CampusZone
import com.illustar0.teishucan.domain.ClassroomBuildingGroup
import com.illustar0.teishucan.domain.ClassroomCatalogData
import com.illustar0.teishucan.domain.ClassroomFilter
import com.illustar0.teishucan.domain.ClassroomItem
import com.illustar0.teishucan.domain.ClassroomLoadProgress
import com.illustar0.teishucan.domain.ClassroomTimeFilter
import com.illustar0.teishucan.domain.DefaultScheduleUnits
import com.illustar0.teishucan.domain.ScheduleDayPart
import com.illustar0.teishucan.domain.ScheduleDayPartGroup
import com.illustar0.teishucan.domain.ScheduleUnit
import com.illustar0.teishucan.domain.effectiveUnits
import com.illustar0.teishucan.domain.matchesAvailability
import com.illustar0.teishucan.domain.unitPositionRanges
import com.illustar0.teishucan.ui.component.PeriodStrip
import com.illustar0.teishucan.ui.component.PeriodStripDefaults
import com.illustar0.teishucan.ui.component.pageSlide
import com.illustar0.teishucan.ui.component.rememberClassTimeFormatter
import com.illustar0.teishucan.ui.theme.CardSpacing
import com.illustar0.teishucan.ui.theme.blurSource
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTitleDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

enum class ClassroomBrowserStatus {
    Ready,
    Loading,
    Empty,
    Failed,
}

private data class ClassroomDayContent(
    val date: LocalDate,
    val isToday: Boolean,
    val status: ClassroomBrowserStatus,
    val groups: List<ClassroomBuildingGroup>,
    val totalMatchedCount: Int,
    val loadProgress: ClassroomLoadProgress?,
    val lastUpdatedAt: Instant?,
    val filter: ClassroomFilter,
    val units: List<ScheduleUnit>,
)

/** 空闲教室主视图：底部为按日横滑的教室列表，上方悬浮日期切换栏与筛选胶囊。 */
@Composable
internal fun ClassroomBrowser(
    selectedDate: LocalDate,
    today: LocalDate,
    currentTime: LocalTime,
    catalog: ClassroomCatalogData?,
    filter: ClassroomFilter,
    groups: List<ClassroomBuildingGroup>,
    totalMatchedCount: Int,
    status: ClassroomBrowserStatus,
    loadProgress: ClassroomLoadProgress?,
    lastUpdatedAt: Instant?,
    onDateSelect: (LocalDate) -> Unit,
    onOpenDatePicker: () -> Unit,
    onFilterChange: (ClassroomFilter) -> Unit,
    onRoomClick: (ClassroomItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    contentPadding: PaddingValues = ClassroomBrowserDefaults.ContentPadding,
    nestedScrollConnection: NestedScrollConnection? = null,
) {
    var floatingHeaderHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isToday = selectedDate == today
    val units = catalog.effectiveUnits
    val topPadding = contentPadding.calculateTopPadding()
    val floatingClearance = (
        floatingHeaderHeight - FloatingToolbarDefaults.OutSidePadding.calculateBottomPadding()
    ).coerceAtLeast(0.dp)
    val dayContent = ClassroomDayContent(
        date = selectedDate,
        isToday = isToday,
        status = status,
        groups = groups,
        totalMatchedCount = totalMatchedCount,
        loadProgress = loadProgress,
        lastUpdatedAt = lastUpdatedAt,
        filter = filter,
        units = units,
    )

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = dayContent,
            modifier = Modifier
                .fillMaxSize()
                .blurSource(backdrop)
                .clipToBounds(),
            transitionSpec = { pageSlide(isForward = targetState.date > initialState.date) },
            contentKey = { it.date },
            label = "ClassroomDaySlide",
        ) { content ->
            val listModifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .then(if (nestedScrollConnection != null) Modifier.nestedScroll(nestedScrollConnection) else Modifier)

            LazyColumn(
                modifier = listModifier,
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    top = topPadding + floatingClearance,
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                overscrollEffect = null,
            ) {
                if (content.status == ClassroomBrowserStatus.Ready) {
                    item(key = "summary") {
                        ClassroomSummaryRow(
                            totalCount = content.totalMatchedCount,
                            onlyAvailable = content.filter.onlyAvailable,
                            lastUpdatedAt = content.lastUpdatedAt,
                            modifier = Modifier.padding(
                                start = ClassroomBrowserDefaults.SummaryHorizontalPadding,
                                top = ClassroomBrowserDefaults.SummaryTopSpacing,
                                end = ClassroomBrowserDefaults.SummaryHorizontalPadding,
                            ),
                        )
                    }
                    classroomBuildingGroups(
                        groups = content.groups,
                        timeFilter = content.filter.timeFilter,
                        currentTime = currentTime,
                        isToday = content.isToday,
                        units = content.units,
                        onRoomClick = onRoomClick,
                        showCampusInHeader = true,
                    )
                    item(key = "bottom-space", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(ClassroomBrowserDefaults.SectionSpacing))
                    }
                } else {
                    item(key = "status") {
                        ClassroomStatusView(
                            status = content.status,
                            loadProgress = content.loadProgress,
                            hasActiveFilter = content.filter.hasAnyActiveFilter,
                            onResetFilter = { onFilterChange(ClassroomFilter()) },
                            onRetry = onRetry,
                        )
                    }
                }
            }
        }

        ClassroomFloatingHeader(
            selectedDate = selectedDate,
            today = today,
            filter = filter,
            catalog = catalog,
            backdrop = backdrop,
            onDateSelect = onDateSelect,
            onOpenDatePicker = onOpenDatePicker,
            onFilterChange = onFilterChange,
            modifier = Modifier
                .padding(top = topPadding)
                .onSizeChanged { size -> floatingHeaderHeight = with(density) { size.height.toDp() } },
        )
    }
}

/**
 * 按教学楼向列表追加教室行，每间教室单独占一个懒加载项。
 * 首尾两行画超椭圆圆角、中间行拼成直角，合起来跟整张卡片一样，又不必一口气测量整栋楼。
 */
internal fun LazyListScope.classroomBuildingGroups(
    groups: List<ClassroomBuildingGroup>,
    timeFilter: ClassroomTimeFilter,
    currentTime: LocalTime,
    isToday: Boolean,
    units: List<ScheduleUnit>,
    onRoomClick: (ClassroomItem) -> Unit,
    showCampusInHeader: Boolean = true,
    headerTopPadding: Dp = ClassroomBrowserDefaults.SectionSpacing,
) {
    val selectedPeriods = (timeFilter as? ClassroomTimeFilter.Periods)?.periods.orEmpty()
    groups.forEachIndexed { groupIndex, group ->
        val headerTitle = if (showCampusInHeader &&
            group.campusName.isNotBlank() &&
            group.campusName != group.buildingName
        ) {
            "${group.buildingName} · ${group.campusName} (${group.rooms.size})"
        } else {
            "${group.buildingName} (${group.rooms.size})"
        }
        val topPadding = if (groupIndex == 0) {
            ClassroomBrowserDefaults.FirstGroupHeaderTopPadding
        } else {
            headerTopPadding
        }
        item(
            key = "building:${group.buildingId}:${group.buildingName}",
            contentType = "building-header",
        ) {
            SmallTitle(
                text = headerTitle,
                modifier = Modifier.padding(top = topPadding),
            )
        }
        val lastIndex = group.rooms.lastIndex
        itemsIndexed(
            items = group.rooms,
            key = { _, room -> room.id },
            contentType = { _, _ -> "classroom-row" },
        ) { index, room ->
            ClassroomGroupedCardRow(
                room = room,
                isAvailable = room.matchesAvailability(timeFilter, currentTime, isToday, units),
                selectedPeriods = selectedPeriods,
                units = units,
                isFirst = index == 0,
                isLast = index == lastIndex,
                onClick = { onRoomClick(room) },
            )
        }
    }
}

@Composable
private fun ClassroomGroupedCardRow(
    room: ClassroomItem,
    isAvailable: Boolean,
    selectedPeriods: Set<Int>,
    units: List<ScheduleUnit>,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val topRadius = if (isFirst) CardDefaults.CornerRadius else 0.dp
    val bottomRadius = if (isLast) CardDefaults.CornerRadius else 0.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClassroomBrowserDefaults.HorizontalPadding)
            .squircleSurface(
                color = MiuixTheme.colorScheme.surfaceContainer,
                topStart = topRadius,
                topEnd = topRadius,
                bottomEnd = bottomRadius,
                bottomStart = bottomRadius,
            ),
    ) {
        if (!isFirst) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = ClassroomBrowserDefaults.RowHorizontalPadding),
            )
        }
        ClassroomRow(
            room = room,
            isAvailable = isAvailable,
            selectedPeriods = selectedPeriods,
            onClick = onClick,
            units = units,
        )
    }
}

@Composable
internal fun ClassroomRow(
    room: ClassroomItem,
    isAvailable: Boolean,
    selectedPeriods: Set<Int>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    units: List<ScheduleUnit> = DefaultScheduleUnits,
) {
    val effectiveUnits = units.ifEmpty { DefaultScheduleUnits }
    val freeCount = room.freePeriodCount(effectiveUnits)
    val statusText = when {
        room.occupations.isEmpty() -> stringResource(R.string.classrooms_status_all_day_free)
        isAvailable -> stringResource(R.string.classrooms_status_free)
        else -> stringResource(R.string.classrooms_status_occupied)
    }
    val freeSummaryText = stringResource(
        R.string.classrooms_free_periods_summary,
        freeCount,
        effectiveUnits.size,
    )
    val subtitle = buildList {
        if (room.campusName.isNotBlank()) add(room.campusName)
        if (room.displayCode.isNotEmpty()) add(room.displayCode)
        add(freeSummaryText)
    }.joinToString(" · ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = ClassroomBrowserDefaults.RowHorizontalPadding,
                vertical = ClassroomBrowserDefaults.RowVerticalPadding,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = room.name,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .squircleBackground(
                        color = ClassroomBrowserDefaults.statusBadgeContainerColor(isAvailable),
                        cornerRadius = ClassroomBrowserDefaults.BadgeCornerRadius,
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = statusText,
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Medium,
                    color = ClassroomBrowserDefaults.statusBadgeContentColor(isAvailable),
                    maxLines = 1,
                )
            }
        }
        Text(
            text = subtitle,
            modifier = Modifier.padding(top = 3.dp),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ClassroomPeriodStrip(
            room = room,
            modifier = Modifier.padding(top = 8.dp),
            selectedPeriods = selectedPeriods,
            units = effectiveUnits,
            includeSemantics = false,
        )
    }
}

/** 按目录作息画出全天各节的占用条，不同时段之间用圆点隔开。 */
@Composable
internal fun ClassroomPeriodStrip(
    room: ClassroomItem,
    modifier: Modifier = Modifier,
    selectedPeriods: Set<Int> = emptySet(),
    units: List<ScheduleUnit> = DefaultScheduleUnits,
    includeSemantics: Boolean = true,
) {
    val effectiveUnits = units.ifEmpty { DefaultScheduleUnits }
    val positionRanges = remember(effectiveUnits) { unitPositionRanges(effectiveUnits) }
    val occupiedColor = ClassroomBrowserDefaults.occupiedPeriodColor()
    val targetedFreeColor = ClassroomBrowserDefaults.targetedFreePeriodColor()
    val freeColor = PeriodStripDefaults.emptyColor()
    val description = if (includeSemantics) {
        stringResource(
            R.string.classrooms_free_periods_summary,
            room.freePeriodCount(effectiveUnits),
            effectiveUnits.size,
        )
    } else {
        null
    }

    PeriodStrip(
        groups = positionRanges,
        periodColor = { unitPosition ->
            val unit = effectiveUnits[unitPosition]
            when {
                !room.isFreeAt(unit) -> occupiedColor
                unit.index in selectedPeriods -> targetedFreeColor
                else -> freeColor
            }
        },
        modifier = if (description != null) {
            modifier.semantics(mergeDescendants = true) { stateDescription = description }
        } else {
            modifier
        },
    )
}

/**
 * 筛选弹层与详情弹层共用的节次网格：左边一列时段标签，右边每行排 4 节，不足 4 节时补空白对齐列宽。
 */
@Composable
internal fun ScheduleUnitGrid(
    dayPartGroups: List<ScheduleDayPartGroup>,
    dayPartLabel: @Composable (ScheduleDayPartGroup) -> Unit,
    modifier: Modifier = Modifier,
    unitCell: @Composable RowScope.(ScheduleUnit) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.PeriodGridRowSpacing),
    ) {
        dayPartGroups.forEach { dayPartGroup ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.PeriodGridRowSpacing),
            ) {
                dayPartLabel(dayPartGroup)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ClassroomBrowserDefaults.PeriodGridRowSpacing),
                ) {
                    dayPartGroup.units.chunked(ClassroomBrowserDefaults.PeriodsPerGridRow).forEach { rowUnits ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                ClassroomBrowserDefaults.PeriodGridColumnSpacing,
                            ),
                        ) {
                            rowUnits.forEach { unit -> unitCell(unit) }
                            repeat(ClassroomBrowserDefaults.PeriodsPerGridRow - rowUnits.size) {
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
private fun ClassroomSummaryRow(
    totalCount: Int,
    onlyAvailable: Boolean,
    lastUpdatedAt: Instant?,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberClassTimeFormatter()
    val summaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val countText = if (onlyAvailable) {
        stringResource(R.string.classrooms_result_available_count, totalCount)
    } else {
        stringResource(R.string.classrooms_result_total_count, totalCount)
    }
    val updatedText = lastUpdatedAt?.let { instant ->
        val localTime = instant.toLocalDateTime(CampusZone).time.toJavaLocalTime()
        stringResource(R.string.classrooms_updated_at, formatter.format(localTime))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = countText,
            style = MiuixTheme.textStyles.footnote1,
            color = summaryColor,
        )
        if (updatedText != null) {
            Text(
                text = updatedText,
                style = MiuixTheme.textStyles.footnote1,
                color = summaryColor,
            )
        }
    }
}

private const val ProgressAnimMillis = 220
private val StatusHorizontalPadding = 24.dp
private val StatusVerticalPadding = 56.dp
private val StatusIndicatorBottomSpacing = 14.dp

@Composable
internal fun ClassroomLoadingIndicator(
    loadProgress: ClassroomLoadProgress?,
    summaryColor: Color,
    modifier: Modifier = Modifier,
) {
    val targetFraction = loadProgress?.fraction
    val percentage = loadProgress?.percentage
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction ?: 0f,
        animationSpec = tween(durationMillis = ProgressAnimMillis),
        label = "ClassroomLoadProgress",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (targetFraction != null) {
            CircularProgressIndicator(progress = { animatedFraction })
        } else {
            CircularProgressIndicator(progress = null)
        }
        Spacer(modifier = Modifier.height(StatusIndicatorBottomSpacing))
        Text(
            text = if (percentage != null) {
                stringResource(R.string.classrooms_loading_percent, percentage)
            } else {
                stringResource(R.string.classrooms_loading)
            },
            style = MiuixTheme.textStyles.body2,
            color = summaryColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ClassroomStatusView(
    status: ClassroomBrowserStatus,
    loadProgress: ClassroomLoadProgress?,
    hasActiveFilter: Boolean,
    onResetFilter: () -> Unit,
    onRetry: () -> Unit,
) {
    if (status == ClassroomBrowserStatus.Ready) return
    val summaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = StatusHorizontalPadding, vertical = StatusVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (status) {
            ClassroomBrowserStatus.Loading -> {
                ClassroomLoadingIndicator(
                    loadProgress = loadProgress,
                    summaryColor = summaryColor,
                )
            }

            ClassroomBrowserStatus.Failed -> {
                Text(
                    text = stringResource(R.string.classrooms_load_failed),
                    style = MiuixTheme.textStyles.body1,
                    color = summaryColor,
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    text = stringResource(R.string.timetable_retry),
                    onClick = onRetry,
                    modifier = Modifier.padding(top = ClassroomBrowserDefaults.SectionSpacing),
                )
            }

            ClassroomBrowserStatus.Empty -> {
                Text(
                    text = stringResource(
                        if (hasActiveFilter) R.string.classrooms_empty_filtered else R.string.classrooms_empty,
                    ),
                    style = MiuixTheme.textStyles.body1,
                    color = summaryColor,
                    textAlign = TextAlign.Center,
                )
                if (hasActiveFilter) {
                    TextButton(
                        text = stringResource(R.string.classrooms_filter_reset),
                        onClick = onResetFilter,
                        modifier = Modifier.padding(top = ClassroomBrowserDefaults.SectionSpacing),
                    )
                }
            }

            ClassroomBrowserStatus.Ready -> Unit
        }
    }
}

@get:StringRes
internal val ScheduleDayPart.labelRes: Int
    get() = when (this) {
        ScheduleDayPart.Morning -> R.string.classrooms_daypart_morning
        ScheduleDayPart.Noon -> R.string.classrooms_daypart_noon
        ScheduleDayPart.Afternoon -> R.string.classrooms_daypart_afternoon
        ScheduleDayPart.Evening -> R.string.classrooms_daypart_evening
    }

internal object ClassroomBrowserDefaults {
    val HorizontalPadding: Dp = CardSpacing
    val SectionSpacing: Dp = CardSpacing
    val RowHorizontalPadding: Dp = BasicComponentDefaults.InsideMargin.calculateStartPadding(LayoutDirection.Ltr)
    val RowVerticalPadding: Dp = 12.dp

    /** 摘要行左右留白等于卡片外边距加行内边距（共 28 dp），与 [SmallTitleDefaults.InsideMargin] 对齐。 */
    val SummaryHorizontalPadding: Dp = HorizontalPadding + RowHorizontalPadding
    val SummaryTopSpacing: Dp = 10.dp

    /** 扣掉 [SmallTitle] 自带的 8 dp 顶距，让第一组教学楼标题距上方保持 12 dp。 */
    val FirstGroupHeaderTopPadding: Dp =
        (SectionSpacing - SmallTitleDefaults.InsideMargin.calculateTopPadding()).coerceAtLeast(0.dp)

    /** 弹层里的卡片贴着内容边缘，小标题左右对齐行内边距 [RowHorizontalPadding]。 */
    val SheetTitleMargin: PaddingValues = PaddingValues(
        horizontal = RowHorizontalPadding,
        vertical = SmallTitleDefaults.InsideMargin.calculateTopPadding(),
    )
    val SheetSectionSpacing: Dp = CardSpacing
    val SheetBottomSpacing: Dp = CardSpacing
    val SheetCardInsideMargin: PaddingValues = PaddingValues(CardSpacing)

    const val PeriodsPerGridRow: Int = 4
    val DayPartLabelWidth: Dp = 44.dp
    val PeriodGridRowSpacing: Dp = 8.dp
    val PeriodGridColumnSpacing: Dp = 6.dp
    val PeriodGridCellCornerRadius: Dp = 8.dp

    val ChipCornerRadius: Dp = 12.dp
    val ChipSpacing: Dp = 8.dp
    val ChipHorizontalPadding: Dp = 12.dp
    val ChipVerticalPadding: Dp = 7.dp
    val BadgeCornerRadius: Dp = 8.dp
    val ContentPadding: PaddingValues = PaddingValues(0.dp)

    private const val OccupiedAlpha = 0.85f
    private const val TargetedFreeAlpha = 0.35f

    @Composable
    @ReadOnlyComposable
    fun statusBadgeContainerColor(isAvailable: Boolean): Color =
        if (isAvailable) MiuixTheme.colorScheme.tertiaryContainer else MiuixTheme.colorScheme.secondaryContainer

    @Composable
    @ReadOnlyComposable
    fun statusBadgeContentColor(isAvailable: Boolean): Color =
        if (isAvailable) MiuixTheme.colorScheme.onTertiaryContainer else MiuixTheme.colorScheme.onSurfaceVariantSummary

    @Composable
    @ReadOnlyComposable
    fun chipContainerColor(selected: Boolean): Color =
        if (selected) MiuixTheme.colorScheme.tertiaryContainer else MiuixTheme.colorScheme.surfaceContainer

    @Composable
    @ReadOnlyComposable
    fun chipContentColor(selected: Boolean): Color =
        if (selected) MiuixTheme.colorScheme.onTertiaryContainer else MiuixTheme.colorScheme.onSurfaceContainer

    @Composable
    @ReadOnlyComposable
    fun occupiedPeriodColor(): Color = MiuixTheme.colorScheme.primary.copy(alpha = OccupiedAlpha)

    @Composable
    @ReadOnlyComposable
    fun targetedFreePeriodColor(): Color = MiuixTheme.colorScheme.primary.copy(alpha = TargetedFreeAlpha)
}

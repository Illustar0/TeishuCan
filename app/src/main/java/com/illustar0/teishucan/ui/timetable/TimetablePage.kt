package com.illustar0.teishucan.ui.timetable

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.ConflictChoice
import com.illustar0.teishucan.domain.ThemeSettings
import com.illustar0.teishucan.domain.TimetableEntry
import com.illustar0.teishucan.domain.TimetableTerm
import com.illustar0.teishucan.domain.TimetableWeek
import com.illustar0.teishucan.ui.component.DateSkeleton
import com.illustar0.teishucan.ui.component.DateWithYearSkeleton
import com.illustar0.teishucan.ui.component.EntryStack
import com.illustar0.teishucan.ui.component.RefreshContainer
import com.illustar0.teishucan.ui.component.Timetable
import com.illustar0.teishucan.ui.component.rememberLocalizedFormatter
import com.illustar0.teishucan.ui.component.liquid.LiquidGlassFloatingToolbar
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.CardSpacing
import com.illustar0.teishucan.ui.theme.TeishuCanTheme
import com.illustar0.teishucan.ui.theme.LocalIsBlurEnabled
import com.illustar0.teishucan.ui.theme.LocalIsLiquidGlassEnabled
import com.illustar0.teishucan.ui.theme.blurSource
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.springAnimateToPage

private val TimetablePadding = PaddingValues(start = 4.dp, end = 8.dp, bottom = 8.dp)

enum class TimetableStatus { Ready, NoLessons, Loading, Failed, NotFetched }

data class TimetableState(
    val today: LocalDate,
    val terms: List<TimetableTerm> = emptyList(),
    val term: TimetableTerm? = null,
    val status: TimetableStatus = TimetableStatus.NotFetched,
    val isRefreshing: Boolean = false,
    val conflictChoices: List<ConflictChoice> = emptyList(),
)

@Composable
fun TimetablePage(
    state: TimetableState,
    onRefresh: () -> Unit,
    onTermSelect: (TimetableTerm) -> Unit,
    onPutInFront: (conflict: List<TimetableEntry>, front: TimetableEntry) -> Unit,
    bottomPadding: Dp,
) {
    val today = state.today
    val scrollBehavior = MiuixScrollBehavior()
    val isBarBlurEnabled = LocalIsBlurEnabled.current
    val isLiquidGlass = LocalIsLiquidGlassEnabled.current
    // 液态玻璃的药丸也采样这一层，但顶栏只看模糊开关。
    val backdrop = rememberBlurBackdrop(isBarBlurEnabled || isLiquidGlass)
    val term = state.term
    val weeks = term?.weeks.orEmpty()
    // 未加载的学期也有周次，但只有日期，不能据此判断能否展示课表。
    val readyTerm = term.takeIf { state.status == TimetableStatus.Ready }
    // 每个学期打开自己的默认周，而不是沿用上个学期的页码。
    val pagerState = key(term?.id) {
        rememberPagerState(
            initialPage = weeks.indexOfFirst { today in it.dates }.coerceAtLeast(0),
            pageCount = { weeks.size },
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var weekSwitcherHeight by remember { mutableStateOf(0.dp) }
    var openedStack by remember { mutableStateOf<EntryStack?>(null) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop.takeIf { isBarBlurEnabled }) { containerColor ->
                TopAppBar(
                    title = stringResource(R.string.destination_timetable),
                    color = containerColor,
                    subtitle = term?.let { termName(it) }.orEmpty(),
                    actions = {
                        if (term != null) {
                            TermMenu(
                                terms = state.terms,
                                selectedTerm = term,
                                onTermSelect = onTermSelect,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()
        val weekSwitcherClearance = if (readyTerm == null) {
            0.dp
        } else {
            weekSwitcherHeight - FloatingToolbarDefaults.OutSidePadding.calculateBottomPadding()
        }
        // 周切换条属于被下拉的内容，刷新指示器才会出现在它上方。
        RefreshContainer(
            // 切换到未加载的学期不算刷新，但同样由这个指示器表示加载中。
            isRefreshing = state.isRefreshing || state.status == TimetableStatus.Loading,
            onRefresh = onRefresh,
            scrollBehavior = scrollBehavior,
            contentPadding = PaddingValues(top = topPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .blurSource(backdrop)
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = topPadding + weekSwitcherClearance,
                        bottom = bottomPadding + CardSpacing,
                    ),
                    overscrollEffect = null,
                ) {
                    item(key = "timetable") {
                        if (readyTerm == null) {
                            TimetableStatusMessage(
                                status = state.status,
                                onRefresh = onRefresh,
                                modifier = Modifier.fillParentMaxHeight(),
                            )
                        } else {
                            Card(
                                modifier = Modifier.padding(start = CardSpacing, end = CardSpacing, top = CardSpacing),
                            ) {
                                WeekPager(
                                    term = readyTerm,
                                    pagerState = pagerState,
                                    conflictChoices = state.conflictChoices,
                                    onEntriesClick = { stack -> openedStack = stack },
                                    today = today,
                                )
                            }
                        }
                    }
                }
                // 不放进 Scaffold 的 floatingToolbar 槽位，它会在顶栏下方再加一次状态栏内边距。
                if (readyTerm != null) {
                    WeekSwitcher(
                        weeks = weeks,
                        selectedWeek = weeks[pagerState.targetPage],
                        currentWeek = currentWeek(weeks, today),
                        onWeekSelect = { week ->
                            coroutineScope.launch { pagerState.springAnimateToPage(weeks.indexOf(week)) }
                        },
                        backdrop = backdrop,
                        modifier = Modifier
                            .padding(top = topPadding)
                            .onSizeChanged { size -> weekSwitcherHeight = with(density) { size.height.toDp() } },
                    )
                }
            }
        }
        EntryDetailSheet(
            stack = openedStack,
            conflictChoices = state.conflictChoices,
            onPutInFront = onPutInFront,
            onDismissRequest = { openedStack = null },
            units = term?.units.orEmpty(),
        )
    }
}

@Composable
private fun TimetableStatusMessage(
    status: TimetableStatus,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        status.messageRes?.let { message ->
            Text(
                text = stringResource(message),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
        status.actionRes?.let { action ->
            TextButton(
                text = stringResource(action),
                onClick = onRefresh,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** 展示 [pagerState] 所在的那一周。这里禁止滑动，课表上的横向拖动才能交给顶层分页器。 */
@Composable
private fun WeekPager(
    term: TimetableTerm,
    pagerState: PagerState,
    conflictChoices: List<ConflictChoice>,
    onEntriesClick: (EntryStack) -> Unit,
    today: LocalDate,
) {
    // 整个学期共用一个高度，翻周时网格不会伸缩。
    val periodCount = remember(term) { term.periodCount }
    HorizontalPager(
        state = pagerState,
        verticalAlignment = Alignment.Top,
        userScrollEnabled = false,
    ) { page ->
        val week = term.weeks[page]
        Timetable(
            week = week,
            today = today,
            periodCount = periodCount,
            conflictChoices = conflictChoices,
            onEntriesClick = onEntriesClick,
            modifier = Modifier.padding(TimetablePadding),
            units = term.units,
        )
    }
}

@Composable
private fun termName(term: TimetableTerm): String =
    stringResource(R.string.timetable_term_name, term.schoolYear, term.number)

@Composable
private fun TermMenu(
    terms: List<TimetableTerm>,
    selectedTerm: TimetableTerm,
    onTermSelect: (TimetableTerm) -> Unit,
) {
    val dateFormatter = rememberLocalizedFormatter(DateWithYearSkeleton)
    OverlayIconDropdownMenu(
        entry = DropdownEntry(
            items = terms.sortedByDescending { it.startDate }.map { term ->
                DropdownItem(
                    text = termName(term),
                    selected = term == selectedTerm,
                    onClick = { onTermSelect(term) },
                    summary = formatDateRange(term.startDate, term.endDate, dateFormatter),
                )
            },
        ),
    ) {
        Icon(
            imageVector = MiuixIcons.Filter,
            contentDescription = stringResource(R.string.timetable_term),
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun WeekSwitcher(
    weeks: List<TimetableWeek>,
    selectedWeek: TimetableWeek,
    currentWeek: TimetableWeek?,
    onWeekSelect: (TimetableWeek) -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = weeks.indexOf(selectedWeek)
    val dateFormatter = rememberLocalizedFormatter(DateSkeleton)
    LiquidGlassFloatingToolbar(backdrop = backdrop, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeekStepButton(
                icon = MiuixIcons.ChevronBackward,
                contentDescription = stringResource(R.string.timetable_previous_week),
                targetWeek = weeks.getOrNull(selectedIndex - 1),
                onWeekSelect = onWeekSelect,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        enabled = currentWeek != null && currentWeek != selectedWeek,
                        onClickLabel = stringResource(R.string.timetable_back_to_current_week),
                        onClick = { currentWeek?.let(onWeekSelect) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.timetable_week_number, selectedWeek.number),
                    style = MiuixTheme.textStyles.headline1,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                )
                Text(
                    text = formatDateRange(selectedWeek.dates.first(), selectedWeek.dates.last(), dateFormatter),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            WeekStepButton(
                icon = MiuixIcons.ChevronForward,
                contentDescription = stringResource(R.string.timetable_next_week),
                targetWeek = weeks.getOrNull(selectedIndex + 1),
                onWeekSelect = onWeekSelect,
            )
        }
    }
}

@Composable
private fun WeekStepButton(
    icon: ImageVector,
    contentDescription: String,
    targetWeek: TimetableWeek?,
    onWeekSelect: (TimetableWeek) -> Unit,
) {
    IconButton(
        onClick = { targetWeek?.let(onWeekSelect) },
        enabled = targetWeek != null,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (targetWeek != null) {
                MiuixTheme.colorScheme.onSurfaceContainer
            } else {
                MiuixTheme.colorScheme.disabledOnSecondaryVariant
            },
        )
    }
}

private fun formatDateRange(start: LocalDate, end: LocalDate, formatter: DateTimeFormatter): String =
    "${formatter.format(start.toJavaLocalDate())} – ${formatter.format(end.toJavaLocalDate())}"

@get:StringRes
private val TimetableStatus.messageRes: Int?
    get() = when (this) {
        TimetableStatus.Ready -> null
        TimetableStatus.NoLessons -> R.string.timetable_no_lessons
        TimetableStatus.Loading -> R.string.timetable_loading
        TimetableStatus.Failed -> R.string.timetable_load_failed
        TimetableStatus.NotFetched -> R.string.timetable_not_fetched
    }

@get:StringRes
private val TimetableStatus.actionRes: Int?
    get() = when (this) {
        TimetableStatus.Failed -> R.string.timetable_retry
        TimetableStatus.NotFetched -> R.string.timetable_fetch
        TimetableStatus.Ready, TimetableStatus.NoLessons, TimetableStatus.Loading -> null
    }

private fun currentWeek(weeks: List<TimetableWeek>, today: LocalDate): TimetableWeek? =
    weeks.find { today in it.dates }

private fun previewEntry(date: LocalDate, startPeriod: Int, title: String, location: String?, periodSpan: Int = 1) =
    TimetableEntry(
        // 预览里每门课的标题都不同，正好用来区分教学班。
        classId = title.hashCode().toLong(),
        day = date.dayOfWeek,
        date = date,
        startPeriod = startPeriod,
        startTime = LocalTime.parse("08:00"),
        endTime = LocalTime.parse("09:40"),
        title = title,
        teacher = "Sample Teacher",
        location = location,
        periodSpan = periodSpan,
    )

private fun previewTerm(id: String, number: String, firstMonday: LocalDate): TimetableTerm {
    val weeks = List(8) { index ->
        val monday = firstMonday.plus(index, DateTimeUnit.WEEK)
        val dates = List(7) { monday.plus(it, DateTimeUnit.DAY) }
        TimetableWeek(
            number = index + 1,
            dates = dates,
            entries = listOf(
                previewEntry(dates[0], startPeriod = 1, title = "Sample A", location = "R101", periodSpan = 2),
                previewEntry(
                    dates[1],
                    startPeriod = 3,
                    title = "Conflict A",
                    location = "R201",
                    periodSpan = 2,
                ),
                previewEntry(
                    dates[1],
                    startPeriod = 3,
                    title = "Conflict B",
                    location = "R202",
                    periodSpan = 2,
                ),
                previewEntry(dates[2], startPeriod = 1, title = "Lab", location = "B402", periodSpan = 4),
                previewEntry(
                    dates[2],
                    startPeriod = 3,
                    title = "Elective",
                    location = "R105",
                    periodSpan = 2,
                ),
                previewEntry(dates[3], startPeriod = 5, title = "Dense 1", location = "R301", periodSpan = 2),
                previewEntry(dates[3], startPeriod = 5, title = "Dense 2", location = "R302", periodSpan = 2),
                previewEntry(dates[3], startPeriod = 5, title = "Dense 3", location = "R303", periodSpan = 2),
                previewEntry(dates[4], startPeriod = 6, title = "Sample C", location = null, periodSpan = 3),
            ),
        )
    }
    return TimetableTerm(
        id = id,
        schoolYear = "2026-2027",
        number = number,
        startDate = weeks.first().dates.first(),
        endDate = weeks.last().dates.last(),
        weeks = weeks,
        isLoaded = true,
    )
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun TimetablePagePreview() {
    TeishuCanTheme(settings = ThemeSettings(isBlurEnabled = false), isDark = false) {
        val currentTerm = previewTerm("t3", "1", LocalDate(2026, 10, 5))
        TimetablePage(
            state = TimetableState(
                today = LocalDate(2026, 10, 30),
                terms = listOf(currentTerm, previewTerm("t4", "2", LocalDate(2026, 12, 7))),
                term = currentTerm,
                status = TimetableStatus.Ready,
            ),
            onRefresh = {},
            onTermSelect = {},
            onPutInFront = { _, _ -> },
            bottomPadding = 0.dp,
        )
    }
}

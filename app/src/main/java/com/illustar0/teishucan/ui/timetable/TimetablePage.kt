package com.illustar0.teishucan.ui.timetable

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.CampusZone
import com.illustar0.teishucan.domain.ThemeSettings
import com.illustar0.teishucan.domain.TimetableEntry
import com.illustar0.teishucan.domain.TimetableTerm
import com.illustar0.teishucan.domain.TimetableWeek
import com.illustar0.teishucan.ui.component.RefreshContainer
import com.illustar0.teishucan.ui.component.Timetable
import com.illustar0.teishucan.ui.component.liquid.LiquidGlassFloatingToolbar
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.TeishuCanTheme
import com.illustar0.teishucan.ui.theme.LocalIsBlurEnabled
import com.illustar0.teishucan.ui.theme.LocalIsLiquidGlassEnabled
import com.illustar0.teishucan.ui.theme.blurSource
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
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
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.springAnimateToPage

/** [TimetableState.term] 的课程处于什么情况。 */
enum class TimetableStatus { Ready, NoLessons, Loading, Failed, NotFetched }

/** 课表页展示的内容；[term] 是从 [terms] 中选出要展示的学期，还没有学期时为 null。 */
data class TimetableState(
    val terms: List<TimetableTerm> = emptyList(),
    val term: TimetableTerm? = null,
    val status: TimetableStatus = TimetableStatus.NotFetched,
    val isRefreshing: Boolean = false,
)

/** 每次展示 [TimetableState.term] 的一周，打开时停在 [today] 所在的周。 */
@Composable
fun TimetablePage(
    state: TimetableState,
    onRefresh: () -> Unit,
    onTermSelect: (TimetableTerm) -> Unit,
    bottomPadding: Dp,
    today: LocalDate = LocalDate.now(CampusZone),
) {
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
    // 关闭后保留，让弹层滑走时内容不变。
    var detailEntry by remember { mutableStateOf<TimetableEntry?>(null) }
    var isDetailVisible by remember { mutableStateOf(false) }

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
                        bottom = bottomPadding + 12.dp,
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
                            Card(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                                WeekPager(
                                    term = readyTerm,
                                    pagerState = pagerState,
                                    onEntryClick = { entry ->
                                        detailEntry = entry
                                        isDetailVisible = true
                                    },
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
            entry = detailEntry,
            show = isDetailVisible,
            onDismissRequest = { isDetailVisible = false },
        )
    }
}

/** 没有课程可展示时代替课表，说明 [status] 并在需要时提供调用 [onRefresh] 的按钮。 */
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
    onEntryClick: (TimetableEntry) -> Unit,
    today: LocalDate,
) {
    HorizontalPager(
        state = pagerState,
        verticalAlignment = Alignment.Top,
        userScrollEnabled = false,
    ) { page ->
        val week = term.weeks[page]
        Timetable(
            week = week,
            today = today,
            onEntryClick = onEntryClick,
            modifier = Modifier.padding(TimetablePadding),
        )
    }
}

@Composable
private fun EntryDetailSheet(
    entry: TimetableEntry?,
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = entry?.title,
        onDismissRequest = onDismissRequest,
    ) {
        if (entry == null) return@OverlayBottomSheet
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
        ) {
            LongPressCopyRow(
                title = stringResource(R.string.timetable_detail_time),
                value = entryScheduleText(entry),
            )
            if (entry.location != null) {
                CopyButtonRow(
                    title = stringResource(R.string.timetable_detail_location),
                    value = entry.location,
                )
            }
            if (entry.teacher.isNotBlank()) {
                LongPressCopyRow(
                    title = stringResource(R.string.timetable_detail_teacher),
                    value = entry.teacher,
                )
            }
        }
    }
}

/** 把 [value] 复制到剪贴板，然后调用 [onCopy]。 */
@Composable
private fun copyAction(
    title: String,
    value: String,
    onCopy: () -> Unit = {},
): () -> Unit {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    return {
        coroutineScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(title, value)))
            onCopy()
        }
    }
}

/** 长按时运行 [copy]，同时给出标准的长按触感。 */
@Composable
private fun Modifier.longPressToCopy(copy: () -> Unit): Modifier = combinedClickable(
    onLongClickLabel = stringResource(R.string.timetable_detail_copy),
    onLongClick = copy,
    onClick = {},
)

@Composable
private fun LongPressCopyRow(title: String, value: String) {
    BasicComponent(
        modifier = Modifier.longPressToCopy(copyAction(title, value)),
        title = title,
        summary = value,
    )
}

@Composable
private fun CopyButtonRow(title: String, value: String) {
    val hapticFeedback = LocalHapticFeedback.current
    var isCopied by remember(value) { mutableStateOf(false) }
    val copy = copyAction(title, value, onCopy = { isCopied = true })

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(CopiedIndicatorMillis)
            isCopied = false
        }
    }

    BasicComponent(
        modifier = Modifier.longPressToCopy(copy),
        title = title,
        summary = value,
        endActions = {
            IconButton(
                onClick = {
                    copy()
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                },
            ) {
                Crossfade(targetState = isCopied) { showsCheck ->
                    Icon(
                        imageVector = if (showsCheck) MiuixIcons.Ok else MiuixIcons.Copy,
                        contentDescription = stringResource(R.string.timetable_detail_copy),
                        tint = if (showsCheck) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantActions
                        },
                    )
                }
            }
        },
    )
}

/** [entry] 的星期与节次，后接具体时刻。 */
@Composable
private fun entryScheduleText(entry: TimetableEntry): String {
    val dayName = entry.day.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])
    val position = if (entry.periodSpan > 1) {
        val lastPeriod = entry.startPeriod + entry.periodSpan - 1
        stringResource(R.string.timetable_entry_period_range, dayName, entry.startPeriod, lastPeriod)
    } else {
        stringResource(R.string.timetable_entry_position, dayName, entry.startPeriod)
    }
    val formatter = DateTimeFormatter.ofPattern(ClassTimePattern)
    return stringResource(
        R.string.timetable_entry_schedule,
        position,
        entry.startTime.format(formatter),
        entry.endTime.format(formatter),
    )
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
    OverlayIconDropdownMenu(
        entry = DropdownEntry(
            items = terms.sortedByDescending { it.startDate }.map { term ->
                DropdownItem(
                    text = termName(term),
                    selected = term == selectedTerm,
                    onClick = { onTermSelect(term) },
                    summary = formatDateRange(term.startDate, term.endDate, TermDatePattern),
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
                    text = formatDateRange(selectedWeek.dates.first(), selectedWeek.dates.last(), WeekDatePattern),
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

private const val TermDatePattern = "yyyy/M/d"
private const val WeekDatePattern = "M/d"
private const val ClassTimePattern = "H:mm"
private const val CopiedIndicatorMillis = 2000L

private val TimetablePadding = PaddingValues(start = 4.dp, end = 8.dp, bottom = 8.dp)

private fun formatDateRange(start: LocalDate, end: LocalDate, pattern: String): String {
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return "${start.format(formatter)} – ${end.format(formatter)}"
}

/** 代替课表显示的说明，课程正常展示时没有。 */
@get:StringRes
private val TimetableStatus.messageRes: Int?
    get() = when (this) {
        TimetableStatus.Ready -> null
        TimetableStatus.NoLessons -> R.string.timetable_no_lessons
        TimetableStatus.Loading -> R.string.timetable_loading
        TimetableStatus.Failed -> R.string.timetable_load_failed
        TimetableStatus.NotFetched -> R.string.timetable_not_fetched
    }

/** 说明下方刷新按钮的文字，刷新解决不了问题或已在进行时没有。 */
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
        val monday = firstMonday.plusWeeks(index.toLong())
        TimetableWeek(
            number = index + 1,
            dates = List(7) { monday.plusDays(it.toLong()) },
            entries = listOf(
                previewEntry(monday, startPeriod = 1, title = "Sample A", location = "R101", periodSpan = 2),
                previewEntry(monday.plusDays(2), startPeriod = 3, title = "Sample B", location = "R202"),
                previewEntry(monday.plusDays(4), startPeriod = 6, title = "Sample C", location = null, periodSpan = 3),
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
        val currentTerm = previewTerm("t3", "1", LocalDate.of(2026, 10, 5))
        TimetablePage(
            state = TimetableState(
                terms = listOf(currentTerm, previewTerm("t4", "2", LocalDate.of(2026, 12, 7))),
                term = currentTerm,
                status = TimetableStatus.Ready,
            ),
            onRefresh = {},
            onTermSelect = {},
            bottomPadding = 0.dp,
            today = LocalDate.of(2026, 10, 30),
        )
    }
}

package com.illustar0.teishucan.ui.timetable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.ConflictChoice
import com.illustar0.teishucan.domain.DefaultScheduleUnits
import com.illustar0.teishucan.domain.ScheduleUnit
import com.illustar0.teishucan.domain.TimetableEntry
import com.illustar0.teishucan.domain.frontIn
import com.illustar0.teishucan.domain.labelOf
import com.illustar0.teishucan.ui.component.CopyableDetailRow
import com.illustar0.teishucan.ui.component.DetailRow
import com.illustar0.teishucan.ui.component.EntryStack
import com.illustar0.teishucan.ui.component.animation.CalmMotion
import com.illustar0.teishucan.ui.component.animation.rememberFrameTransition
import com.illustar0.teishucan.ui.component.localizedDayName
import com.illustar0.teishucan.ui.component.rememberClassTimeFormatter
import com.illustar0.teishucan.ui.component.rememberShownContent
import java.time.format.TextStyle
import kotlinx.datetime.toJavaLocalTime
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.squircle.isSquircleEnabled
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 「置顶」标签的圆角与内边距。 */
private val FrontBadgeCornerRadius = 6.dp
private val FrontBadgePadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp)

/**
 * 课表上 [stack] 那一格的详情，[stack] 为 null 时关闭。
 *
 * 没有冲突时列出这门课的地点、时间和老师。有冲突时只展开一门，其余每门折叠成一行：
 * 起初展开点中的那门，点哪一行就改为展开哪门。
 * 叠在课表最上层的那门标为置顶。点别的课的图钉，会把那门课置顶；点亮着的图钉，会把置顶依次换给下一门课。
 * 选择由 [onPutInFront] 保存，课表随之改变叠放。
 */
@Composable
internal fun EntryDetailSheet(
    stack: EntryStack?,
    conflictChoices: List<ConflictChoice>,
    onPutInFront: (conflict: List<TimetableEntry>, front: TimetableEntry) -> Unit,
    onDismissRequest: () -> Unit,
    units: List<ScheduleUnit> = emptyList(),
) {
    // 关闭后保留最后一格，让弹层滑走时内容不变。
    val sheet = rememberShownContent(stack)
    val shown = sheet.value ?: return
    val effectiveUnits = units.ifEmpty { DefaultScheduleUnits }
    val title = if (shown.isConflict) {
        stringResource(R.string.timetable_conflict_sheet_title, shown.entries.size)
    } else {
        shown.entries.single().title
    }

    OverlayBottomSheet(
        show = stack != null,
        title = title,
        onDismissRequest = onDismissRequest,
        onDismissFinished = sheet.release,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            if (shown.isConflict) {
                ConflictDetail(
                    stack = shown,
                    conflictChoices = conflictChoices,
                    units = effectiveUnits,
                    onPutInFront = onPutInFront,
                )
            } else {
                DetailCard { EntryDetailRows(entry = shown.entries.single(), units = effectiveUnits) }
            }
        }
    }
}

@Composable
private fun ConflictDetail(
    stack: EntryStack,
    conflictChoices: List<ConflictChoice>,
    units: List<ScheduleUnit>,
    onPutInFront: (conflict: List<TimetableEntry>, front: TimetableEntry) -> Unit,
) {
    // 刚点的置顶先在这里生效，不等存储写完，连点图钉时才能接着轮转。
    var pinnedHere by remember(stack) { mutableStateOf<TimetableEntry?>(null) }
    val front = pinnedHere ?: conflictChoices.frontIn(stack.entries) ?: stack.defaultFront
    var focus by remember(stack) { mutableStateOf(ConflictFocus(current = stack.entries.first())) }
    // 展开的课一换，各块从此刻画着的样子变形过去，见 morphFrame；块里的新旧内容按时间交替，见 MorphContent。
    val morph = rememberFrameTransition<Any, BlockFrame>(trigger = focus, interpolate = ::morphFrame)
    val isMorphing by remember(morph) { derivedStateOf { morph.elapsed < 1f } }
    // 点亮着的图钉把置顶换给下一门课，点别的课的图钉把那门课置顶；展开的课随之换成新置顶的那门。
    val pin = { entry: TimetableEntry ->
        val newFront = if (entry == front) stack.entries.cyclicNext(entry) else entry
        pinnedHere = newFront
        onPutInFront(stack.entries, newFront)
        focus = focus.moveTo(newFront, frontBefore = front)
    }
    val surfaceColor = MiuixTheme.colorScheme.secondaryContainer
    val isSquircle = isSquircleEnabled()

    Layout(
        content = {
            SmallTitle(
                text = stringResource(R.string.timetable_conflict_others),
                modifier = Modifier.padding(top = 12.dp),
                // 弹层里的卡片贴着内容边缘，标题因此与卡片里的文字对齐，不用页面上的 28 dp 缩进。
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            )
            // 每门课一块底色、一份内容，始终按同一顺序列出；摆在哪里由布局决定。
            stack.entries.forEach { entry ->
                Box(Modifier.background(surfaceColor))
                val isExpanded = entry == focus.current
                val wasExpanded = entry == focus.previous
                val onFocus = { focus = focus.moveTo(entry, frontBefore = front) }
                MorphContent(
                    clock = { morph.elapsed },
                    previous = if (isMorphing && wasExpanded != isExpanded) {
                        {
                            val wasFront = entry == focus.previousFront
                            ConflictCourse(entry, units, wasExpanded, wasFront, onFocus, onPin = { pin(entry) })
                        }
                    } else {
                        null
                    },
                    current = {
                        ConflictCourse(entry, units, isExpanded, entry == front, onFocus, onPin = { pin(entry) })
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val natural = Constraints.fixedWidth(width)
        val title = measurables.first().measure(natural)
        val contents = stack.entries.indices.map { measurables[2 * it + 2].measure(natural) }
        val frames = morph.nextFrames(
            conflictBlockTargets(
                expandedIndex = stack.entries.indexOf(focus.current),
                courseHeights = contents.map { it.height },
                titleHeight = title.height,
                cornerPx = CardDefaults.CornerRadius.toPx(),
            ),
        )
        val surfaces = stack.entries.indices.map {
            measurables[2 * it + 1].measure(Constraints.fixed(width, frames.getValue(it).height))
        }
        layout(width, constraints.constrainHeight(frames.getValue(ConflictLayoutKey.Whole).height)) {
            title.place(0, frames.getValue(ConflictLayoutKey.OthersTitle).y)
            stack.entries.forEachIndexed { index, entry ->
                val frame = frames.getValue(index)
                val outline = BlockOutline(width.toFloat(), frame, isSquircle)
                val zIndex = focus.zIndexOf(entry)
                surfaces[index].placeWithLayer(0, frame.y, zIndex) {
                    shape = outline
                    clip = true
                }
                contents[index].placeWithLayer(0, frame.y, zIndex) {
                    shape = outline
                    clip = true
                }
            }
        }
    }
}

/** 正展开的课 [current]；[previous] 是在它之前展开的课，[previousFront] 是那时置顶的课（刚打开时二者为 null）。 */
private data class ConflictFocus(
    val current: TimetableEntry,
    val previous: TimetableEntry? = null,
    val previousFront: TimetableEntry? = null,
) {
    fun moveTo(entry: TimetableEntry, frontBefore: TimetableEntry): ConflictFocus =
        if (entry == current) this else ConflictFocus(current = entry, previous = current, previousFront = frontBefore)

    /** 两块交错而过时，缩回的那门盖住长出的那门，二者又都盖住其余各块。 */
    fun zIndexOf(entry: TimetableEntry): Float = when (entry) {
        previous -> 2f
        current -> 1f
        else -> 0f
    }
}

/** 冲突详情里不属于任何一门课的两项：「同时段的其他课程」标题，以及整个冲突区（只用它的高度）。 */
internal enum class ConflictLayoutKey { OthersTitle, Whole }

/** 一块的位置 [y]、高度 [height] 与上下两边的圆角，单位都是像素。 */
internal data class BlockFrame(val y: Int, val height: Int, val topCorner: Float = 0f, val bottomCorner: Float = 0f)

/**
 * 冲突详情从上到下的排法，各门课以各自在冲突里的序号为键。
 *
 * 展开的第 [expandedIndex] 门在最上面，其次是 [ConflictLayoutKey.OthersTitle]。其余的课按原顺序折叠成行，
 * 连成一张卡，只有首尾两行带圆角。[ConflictLayoutKey.Whole] 是整个冲突区，[courseHeights] 是各门课内容的高度。
 */
internal fun conflictBlockTargets(
    expandedIndex: Int,
    courseHeights: List<Int>,
    titleHeight: Int,
    cornerPx: Float,
): Map<Any, BlockFrame> {
    val blocks = HashMap<Any, BlockFrame>()
    var y = 0
    fun stack(key: Any, height: Int, topCorner: Float, bottomCorner: Float) {
        blocks[key] = BlockFrame(y = y, height = height, topCorner = topCorner, bottomCorner = bottomCorner)
        y += height
    }
    stack(expandedIndex, courseHeights[expandedIndex], cornerPx, cornerPx)
    stack(ConflictLayoutKey.OthersTitle, titleHeight, 0f, 0f)
    val rows = courseHeights.indices.filter { it != expandedIndex }
    rows.forEachIndexed { order, index ->
        stack(
            key = index,
            height = courseHeights[index],
            topCorner = if (order == 0) cornerPx else 0f,
            bottomCorner = if (order == rows.lastIndex) cornerPx else 0f,
        )
    }
    blocks[ConflictLayoutKey.Whole] = BlockFrame(y = 0, height = y)
    return blocks
}

/** 变形走到 [progress] 时一块的样子：位置、高度与圆角一起从 [start] 线性插值到 [target]。 */
internal fun morphFrame(start: BlockFrame, target: BlockFrame, progress: Float): BlockFrame = BlockFrame(
    y = lerp(start.y, target.y, progress),
    height = lerp(start.height, target.height, progress),
    topCorner = lerp(start.topCorner, target.topCorner, progress),
    bottomCorner = lerp(start.bottomCorner, target.bottomCorner, progress),
)

/**
 * 一块此刻的轮廓，底色和内容都按它裁切。轮廓宽 [width]，高取 [frame] 的高度，与内容自身的大小无关：
 * 变形途中内容仍按自己的高度排，超出这一块的部分裁掉不画。
 */
private class BlockOutline(
    private val width: Float,
    private val frame: BlockFrame,
    private val isSquircle: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(
            Path().apply {
                addSquircleRect(
                    width = width,
                    height = frame.height.toFloat(),
                    topLeft = frame.topCorner,
                    topRight = frame.topCorner,
                    bottomRight = frame.bottomCorner,
                    bottomLeft = frame.bottomCorner,
                    squircleEnabled = isSquircle,
                )
            },
        )
}

/** 旧内容在变形的前 20% 时间里淡出，新内容在 15%～45% 的时间里淡入，之后一直清晰。 */
private const val MorphFadeOutEnd = 0.2f
private const val MorphFadeInStart = 0.15f
private const val MorphFadeInEnd = 0.45f

private fun morphFadeOut(clock: Float): Float = (1f - clock / MorphFadeOutEnd).coerceIn(0f, 1f)

private fun morphFadeIn(clock: Float): Float =
    ((clock - MorphFadeInStart) / (MorphFadeInEnd - MorphFadeInStart)).coerceIn(0f, 1f)

/** 变形途中同时画出旧内容 [previous] 与新内容 [current]，按 [clock] 交替；大小以新内容为准。 */
@Composable
private fun MorphContent(
    clock: () -> Float,
    previous: (@Composable () -> Unit)?,
    current: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box(Modifier.graphicsLayer { alpha = if (previous == null) 1f else morphFadeIn(clock()) }) {
                current()
            }
            if (previous != null) {
                Box(Modifier.graphicsLayer { alpha = morphFadeOut(clock()) }) {
                    previous()
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val shown = placeables.first()
        // 新内容最后放置，盖在最上面，点按由它接收。
        layout(shown.width, shown.height) {
            placeables.asReversed().forEach { it.place(0, 0) }
        }
    }
}

@Composable
private fun ConflictCourse(
    entry: TimetableEntry,
    units: List<ScheduleUnit>,
    isExpanded: Boolean,
    isFront: Boolean,
    onFocus: () -> Unit,
    onPin: () -> Unit,
) {
    if (isExpanded) {
        ExpandedCourse(entry = entry, units = units, isFront = isFront, onPin = onPin)
    } else {
        CollapsedCourse(entry = entry, units = units, isFront = isFront, onFocus = onFocus, onPin = onPin)
    }
}

@Composable
private fun ExpandedCourse(
    entry: TimetableEntry,
    units: List<ScheduleUnit>,
    isFront: Boolean,
    onPin: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(endActions = { PinAction(entry = entry, isFront = isFront, onClick = onPin) }) {
            CourseTitle(entry = entry, isFront = isFront)
        }
        EntryDetailRows(entry = entry, units = units)
    }
}

@Composable
private fun CollapsedCourse(
    entry: TimetableEntry,
    units: List<ScheduleUnit>,
    isFront: Boolean,
    onFocus: () -> Unit,
    onPin: () -> Unit,
) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        onClick = onFocus,
        onClickLabel = stringResource(R.string.timetable_conflict_show),
        endActions = { PinAction(entry = entry, isFront = isFront, onClick = onPin) },
    ) {
        CourseTitle(entry = entry, isFront = isFront)
        Text(
            text = otherEntrySummary(entry, units),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 排在 [entry] 之后的那门课，最后一门之后回到第一门。 */
private fun List<TimetableEntry>.cyclicNext(entry: TimetableEntry): TimetableEntry = this[(indexOf(entry) + 1) % size]

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
        content()
    }
}

/** 样式与 BasicComponent 的标题相同。置顶的课在标题后加「置顶」标签，只换了置顶时标签淡入。 */
@Composable
private fun CourseTitle(entry: TimetableEntry, isFront: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = entry.title,
            modifier = Modifier.weight(1f, fill = false),
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
        )
        AnimatedVisibility(
            visible = isFront,
            enter = fadeIn(CalmMotion.spring()),
            exit = ExitTransition.None,
        ) {
            Text(
                text = stringResource(R.string.timetable_front_badge),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .squircleBackground(MiuixTheme.colorScheme.tertiaryContainer, FrontBadgeCornerRadius)
                    .padding(FrontBadgePadding),
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/**
 * 每门课的图钉都能点。置顶那门的图钉亮着，点它会把置顶换给下一门课（只有两门时就是互换）；
 * 点其余课的图钉，会把那门课置顶。
 */
@Composable
private fun PinAction(entry: TimetableEntry, isFront: Boolean, onClick: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    IconButton(
        onClick = {
            onClick()
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        },
    ) {
        Icon(
            imageVector = MiuixIcons.Pin,
            contentDescription = if (isFront) {
                stringResource(R.string.timetable_pass_front_on, entry.title)
            } else {
                stringResource(R.string.timetable_put_in_front, entry.title)
            },
            tint = if (isFront) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

/** 一门课的地点、时间和老师，没有地点或老师时省去那一行。 */
@Composable
private fun EntryDetailRows(entry: TimetableEntry, units: List<ScheduleUnit>) {
    if (entry.location != null) {
        CopyableDetailRow(
            icon = MiuixIcons.Location,
            label = stringResource(R.string.timetable_detail_location),
            title = entry.location,
        )
    }
    val formatter = rememberClassTimeFormatter()
    val startTime = formatter.format(entry.startTime.toJavaLocalTime())
    val endTime = formatter.format(entry.endTime.toJavaLocalTime())
    val position = entryPositionText(entry, units)
    DetailRow(
        icon = MiuixIcons.Recent,
        label = stringResource(R.string.timetable_detail_time),
        title = stringResource(R.string.timetable_time_range, startTime, endTime),
        summary = position,
        copyText = stringResource(R.string.timetable_entry_schedule, position, startTime, endTime),
    )
    if (entry.teacher.isNotBlank()) {
        DetailRow(
            icon = MiuixIcons.Contacts,
            label = stringResource(R.string.timetable_detail_teacher),
            title = entry.teacher,
        )
    }
}

/** 折叠的课程行下方的说明：地点与节次，没有地点时只有节次。 */
@Composable
@ReadOnlyComposable
private fun otherEntrySummary(entry: TimetableEntry, units: List<ScheduleUnit>): String {
    val startLabel = units.labelOf(entry.startPeriod)
    val lastLabel = units.labelOf(entry.lastPeriod)
    val periods = if (entry.periodSpan > 1) {
        stringResource(R.string.timetable_periods_range, startLabel, lastLabel)
    } else {
        stringResource(R.string.timetable_period, startLabel)
    }
    return entry.location?.let { stringResource(R.string.timetable_conflict_other_summary, it, periods) } ?: periods
}

/** 星期与节次，如「星期一第 5–6 节」或「星期二第 +1–+2 节」。 */
@Composable
@ReadOnlyComposable
private fun entryPositionText(entry: TimetableEntry, units: List<ScheduleUnit>): String {
    val dayName = localizedDayName(entry.day, TextStyle.FULL)
    val startLabel = units.labelOf(entry.startPeriod)
    val lastLabel = units.labelOf(entry.lastPeriod)
    return if (entry.periodSpan > 1) {
        stringResource(R.string.timetable_entry_period_range, dayName, startLabel, lastLabel)
    } else {
        stringResource(R.string.timetable_entry_position, dayName, startLabel)
    }
}

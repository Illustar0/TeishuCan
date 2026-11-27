package com.illustar0.teishucan.ui.classrooms

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.ClassroomActivityKind
import com.illustar0.teishucan.domain.ClassroomItem
import com.illustar0.teishucan.domain.DefaultScheduleUnits
import com.illustar0.teishucan.domain.MergedClassroomOccupation
import com.illustar0.teishucan.domain.ParsedOccupationActivity
import com.illustar0.teishucan.domain.ScheduleUnit
import com.illustar0.teishucan.domain.groupScheduleUnitsByDayPart
import com.illustar0.teishucan.domain.mergeClassroomOccupations
import com.illustar0.teishucan.ui.component.CopyableDetailRow
import com.illustar0.teishucan.ui.component.rememberClassTimeFormatter
import com.illustar0.teishucan.ui.component.rememberLocalizedFormatter
import com.illustar0.teishucan.ui.component.rememberShownContent
import kotlinx.datetime.toJavaLocalTime
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 教室当天的占用详情弹层，[room] 为 null 时关闭。 */
@Composable
internal fun ClassroomDetailSheet(
    room: ClassroomItem?,
    selectedPeriods: Set<Int>,
    onDismissRequest: () -> Unit,
    units: List<ScheduleUnit> = DefaultScheduleUnits,
) {
    val sheet = rememberShownContent(room)
    val displayed = sheet.value ?: return
    val effectiveUnits = units.ifEmpty { DefaultScheduleUnits }

    OverlayBottomSheet(
        show = room != null,
        title = displayed.name,
        onDismissRequest = onDismissRequest,
        onDismissFinished = sheet.release,
    ) {
        val locationText = listOf(displayed.campusName, displayed.buildingName)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" · ")
            .ifBlank { displayed.name }
        val mergedOccupations = remember(displayed.occupations) {
            mergeClassroomOccupations(displayed.occupations)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = ClassroomBrowserDefaults.SheetBottomSpacing),
        ) {
            Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                CopyableDetailRow(
                    icon = MiuixIcons.Location,
                    label = stringResource(R.string.classrooms_detail_location),
                    title = locationText,
                    summary = displayed.displayCode.takeIf(String::isNotEmpty),
                    copyLabel = displayed.name,
                    copyText = displayed.name,
                )
            }

            SmallTitle(
                text = stringResource(R.string.classrooms_detail_period_overview),
                modifier = Modifier.padding(top = ClassroomBrowserDefaults.SheetSectionSpacing),
                insideMargin = ClassroomBrowserDefaults.SheetTitleMargin,
            )
            Card(
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
                insideMargin = ClassroomBrowserDefaults.SheetCardInsideMargin,
            ) {
                ClassroomPeriodOverviewGrid(
                    room = displayed,
                    selectedPeriods = selectedPeriods,
                    units = effectiveUnits,
                )
            }

            SmallTitle(
                text = stringResource(R.string.classrooms_detail_occupations),
                modifier = Modifier.padding(top = ClassroomBrowserDefaults.SheetSectionSpacing),
                insideMargin = ClassroomBrowserDefaults.SheetTitleMargin,
            )
            Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                if (mergedOccupations.isEmpty()) {
                    BasicComponent(
                        title = stringResource(R.string.classrooms_status_all_day_free),
                        summary = stringResource(R.string.classrooms_detail_no_occupations),
                    )
                } else {
                    mergedOccupations.forEachIndexed { index, occupation ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = ClassroomBrowserDefaults.RowHorizontalPadding),
                            )
                        }
                        MergedOccupationRow(occupation = occupation)
                    }
                }
            }
        }
    }
}

private const val PeriodStartTimeSkeleton = "Hm"
private const val PeriodStartTimeAlpha = 0.72f
private val OverviewCellVerticalPadding = 6.dp

@Composable
private fun ClassroomPeriodOverviewGrid(
    room: ClassroomItem,
    selectedPeriods: Set<Int>,
    units: List<ScheduleUnit>,
) {
    val scheme = MiuixTheme.colorScheme
    val startTimeFormatter = rememberLocalizedFormatter(PeriodStartTimeSkeleton)
    val dayPartGroups = remember(units) { groupScheduleUnitsByDayPart(units) }

    ScheduleUnitGrid(
        dayPartGroups = dayPartGroups,
        dayPartLabel = { dayPartGroup ->
            Text(
                text = stringResource(dayPartGroup.dayPart.labelRes),
                modifier = Modifier.width(ClassroomBrowserDefaults.DayPartLabelWidth),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        },
    ) { unit ->
        val isOccupied = !room.isFreeAt(unit)
        val isTargeted = unit.index in selectedPeriods
        val container = when {
            isOccupied -> ClassroomBrowserDefaults.occupiedPeriodColor()
            isTargeted -> scheme.tertiaryContainer
            else -> scheme.surfaceContainer
        }
        val content = when {
            isOccupied -> scheme.onPrimary
            isTargeted -> scheme.onTertiaryContainer
            else -> scheme.onSurfaceContainer
        }
        val stateText = when {
            isOccupied -> stringResource(R.string.classrooms_detail_period_occupied)
            isTargeted -> stringResource(R.string.classrooms_detail_period_selected_free)
            else -> stringResource(R.string.classrooms_detail_period_free)
        }
        val periodLabel = stringResource(R.string.classrooms_period_short, unit.label)
        val formattedStart = startTimeFormatter.format(unit.startTime.toJavaLocalTime())

        Column(
            modifier = Modifier
                .weight(1f)
                .squircleBackground(
                    color = container,
                    cornerRadius = ClassroomBrowserDefaults.PeriodGridCellCornerRadius,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "$periodLabel $formattedStart"
                    stateDescription = stateText
                }
                .padding(vertical = OverviewCellVerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = unit.label,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
                color = content,
            )
            Text(
                text = formattedStart,
                style = MiuixTheme.textStyles.footnote2,
                color = content.copy(alpha = PeriodStartTimeAlpha),
                maxLines = 1,
            )
        }
    }
}

private const val TimeSlotJoinSeparator = "  /  "
private val KindBadgeCornerRadius = 5.dp

@Composable
private fun MergedOccupationRow(occupation: MergedClassroomOccupation) {
    val scheme = MiuixTheme.colorScheme
    val formatter = rememberClassTimeFormatter()
    val kindLabel = stringResource(occupation.activityKind.labelRes)
    val slotsText = occupation.slots
        .map { slot ->
            val start = formatter.format(slot.startTime.toJavaLocalTime())
            val end = formatter.format(slot.endTime.toJavaLocalTime())
            stringResource(R.string.timetable_time_range, start, end)
        }
        .joinToString(TimeSlotJoinSeparator)
    val metaText = formatOccupationMetadata(occupation.activity)
    var isTeachersExpanded by remember(occupation) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(BasicComponentDefaults.InsideMargin),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .squircleBackground(
                        color = scheme.tertiaryContainer,
                        cornerRadius = KindBadgeCornerRadius,
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    text = kindLabel,
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onTertiaryContainer,
                    maxLines = 1,
                )
            }
            Text(
                text = occupation.activity.title.ifBlank { kindLabel },
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceContainer,
            )
        }
        Text(
            text = slotsText,
            modifier = Modifier.padding(top = 4.dp),
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurfaceSecondary,
        )
        if (metaText.isNotEmpty()) {
            Text(
                text = metaText,
                modifier = Modifier.padding(top = 3.dp),
                style = MiuixTheme.textStyles.footnote1,
                color = scheme.onSurfaceVariantSummary,
            )
        }
        if (occupation.teachers.isNotEmpty()) {
            OccupationTeachersLine(
                teachers = occupation.teachers,
                isExpanded = isTeachersExpanded,
                onToggleExpand = { isTeachersExpanded = !isTeachersExpanded },
            )
        }
    }
}

@Composable
@ReadOnlyComposable
private fun formatOccupationMetadata(activity: ParsedOccupationActivity): String = when {
    activity.isExperimentCode && activity.code.isNotEmpty() -> {
        stringResource(R.string.classrooms_occupation_code, activity.code)
    }
    activity.department.isNotEmpty() && activity.code.isNotEmpty() -> {
        "${activity.department} · ${activity.code}"
    }
    activity.department.isNotEmpty() -> activity.department
    else -> activity.code
}

private const val MaxCollapsedTeachers = 3
private const val TeacherJoinSeparator = "、"
private val TeachersExpandCornerRadius = 6.dp

@Composable
private fun OccupationTeachersLine(
    teachers: List<String>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    val summaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    if (teachers.size <= MaxCollapsedTeachers) {
        Text(
            text = teachers.joinToString(TeacherJoinSeparator),
            modifier = Modifier.padding(top = 3.dp),
            style = MiuixTheme.textStyles.footnote1,
            color = summaryColor,
        )
        return
    }

    val currentOnToggle by rememberUpdatedState(onToggleExpand)
    val visibleNames = if (isExpanded) teachers else teachers.take(MaxCollapsedTeachers)
    val actionLabel = if (isExpanded) {
        stringResource(R.string.classrooms_teachers_collapse)
    } else {
        stringResource(R.string.classrooms_teachers_more, teachers.size)
    }
    val annotated = buildAnnotatedString {
        append(visibleNames.joinToString(TeacherJoinSeparator))
        append(' ')
        withStyle(SpanStyle(color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) {
            append(actionLabel)
        }
    }

    Text(
        text = annotated,
        modifier = Modifier
            .padding(top = 3.dp)
            .squircleClip(TeachersExpandCornerRadius)
            .clickable(
                role = Role.Button,
                onClick = { currentOnToggle() },
            ),
        style = MiuixTheme.textStyles.footnote1,
        color = summaryColor,
    )
}

@get:StringRes
private val ClassroomActivityKind.labelRes: Int
    get() = when (this) {
        ClassroomActivityKind.Lesson -> R.string.classrooms_activity_lesson
        ClassroomActivityKind.Experiment -> R.string.classrooms_activity_experiment
        ClassroomActivityKind.RoomBorrow -> R.string.classrooms_activity_borrow
        ClassroomActivityKind.Exam -> R.string.classrooms_activity_exam
        ClassroomActivityKind.ScheduleChange -> R.string.classrooms_activity_schedule_change
        ClassroomActivityKind.Other -> R.string.classrooms_activity_other
    }

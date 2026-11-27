package com.illustar0.teishucan.ui.services

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.CampusService
import com.illustar0.teishucan.ui.theme.CardSpacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ContactsBook
import top.yukonga.miuix.kmp.icon.extended.Email
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Years
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 服务目录中内置原生页面的跳转目标。 */
enum class NativeService {
    UndergradEas,
}

/** 服务条目的打开目标：外部单点登录网页或内置原生页面。 */
sealed interface ServiceTarget {
    data class Web(val service: CampusService) : ServiceTarget
    data class Native(val service: NativeService) : ServiceTarget
}

/** 服务页与本科教务子页上的分组，按声明顺序排列。[isUndergradEas] 的组收在本科教务子页里。 */
internal enum class ServiceGroup(@param:StringRes val titleRes: Int, val isUndergradEas: Boolean) {
    Academic(R.string.services_academic, isUndergradEas = false),
    Library(R.string.services_library, isUndergradEas = false),
    StudentAffairs(R.string.services_student_affairs, isUndergradEas = false),
    EasRecords(R.string.services_eas_records, isUndergradEas = true),
    EasExamsAndGrades(R.string.services_eas_exams_and_grades, isUndergradEas = true),
    EasSchedule(R.string.services_eas_schedule, isUndergradEas = true),
    ;

    /** 组内的服务条目，按 [ServiceEntry] 的声明顺序排列。 */
    val entries: List<ServiceEntry> by lazy { ServiceEntry.entries.filter { it.group == this } }
}

/** 服务目录中的单个入口，按声明顺序排列。 */
internal enum class ServiceEntry(
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val group: ServiceGroup,
    val target: ServiceTarget,
) {
    UndergradEas(
        titleRes = R.string.service_undergrad_eas,
        summaryRes = R.string.service_undergrad_eas_summary,
        group = ServiceGroup.Academic,
        target = ServiceTarget.Native(NativeService.UndergradEas),
    ),
    Library(
        titleRes = R.string.service_library,
        summaryRes = R.string.service_library_summary,
        group = ServiceGroup.Library,
        target = ServiceTarget.Web(CampusService.Library),
    ),
    LibraryReservation(
        titleRes = R.string.service_library_reservation,
        summaryRes = R.string.service_library_reservation_summary,
        group = ServiceGroup.Library,
        target = ServiceTarget.Web(CampusService.LibraryReservation),
    ),
    CampusCard(
        titleRes = R.string.service_campus_card,
        summaryRes = R.string.service_campus_card_summary,
        group = ServiceGroup.StudentAffairs,
        target = ServiceTarget.Web(CampusService.CampusCard),
    ),
    Mail(
        titleRes = R.string.service_mail,
        summaryRes = R.string.service_mail_summary,
        group = ServiceGroup.StudentAffairs,
        target = ServiceTarget.Web(CampusService.Mail),
    ),
    Leave(
        titleRes = R.string.service_leave,
        summaryRes = R.string.service_leave_summary,
        group = ServiceGroup.StudentAffairs,
        target = ServiceTarget.Web(CampusService.Leave),
    ),
    PresidentMailbox(
        titleRes = R.string.service_president_mailbox,
        summaryRes = R.string.service_president_mailbox_summary,
        group = ServiceGroup.StudentAffairs,
        target = ServiceTarget.Web(CampusService.PresidentMailbox),
    ),
    StudentStatus(
        titleRes = R.string.service_student_status,
        summaryRes = R.string.service_student_status_summary,
        group = ServiceGroup.EasRecords,
        target = ServiceTarget.Web(CampusService.StudentStatus),
    ),
    ProgramCompletion(
        titleRes = R.string.service_program_completion,
        summaryRes = R.string.service_program_completion_summary,
        group = ServiceGroup.EasRecords,
        target = ServiceTarget.Web(CampusService.ProgramCompletion),
    ),
    AcademicWarnings(
        titleRes = R.string.service_academic_warnings,
        summaryRes = R.string.service_academic_warnings_summary,
        group = ServiceGroup.EasRecords,
        target = ServiceTarget.Web(CampusService.AcademicWarnings),
    ),
    Exams(
        titleRes = R.string.service_exams,
        summaryRes = R.string.service_exams_summary,
        group = ServiceGroup.EasExamsAndGrades,
        target = ServiceTarget.Web(CampusService.Exams),
    ),
    Grades(
        titleRes = R.string.service_grades,
        summaryRes = R.string.service_grades_summary,
        group = ServiceGroup.EasExamsAndGrades,
        target = ServiceTarget.Web(CampusService.Grades),
    ),
    Timetable(
        titleRes = R.string.service_timetable,
        summaryRes = R.string.service_timetable_summary,
        group = ServiceGroup.EasSchedule,
        target = ServiceTarget.Web(CampusService.Timetable),
    ),
    CourseOfferings(
        titleRes = R.string.service_course_offerings,
        summaryRes = R.string.service_course_offerings_summary,
        group = ServiceGroup.EasSchedule,
        target = ServiceTarget.Web(CampusService.CourseOfferings),
    ),
    SchoolCalendar(
        titleRes = R.string.service_school_calendar,
        summaryRes = R.string.service_school_calendar_summary,
        group = ServiceGroup.EasSchedule,
        target = ServiceTarget.Web(CampusService.SchoolCalendar),
    ),
    AvailableClassrooms(
        titleRes = R.string.service_available_classrooms,
        summaryRes = R.string.service_available_classrooms_summary,
        group = ServiceGroup.EasSchedule,
        target = ServiceTarget.Web(CampusService.AvailableClassrooms),
    ),
    ClassroomTimetable(
        titleRes = R.string.service_classroom_timetable,
        summaryRes = R.string.service_classroom_timetable_summary,
        group = ServiceGroup.EasSchedule,
        target = ServiceTarget.Web(CampusService.ClassroomTimetable),
    ),
    ;

    fun isOpening(openingService: CampusService?): Boolean =
        openingService != null && (target as? ServiceTarget.Web)?.service == openingService
}

@Composable
internal fun ServiceGroupSection(
    group: ServiceGroup,
    openingService: CampusService?,
    onOpen: (ServiceTarget) -> Unit,
) {
    Column {
        SmallTitle(
            text = stringResource(group.titleRes),
            modifier = Modifier.padding(top = 12.dp),
        )
        Card(modifier = Modifier.padding(horizontal = CardSpacing)) {
            group.entries.forEach { entry ->
                ServiceEntryRow(
                    entry = entry,
                    isOpening = entry.isOpening(openingService),
                    onClick = { onOpen(entry.target) },
                )
            }
        }
    }
}

@Composable
internal fun ServiceEntryRow(
    entry: ServiceEntry,
    isOpening: Boolean,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = stringResource(entry.titleRes),
        summary = stringResource(entry.summaryRes),
        startAction = { ServiceIcon(entry.icon) },
        onClick = onClick,
        holdDownState = isOpening,
    )
}

@Composable
private fun ServiceIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.padding(end = 6.dp),
        tint = MiuixTheme.colorScheme.onBackground,
    )
}

private val ServiceEntry.icon: ImageVector
    @Composable get() = when (this) {
        ServiceEntry.UndergradEas -> ImageVector.vectorResource(R.drawable.ic_lucide_graduation_cap)
        ServiceEntry.Library -> ImageVector.vectorResource(R.drawable.ic_lucide_library_big)
        ServiceEntry.LibraryReservation -> ImageVector.vectorResource(R.drawable.ic_lucide_armchair)
        ServiceEntry.CampusCard -> ImageVector.vectorResource(R.drawable.ic_lucide_id_card)
        ServiceEntry.Mail -> MiuixIcons.Email
        ServiceEntry.Leave -> ImageVector.vectorResource(R.drawable.ic_lucide_calendar_off)
        ServiceEntry.PresidentMailbox -> ImageVector.vectorResource(R.drawable.ic_lucide_inbox)
        ServiceEntry.StudentStatus -> MiuixIcons.ContactsBook
        ServiceEntry.ProgramCompletion -> ImageVector.vectorResource(R.drawable.ic_lucide_clipboard_check)
        ServiceEntry.AcademicWarnings -> ImageVector.vectorResource(R.drawable.ic_lucide_triangle_alert)
        ServiceEntry.Exams -> ImageVector.vectorResource(R.drawable.ic_lucide_file_pen_line)
        ServiceEntry.Grades -> ImageVector.vectorResource(R.drawable.ic_lucide_award)
        ServiceEntry.Timetable -> MiuixIcons.Months
        ServiceEntry.CourseOfferings -> MiuixIcons.ListView
        ServiceEntry.SchoolCalendar -> MiuixIcons.Years
        ServiceEntry.AvailableClassrooms -> ImageVector.vectorResource(R.drawable.ic_lucide_door_open)
        ServiceEntry.ClassroomTimetable -> ImageVector.vectorResource(R.drawable.ic_lucide_calendar_clock)
    }

private val QueryWhitespace = Regex("\\s+")

/** 判断 [query] 按空白拆出的每个关键词是否都能在任意一个 [fields] 中忽略大小写命中。 */
internal fun matchesServiceQuery(query: String, vararg fields: String): Boolean {
    val tokens = query.trim().split(QueryWhitespace).filter(String::isNotEmpty)
    if (tokens.isEmpty()) return true
    return tokens.all { token -> fields.any { field -> field.contains(token, ignoreCase = true) } }
}

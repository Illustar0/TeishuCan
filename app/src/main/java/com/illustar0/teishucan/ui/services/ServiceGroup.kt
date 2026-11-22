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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ContactsBook
import top.yukonga.miuix.kmp.icon.extended.Email
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Years
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal class ServiceGroup(
    @param:StringRes val titleRes: Int,
    val services: List<CampusService>,
)

@Composable
internal fun ServiceGroupSection(
    group: ServiceGroup,
    openingService: CampusService?,
    onOpenService: (CampusService) -> Unit,
) {
    Column {
        SmallTitle(
            text = stringResource(group.titleRes),
            modifier = Modifier.padding(top = 12.dp),
        )
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            group.services.forEach { service ->
                ServiceRow(
                    service = service,
                    isOpening = service == openingService,
                    onClick = { onOpenService(service) },
                )
            }
        }
    }
}

@Composable
private fun ServiceRow(
    service: CampusService,
    isOpening: Boolean,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = stringResource(service.titleRes),
        summary = stringResource(service.summaryRes),
        startAction = { ServiceIcon(service.icon) },
        onClick = onClick,
        holdDownState = isOpening,
    )
}

@Composable
internal fun ServiceIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.padding(end = 6.dp),
        tint = MiuixTheme.colorScheme.onBackground,
    )
}

@get:StringRes
private val CampusService.titleRes: Int
    get() = when (this) {
        CampusService.Library -> R.string.service_library
        CampusService.LibraryReservation -> R.string.service_library_reservation
        CampusService.CampusCard -> R.string.service_campus_card
        CampusService.Leave -> R.string.service_leave
        CampusService.PresidentMailbox -> R.string.service_president_mailbox
        CampusService.StudentStatus -> R.string.service_student_status
        CampusService.ProgramCompletion -> R.string.service_program_completion
        CampusService.AcademicWarnings -> R.string.service_academic_warnings
        CampusService.Exams -> R.string.service_exams
        CampusService.Grades -> R.string.service_grades
        CampusService.Timetable -> R.string.service_timetable
        CampusService.CourseOfferings -> R.string.service_course_offerings
        CampusService.SchoolCalendar -> R.string.service_school_calendar
        CampusService.AvailableClassrooms -> R.string.service_available_classrooms
        CampusService.ClassroomTimetable -> R.string.service_classroom_timetable
    }

@get:StringRes
private val CampusService.summaryRes: Int
    get() = when (this) {
        CampusService.Library -> R.string.service_library_summary
        CampusService.LibraryReservation -> R.string.service_library_reservation_summary
        CampusService.CampusCard -> R.string.service_campus_card_summary
        CampusService.Leave -> R.string.service_leave_summary
        CampusService.PresidentMailbox -> R.string.service_president_mailbox_summary
        CampusService.StudentStatus -> R.string.service_student_status_summary
        CampusService.ProgramCompletion -> R.string.service_program_completion_summary
        CampusService.AcademicWarnings -> R.string.service_academic_warnings_summary
        CampusService.Exams -> R.string.service_exams_summary
        CampusService.Grades -> R.string.service_grades_summary
        CampusService.Timetable -> R.string.service_timetable_summary
        CampusService.CourseOfferings -> R.string.service_course_offerings_summary
        CampusService.SchoolCalendar -> R.string.service_school_calendar_summary
        CampusService.AvailableClassrooms -> R.string.service_available_classrooms_summary
        CampusService.ClassroomTimetable -> R.string.service_classroom_timetable_summary
    }

private val CampusService.icon: ImageVector
    @Composable get() = when (this) {
        CampusService.Library -> ImageVector.vectorResource(R.drawable.ic_lucide_library_big)
        CampusService.LibraryReservation -> ImageVector.vectorResource(R.drawable.ic_lucide_armchair)
        CampusService.CampusCard -> ImageVector.vectorResource(R.drawable.ic_lucide_id_card)
        CampusService.Leave -> ImageVector.vectorResource(R.drawable.ic_lucide_calendar_off)
        CampusService.PresidentMailbox -> MiuixIcons.Email
        CampusService.StudentStatus -> MiuixIcons.ContactsBook
        CampusService.ProgramCompletion -> MiuixIcons.Tasks
        CampusService.AcademicWarnings -> ImageVector.vectorResource(R.drawable.ic_lucide_triangle_alert)
        CampusService.Exams -> ImageVector.vectorResource(R.drawable.ic_lucide_file_pen_line)
        CampusService.Grades -> ImageVector.vectorResource(R.drawable.ic_lucide_award)
        CampusService.Timetable -> MiuixIcons.Months
        CampusService.CourseOfferings -> MiuixIcons.ListView
        CampusService.SchoolCalendar -> MiuixIcons.Years
        CampusService.AvailableClassrooms -> ImageVector.vectorResource(R.drawable.ic_lucide_door_open)
        CampusService.ClassroomTimetable -> ImageVector.vectorResource(R.drawable.ic_lucide_calendar_clock)
    }

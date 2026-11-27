package com.illustar0.teishucan.data

import com.illustar0.teishucan.domain.CampusFeature
import com.illustar0.teishucan.domain.CampusService
import com.illustar0.zzu.cas.SsoService
import javax.inject.Inject
import javax.inject.Singleton

/** 为各校内网页服务签发单点登录入口地址。 */
@Singleton
class CampusServiceRepository @Inject constructor(
    private val session: ZzuSession,
) {
    suspend fun loginUrl(service: CampusService): String =
        session.request(CampusFeature.Services) { cas.serviceSsoUrl(service.toSsoService()) }
}

private fun CampusService.toSsoService() = when (this) {
    CampusService.Library -> SsoService.LIBRARY
    CampusService.LibraryReservation -> SsoService.LIBRARY_RESERVATION
    CampusService.CampusCard -> SsoService.ECARD
    CampusService.Mail -> SsoService.EMAIL
    CampusService.Leave -> SsoService.LEAVE
    CampusService.PresidentMailbox -> SsoService.PRESIDENT_MAILBOX
    CampusService.StudentStatus -> SsoService.UndergradEas.STUDENT_STATUS
    CampusService.ProgramCompletion -> SsoService.UndergradEas.PROGRAM_COMPLETION
    CampusService.AcademicWarnings -> SsoService.UndergradEas.ACADEMIC_WARNINGS
    CampusService.Exams -> SsoService.UndergradEas.EXAMS
    CampusService.Grades -> SsoService.UndergradEas.GRADES
    CampusService.Timetable -> SsoService.UndergradEas.TIMETABLE
    CampusService.CourseOfferings -> SsoService.UndergradEas.COURSE_OFFERINGS
    CampusService.SchoolCalendar -> SsoService.UndergradEas.SCHOOL_CALENDAR
    CampusService.ClassroomTimetable -> SsoService.UndergradEas.CLASSROOM_TIMETABLE
}

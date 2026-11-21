package com.illustar0.teishucan.data

import com.illustar0.teishucan.domain.CampusFeature
import com.illustar0.teishucan.domain.CampusService
import com.illustar0.teishucan.domain.PaymentChannel
import com.illustar0.teishucan.domain.RechargeOrder
import com.illustar0.teishucan.domain.StudentData
import com.illustar0.teishucan.domain.StudentProfile
import com.illustar0.zzu.cas.PersonalInfo
import com.illustar0.zzu.cas.SsoService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Instant

@Singleton
class StudentRepository @Inject constructor(
    private val session: ZzuSession,
) {
    private val cache = session.createSnapshotCache(
        "student.json",
        StudentData(),
        StudentData.serializer(),
    )
    val student: StateFlow<StudentData> = cache.value

    suspend fun isStaleAt(now: Instant, maxAge: Duration): Boolean = cache.isStaleAt(now, maxAge)

    suspend fun refresh() = session.request(CampusFeature.CampusCard) {
        val refreshVersion = cache.nextRefreshVersion()
        val info = cas.userInfo()
        cache.update(
            this,
            refreshVersion,
            saveRelatedData = { session.saveProfile(this, info.toStudentProfile()) },
        ) { info.toStudentData() }
    }

    /** 余额可能已在应用外变化，例如在收银台付了款。下次自动刷新时必定更新。 */
    fun expire() {
        cache.expire()
    }

    suspend fun createRechargeOrder(amountYuan: Int): RechargeOrder {
        require(amountYuan > 0)
        return session.request(CampusFeature.CampusCard) {
            val card = ecard()
            val order = card.createRechargeOrder(amountYuan)
            RechargeOrder(
                amountYuan = order.amount,
                channels = card.paymentChannels(order).map { PaymentChannel(it.name, order.checkoutUrlFor(it)) },
            )
        }
    }

    suspend fun mailLoginUrl(): String =
        session.request(CampusFeature.Mail) { cas.serviceLoginUrl(SsoService.EMAIL) }

    suspend fun serviceLoginUrl(service: CampusService): String =
        session.request(CampusFeature.Services) { cas.serviceLoginUrl(service.toSsoService()) }
}

private fun CampusService.toSsoService() = when (this) {
    CampusService.Library -> SsoService.LIBRARY
    CampusService.LibraryReservation -> SsoService.LIBRARY_RESERVATION
    CampusService.CampusCard -> SsoService.ECARD
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
    CampusService.AvailableClassrooms -> SsoService.UndergradEas.AVAILABLE_CLASSROOMS
    CampusService.ClassroomTimetable -> SsoService.UndergradEas.CLASSROOM_TIMETABLE
}

internal fun PersonalInfo.toStudentProfile() = StudentProfile(name, organizationName, identityTypeName)

private fun PersonalInfo.toStudentData() = StudentData(
    campusCardBalanceYuan = balance,
    mailCount = unreadEmailCount.toInt(),
    researchProjectCount = researchCount.toInt(),
)

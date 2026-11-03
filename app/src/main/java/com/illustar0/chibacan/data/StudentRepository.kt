package com.illustar0.chibacan.data

import com.illustar0.chibacan.di.ApplicationScope
import com.illustar0.chibacan.domain.CampusFeature
import com.illustar0.chibacan.domain.CampusService
import com.illustar0.chibacan.domain.PaymentChannel
import com.illustar0.chibacan.domain.RechargeOrder
import com.illustar0.chibacan.domain.StudentData
import com.illustar0.chibacan.domain.StudentProfile
import dev.zzu.cas.PersonalInfo
import dev.zzu.cas.SsoService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Instant

@Singleton
class StudentRepository @Inject constructor(
    private val session: ZzuSession,
    snapshots: SignInSnapshotStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val cache = SignInCache(
        session.state,
        scope,
        StudentData(),
        snapshots.file("student.json", StudentData.serializer()),
    )
    val student: StateFlow<StudentData> = cache.value

    /** [student] 在 [now] 是否超过 [maxAge] 未更新，或被 [expire] 过。 */
    suspend fun isStaleAt(now: Instant, maxAge: Duration): Boolean = cache.isStaleAt(now, maxAge)

    suspend fun refresh() = session.request(CampusFeature.CampusCard) {
        val info = cas.userInfo()
        session.saveProfile(this, info.toStudentProfile())
        cache.update(this) { info.toStudentData() }
    }

    /** 余额可能已在应用外变化，例如在收银台付了款。下次自动刷新时必定更新。 */
    fun expire() {
        cache.expire()
    }

    /** 创建校园卡充值订单，订单连同各支付渠道的收银台地址一起返回。 */
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

    /** 免登录打开学生邮箱的地址。 */
    suspend fun mailLoginUrl(): String =
        session.request(CampusFeature.Mail) { cas.serviceLoginUrl(SsoService.EMAIL) }

    /** 免登录打开 [service] 的地址。 */
    suspend fun serviceLoginUrl(service: CampusService): String =
        session.request(CampusFeature.Services) { cas.serviceLoginUrl(service.toSsoService()) }
}

private fun CampusService.toSsoService() = when (this) {
    CampusService.Library -> SsoService.LIBRARY
    CampusService.LibraryReservation -> SsoService.LIBRARY_RESERVATION
    CampusService.Leave -> SsoService.LEAVE
    CampusService.PresidentMailbox -> SsoService.PRESIDENT_MAILBOX
    CampusService.StudentStatus -> SsoService.UndergradEas.STUDENT_STATUS
    CampusService.ProgramCompletion -> SsoService.UndergradEas.PROGRAM_COMPLETION
    CampusService.AcademicWarnings -> SsoService.UndergradEas.ACADEMIC_WARNINGS
    CampusService.CourseSelection -> SsoService.UndergradEas.COURSE_SELECTION
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

package com.illustar0.teishucan.data

import com.illustar0.teishucan.domain.CampusFeature
import com.illustar0.teishucan.domain.CampusService
import com.illustar0.teishucan.domain.PaymentChannel
import com.illustar0.teishucan.domain.RechargeOrder
import com.illustar0.teishucan.domain.StudentData
import com.illustar0.teishucan.domain.StudentProfile
import com.illustar0.zzu.cas.PersonalInfo
import com.illustar0.zzu.cas.SsoService
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.supervisorScope
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

    /** 并发刷新个人资料、校园卡余额与未读邮件；部分失败时保留已拿到的数据，再抛出第一个失败。 */
    suspend fun refresh() = session.request(CampusFeature.CampusCard) {
        val refreshVersion = cache.nextRefreshVersion()
        val fetched = supervisorScope {
            val info = async { runCatchingNonCancellation { cas.userInfo() } }
            val balance = async { runCatchingNonCancellation { ecard().balance() } }
            val unreadMail = async { runCatchingNonCancellation { mail().unreadCount() } }
            StudentRefresh(info.await(), balance.await(), unreadMail.await())
        }
        applyRefresh(this, refreshVersion, fetched)
        fetched.firstFailure?.let { throw it }
    }

    private suspend fun applyRefresh(owner: ZzuClients, refreshVersion: Long, fetched: StudentRefresh) {
        val profile = fetched.info.getOrNull()?.toStudentProfile()
        val balance = fetched.balance.getOrNull()
        val unreadMail = fetched.unreadMail.getOrNull()
        if (fetched.firstFailure == null) {
            cache.update(
                owner,
                refreshVersion,
                saveRelatedData = { if (profile != null) session.saveProfile(owner, profile) },
            ) {
                StudentData(
                    campusCardBalanceYuan = balance,
                    mailCount = unreadMail,
                )
            }
            return
        }
        if (profile != null) session.saveProfile(owner, profile)
        if (balance != null || unreadMail != null) {
            cache.amend(owner) { current ->
                current.copy(
                    campusCardBalanceYuan = balance ?: current.campusCardBalanceYuan,
                    mailCount = unreadMail ?: current.mailCount,
                    researchProjectCount = null,
                )
            }
        }
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
        session.request(CampusFeature.Mail) { cas.serviceSsoUrl(SsoService.EMAIL) }

    suspend fun serviceLoginUrl(service: CampusService): String =
        session.request(CampusFeature.Services) { cas.serviceSsoUrl(service.toSsoService()) }
}

private class StudentRefresh(
    val info: Result<PersonalInfo>,
    val balance: Result<BigDecimal>,
    val unreadMail: Result<Int>,
) {
    val firstFailure: Throwable?
        get() = info.exceptionOrNull() ?: balance.exceptionOrNull() ?: unreadMail.exceptionOrNull()
}

private suspend inline fun <T> runCatchingNonCancellation(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Exception) {
    Result.failure(cause)
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

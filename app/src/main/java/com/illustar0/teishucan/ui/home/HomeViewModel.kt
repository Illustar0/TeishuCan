package com.illustar0.teishucan.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.R
import com.illustar0.teishucan.data.CampusRefresher
import com.illustar0.teishucan.data.ElectricityRepository
import com.illustar0.teishucan.data.StudentRepository
import com.illustar0.teishucan.data.TimetableRepository
import com.illustar0.teishucan.data.ZzuSession
import com.illustar0.teishucan.domain.AccountState
import com.illustar0.teishucan.domain.BackgroundTasks
import com.illustar0.teishucan.domain.CampusZone
import com.illustar0.teishucan.domain.MeterReading
import com.illustar0.teishucan.domain.StudentData
import com.illustar0.teishucan.domain.TimetableTerm
import com.illustar0.teishucan.domain.totalKilowattHours
import com.illustar0.teishucan.domain.upcomingClassAfter
import com.illustar0.teishucan.domain.upcomingClassAfter
import com.illustar0.teishucan.ui.component.FailureToasts
import com.illustar0.teishucan.ui.component.OpenPendingUrl
import com.illustar0.teishucan.ui.component.OutcomeHaptics
import com.illustar0.teishucan.ui.component.PendingUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    session: ZzuSession,
    timetable: TimetableRepository,
    private val student: StudentRepository,
    electricity: ElectricityRepository,
    private val refresher: CampusRefresher,
) : ViewModel() {
    private val now = MutableStateFlow(LocalDateTime.now(CampusZone))

    val tasks = BackgroundTasks(viewModelScope)
    val isRefreshing: StateFlow<Boolean> = refresher.isRefreshing

    /** 等待在浏览器中打开的邮箱地址。 */
    val mailUrl = PendingUrl(tasks)

    val state: StateFlow<HomeState> = combine(
        timetable.terms, now, student.student, electricity.meters, session.account,
    ) { terms, time, data, meters, account ->
        homeState(terms, time, data, meters, account)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        homeState(
            timetable.terms.value,
            now.value,
            student.student.value,
            electricity.meters.value,
            session.account.value,
        ),
    )

    /** 让上次计算之后已经开始的课程从“下一节课”中退场。 */
    fun updateTime() {
        now.value = LocalDateTime.now(CampusZone)
    }

    fun refresh() {
        updateTime()
        refresher.tasks.refresh()
    }

    fun openMail() {
        mailUrl.fetch(student::mailLoginUrl)
    }

    private fun homeState(
        terms: List<TimetableTerm>,
        time: LocalDateTime,
        student: StudentData,
        meters: List<MeterReading>?,
        account: AccountState,
    ) = HomeState(
        upcomingClass = terms.upcomingClassAfter(time),
        campusCardBalanceYuan = student.campusCardBalanceYuan,
        mailCount = student.mailCount,
        researchProjectCount = student.researchProjectCount,
        electricityKilowattHours = meters?.totalKilowattHours(),
        hasMeters = !meters.isNullOrEmpty(),
        profile = (account as? AccountState.SignedIn)?.profile,
    )
}

@Composable
fun HomeRoute(
    onOpenElectricitySettings: () -> Unit,
    bottomPadding: Dp,
) {
    val viewModel = hiltViewModel<HomeViewModel>()
    val recharge = hiltViewModel<CampusCardRechargeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    FailureToasts(viewModel.tasks)
    OpenPendingUrl(viewModel.mailUrl, failureMessage = R.string.mail_open_failed)
    OutcomeHaptics(recharge.outcomes)
    // ViewModel 比分页页面活得久，不更新的话时间会停在第一次进入时。
    LifecycleResumeEffect(viewModel) {
        viewModel.updateTime()
        onPauseOrDispose {}
    }

    HomePage(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        isRechargeVisible = recharge.state.isVisible,
        onOpenRecharge = recharge::open,
        onOpenMail = viewModel::openMail,
        onOpenElectricitySettings = onOpenElectricitySettings,
        bottomPadding = bottomPadding,
        rechargeSheet = {
            CampusCardRechargeSheet(
                state = recharge.state,
                campusCardBalanceYuan = state.campusCardBalanceYuan,
                onSubmitAmount = recharge::submitAmount,
                onCheckoutOpen = recharge::finishCheckout,
                onCheckoutFail = recharge::failCheckout,
                onDismissRequest = recharge::dismiss,
                onDismissFinished = recharge::reset,
            )
        },
    )
}

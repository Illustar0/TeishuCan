package com.illustar0.teishucan.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.data.CampusCardRepository
import com.illustar0.teishucan.data.CampusRefresher
import com.illustar0.teishucan.data.ElectricityRepository
import com.illustar0.teishucan.data.TimetableRepository
import com.illustar0.teishucan.data.ZzuSession
import com.illustar0.teishucan.domain.AccountState
import com.illustar0.teishucan.domain.CampusZone
import com.illustar0.teishucan.domain.MeterReading
import com.illustar0.teishucan.domain.UpcomingClass
import com.illustar0.teishucan.domain.totalKilowattHours
import com.illustar0.teishucan.domain.upcomingClassAfter
import com.illustar0.teishucan.ui.component.OutcomeHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
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
    campusCard: CampusCardRepository,
    electricity: ElectricityRepository,
    private val refresher: CampusRefresher,
) : ViewModel() {
    private val now = MutableStateFlow(LocalDateTime.now(CampusZone))
    private val upcomingClass = combine(timetable.terms, now) { terms, time -> terms.upcomingClassAfter(time) }

    val isRefreshing: StateFlow<Boolean> = refresher.isRefreshing

    val state: StateFlow<HomeState> = combine(
        upcomingClass,
        campusCard.balanceYuan,
        electricity.meters,
        session.account,
    ) { upcoming, balance, meters, account ->
        homeState(upcoming, balance, meters, account)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        homeState(
            timetable.terms.value.upcomingClassAfter(now.value),
            campusCard.balanceYuan.value,
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
        refresher.refresh()
    }

    private fun homeState(
        upcomingClass: UpcomingClass,
        campusCardBalanceYuan: BigDecimal?,
        meters: List<MeterReading>?,
        account: AccountState,
    ) = HomeState(
        upcomingClass = upcomingClass,
        campusCardBalanceYuan = campusCardBalanceYuan,
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
        isRechargeVisible = recharge.draft != null,
        onOpenRecharge = recharge::open,
        onOpenElectricitySettings = onOpenElectricitySettings,
        bottomPadding = bottomPadding,
        rechargeSheet = {
            CampusCardRechargeSheet(
                draft = recharge.draft,
                campusCardBalanceYuan = state.campusCardBalanceYuan,
                onSubmitAmount = recharge::submitAmount,
                onCheckoutOpen = recharge::finishCheckout,
                onCheckoutFail = recharge::failCheckout,
                onDismissRequest = recharge::dismiss,
            )
        },
    )
}

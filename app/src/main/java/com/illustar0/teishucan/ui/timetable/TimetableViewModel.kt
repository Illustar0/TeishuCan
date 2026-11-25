package com.illustar0.teishucan.ui.timetable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.concurrency.PendingFailure
import com.illustar0.teishucan.concurrency.RefreshQueue
import com.illustar0.teishucan.data.CampusZone
import com.illustar0.teishucan.data.TimetableRepository
import com.illustar0.teishucan.data.ZzuSession
import com.illustar0.teishucan.data.campusTimeTicks
import com.illustar0.teishucan.domain.AccountState
import com.illustar0.teishucan.domain.TimetableTerm
import com.illustar0.teishucan.domain.defaultTerm
import com.illustar0.teishucan.ui.component.FailureToasts
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

private const val SelectedTermIdKey = "selectedTermId"

private enum class TermLoad { Loading, Failed }

@HiltViewModel
class TimetableViewModel @Inject constructor(
    savedState: SavedStateHandle,
    session: ZzuSession,
    private val timetable: TimetableRepository,
) : ViewModel() {
    /** 用户选中的学期，null 时打开默认学期。这里只存 id，内容在进程重建后从数据层重新加载。 */
    private val selectedTermId = savedState.getMutableStateFlow<String?>(SelectedTermIdKey, null)

    /** 各学期课程的加载情况，没有记录的学期既不在加载也没有失败。 */
    private val termLoads = MutableStateFlow<Map<String, TermLoad>>(emptyMap())

    val failure = PendingFailure()

    /** 这次登录发起的加载与刷新都在这里运行。登录结束时整个取消，迟到的结果不会落到下一个账号上。 */
    private var signInScope = newSignInScope()
    private val refreshQueue = RefreshQueue {
        signInScope.launch { failure.capture { refreshPage() } }.join()
    }

    private val isRefreshing = combine(refreshQueue.isRefreshing, timetable.isRefreshing) { page, timetable ->
        page || timetable
    }

    /** 默认学期与本周都以今天为准，跨过零点时随之更新。 */
    private val today = campusTimeTicks(ChronoUnit.DAYS).map { it.toLocalDate() }

    val state: StateFlow<TimetableState> = combine(
        timetable.terms,
        selectedTermId,
        termLoads,
        isRefreshing,
        today,
    ) { terms, selectedId, loads, isRefreshing, today ->
        val term = terms.find { it.id == selectedId } ?: terms.defaultTerm(today)
        TimetableState(
            today = today,
            terms = terms,
            term = term,
            status = statusOf(term, term?.let { loads[it.id] }, isRefreshing),
            isRefreshing = isRefreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TimetableState(today = LocalDate.now(CampusZone)))

    init {
        viewModelScope.launch { refreshQueue.run() }
        // 无论学期是选中的、恢复的还是默认的，只要正在展示却还没有课程就加载。换过学期再换回来会重试失败的学期。
        viewModelScope.launch {
            state.map { it.term?.takeUnless(TimetableTerm::isLoaded)?.id }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { termId ->
                    signInScope.launch { failure.capture { load(termId) } }
                }
        }
        // 选中的学期、各学期的加载情况和待提示的失败都属于这次登录，下一个账号应该从头开始。
        viewModelScope.launch {
            session.account
                .filter { it == AccountState.SignedOut }
                .collect {
                    signInScope.cancel()
                    signInScope = newSignInScope()
                    selectedTermId.value = null
                    termLoads.value = emptyMap()
                    failure.clear()
                }
        }
    }

    fun selectTerm(term: TimetableTerm) {
        selectedTermId.value = term.id
    }

    fun refresh() = refreshQueue.request()

    private suspend fun refreshPage() {
        timetable.refresh()
        val term = state.value.term ?: return
        if (selectedTermId.value != null || !term.isLoaded) load(term.id)
    }

    private suspend fun load(termId: String) {
        termLoads.update { it + (termId to TermLoad.Loading) }
        try {
            timetable.loadTerm(termId)
            termLoads.update { it - termId }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            termLoads.update { it + (termId to TermLoad.Failed) }
            throw cause
        }
    }

    private fun newSignInScope(): CoroutineScope {
        val parent = viewModelScope.coroutineContext
        return CoroutineScope(parent + SupervisorJob(parent.job))
    }

    private fun statusOf(term: TimetableTerm?, load: TermLoad?, isRefreshing: Boolean): TimetableStatus = when {
        term?.isLoaded == true -> {
            if (term.weeks.all { it.entries.isEmpty() }) TimetableStatus.NoLessons else TimetableStatus.Ready
        }

        isRefreshing || load == TermLoad.Loading -> TimetableStatus.Loading
        load == TermLoad.Failed -> TimetableStatus.Failed
        else -> TimetableStatus.NotFetched
    }
}

@Composable
fun TimetableRoute(bottomPadding: Dp) {
    val viewModel = hiltViewModel<TimetableViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    FailureToasts(viewModel.failure)

    TimetablePage(
        state = state,
        onRefresh = viewModel::refresh,
        onTermSelect = viewModel::selectTerm,
        bottomPadding = bottomPadding,
    )
}

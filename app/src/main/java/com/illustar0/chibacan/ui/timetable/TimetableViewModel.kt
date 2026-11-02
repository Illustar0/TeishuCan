package com.illustar0.chibacan.ui.timetable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.illustar0.chibacan.data.CampusRefresher
import com.illustar0.chibacan.data.TimetableRepository
import com.illustar0.chibacan.data.ZzuSession
import com.illustar0.chibacan.domain.AccountState
import com.illustar0.chibacan.domain.BackgroundTasks
import com.illustar0.chibacan.domain.CampusZone
import com.illustar0.chibacan.domain.TimetableTerm
import com.illustar0.chibacan.domain.defaultTerm
import com.illustar0.chibacan.ui.component.FailureToasts
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.launch

private const val SelectedTermIdKey = "selectedTermId"

private enum class TermLoad { Loading, Failed }

@HiltViewModel
class TimetableViewModel @Inject constructor(
    savedState: SavedStateHandle,
    session: ZzuSession,
    private val timetable: TimetableRepository,
    refresher: CampusRefresher,
) : ViewModel() {
    /** 用户选中的学期，null 时打开默认学期。这里只存 id，内容在进程重建后从数据层重新加载。 */
    private val selectedTermId = savedState.getMutableStateFlow<String?>(SelectedTermIdKey, null)

    /** 各学期课程的加载情况，没有记录的学期既不在加载也没有失败。 */
    private val termLoads = MutableStateFlow<Map<String, TermLoad>>(emptyMap())

    val tasks = BackgroundTasks(viewModelScope) { refreshPage() }

    val state: StateFlow<TimetableState> = combine(
        timetable.terms,
        selectedTermId,
        termLoads,
        tasks.isRefreshing,
        refresher.tasks.isRefreshing,
    ) { terms, selectedId, loads, isPageRefreshing, isAppRefreshing ->
        val term = terms.find { it.id == selectedId } ?: terms.defaultTerm(LocalDate.now(CampusZone))
        val isRefreshing = isPageRefreshing || isAppRefreshing
        TimetableState(
            terms = terms,
            term = term,
            status = statusOf(term, term?.let { loads[it.id] }, isRefreshing),
            isRefreshing = isRefreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TimetableState())

    init {
        // 无论学期是选中的、恢复的还是默认的，只要正在展示却还没有课程就加载。换过学期再换回来会重试失败的学期。
        viewModelScope.launch {
            state.map { it.term?.takeUnless(TimetableTerm::isLoaded)?.id }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { termId -> tasks.launch { load(termId) } }
        }
        // 选中的学期属于这次登录，下一个账号应该打开默认学期。
        viewModelScope.launch {
            session.account
                .filter { it == AccountState.SignedOut }
                .collect { selectedTermId.value = null }
        }
    }

    fun selectTerm(term: TimetableTerm) {
        selectedTermId.value = term.id
    }

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

    FailureToasts(viewModel.tasks)

    TimetablePage(
        state = state,
        onRefresh = viewModel.tasks::refresh,
        onTermSelect = viewModel::selectTerm,
        bottomPadding = bottomPadding,
    )
}

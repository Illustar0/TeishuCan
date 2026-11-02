package com.illustar0.chibacan.data

import com.illustar0.chibacan.di.ApplicationScope
import com.illustar0.chibacan.domain.AccountState
import com.illustar0.chibacan.domain.BackgroundTasks
import com.illustar0.chibacan.domain.refreshAll
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 全应用数据的刷新入口，自动刷新和首页下拉都走这里。
 *
 * 不归属任何页面，所以无论应用恢复到哪一页，数据都会加载。
 */
@Singleton
class CampusRefresher @Inject constructor(
    private val session: ZzuSession,
    timetable: TimetableRepository,
    student: StudentRepository,
    electricity: ElectricityRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val tasks = BackgroundTasks(scope) { refreshAll(timetable::refresh, student::refresh, electricity::refresh) }

    /** 全应用唯一的自动刷新：应用进入前台时，或在前台完成登录时。 */
    fun refreshOnForeground(isForeground: Flow<Boolean>) {
        val isSignedIn = session.account.map { it is AccountState.SignedIn }
        scope.launch {
            combine(isForeground, isSignedIn) { foreground, signedIn -> foreground && signedIn }
                .distinctUntilChanged()
                .filter { it }
                .collect { tasks.refresh() }
        }
    }
}

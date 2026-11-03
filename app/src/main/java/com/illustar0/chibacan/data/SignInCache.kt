package com.illustar0.chibacan.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 当前这次登录查询到的数据，每次更新都存一份快照到 [file]。
 *
 * 登录开始时读回同一账号的快照，冷启动后刷新完成之前也有内容可显示。
 * 登录结束后读回 [initial]，上一次登录迟到的响应也绝不会落到下一次登录上。
 */
internal class SignInCache<T : Any>(
    private val session: StateFlow<SessionState>,
    scope: CoroutineScope,
    private val initial: T,
    private val file: SignInSnapshotStore.SnapshotFile<T>,
) {
    private class Entry<T>(val owner: ZzuClients, val account: String, val value: T)

    private val entry = MutableStateFlow<Entry<T>?>(null)

    val value: StateFlow<T> = combine(session, entry) { state, latest -> valueFor(state.clients, latest) }
        .stateIn(scope, SharingStarted.Eagerly, initial)

    init {
        scope.launch {
            // 存档更新会替换 SignIn 实例，只有换了登录才需要读快照。
            session.filterIsInstance<SignIn>().distinctUntilChangedBy { it.clients }.collect { restore(it) }
        }
        scope.launch {
            // StateFlow 会合并来不及写的中间值，最后落盘的总是最新的一份。
            entry.filterNotNull().collect { latest ->
                file.write(Snapshot(latest.account, latest.value)) { session.value.clients === latest.owner }
            }
        }
    }

    /** 应用 [transform]，它可能运行多次；[owner] 已结束时什么也不做。 */
    fun update(owner: ZzuClients, transform: (T) -> T) {
        entry.update { latest ->
            val signIn = session.value as? SignIn
            if (signIn?.clients === owner) {
                Entry(owner, signIn.session.account, transform(valueFor(owner, latest)))
            } else {
                latest
            }
        }
    }

    /** 让 [signIn] 从它账号的快照开始。读盘期间已有更新或登录已结束时，快照作废。 */
    private suspend fun restore(signIn: SignIn) {
        val snapshot = file.read()?.takeIf { it.account == signIn.session.account } ?: return
        entry.update { latest ->
            val canRestore = latest?.owner !== signIn.clients && session.value.clients === signIn.clients
            if (canRestore) Entry(signIn.clients, snapshot.account, snapshot.value) else latest
        }
    }

    private fun valueFor(owner: ZzuClients?, latest: Entry<T>?): T =
        latest?.takeIf { it.owner === owner }?.value ?: initial
}

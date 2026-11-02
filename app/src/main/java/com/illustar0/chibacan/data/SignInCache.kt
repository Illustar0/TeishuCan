package com.illustar0.chibacan.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 当前这次登录查询到的数据。登录结束后读回 [initial]，
 * 上一次登录迟到的响应也绝不会落到下一次登录上。
 */
internal class SignInCache<T : Any>(
    private val session: StateFlow<SessionState>,
    scope: CoroutineScope,
    private val initial: T,
) {
    private class Entry<T>(val owner: ZzuClients, val value: T)

    private val entry = MutableStateFlow<Entry<T>?>(null)

    val value: StateFlow<T> = combine(session, entry) { state, latest -> valueFor(state.clients, latest) }
        .stateIn(scope, SharingStarted.Eagerly, initial)

    /** 应用 [transform]，它可能运行多次；[owner] 已结束时什么也不做。 */
    fun update(owner: ZzuClients, transform: (T) -> T) {
        entry.update { latest ->
            if (session.value.clients === owner) Entry(owner, transform(valueFor(owner, latest))) else latest
        }
    }

    private fun valueFor(owner: ZzuClients?, latest: Entry<T>?): T =
        latest?.takeIf { it.owner === owner }?.value ?: initial
}

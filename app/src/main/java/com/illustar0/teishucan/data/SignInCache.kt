package com.illustar0.teishucan.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

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
    /** [fetchedAt] 是 [value] 最近一次与服务端核对的时间，null 表示需要重新核对。 */
    private class Entry<T>(val owner: ZzuClients, val account: String, val fetchedAt: Instant?, val value: T)

    private val entry = MutableStateFlow<Entry<T>?>(null)

    /** 已经尝试过读回快照的登录，读回之前 [entry] 还不能代表这次登录。 */
    private val restoredOwner = MutableStateFlow<ZzuClients?>(null)

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
                file.write(Snapshot(latest.account, latest.fetchedAt, latest.value)) {
                    session.value.clients === latest.owner
                }
            }
        }
    }

    /** 用刚从服务端取得的数据应用 [transform]，它可能运行多次；[owner] 已结束时什么也不做。 */
    fun update(owner: ZzuClients, transform: (T) -> T) {
        val fetchedAt = Clock.System.now()
        modify(owner) { latest -> Entry(owner, latest.account, fetchedAt, transform(latest.value)) }
    }

    /** 补充一部分数据而不算作整体核对过，例如加载另一个学期。其余同 [update]。 */
    fun amend(owner: ZzuClients, transform: (T) -> T) {
        modify(owner) { latest -> Entry(owner, latest.account, latest.fetchedAt, transform(latest.value)) }
    }

    /** 让当前数据下次自动刷新时必定更新，数据本身照常显示。 */
    fun expire() {
        entry.update { latest -> latest?.let { Entry(it.owner, it.account, fetchedAt = null, it.value) } }
    }

    /**
     * 当前登录的数据在 [now] 是否需要重新核对：从未核对、被 [expire] 过、超过 [maxAge] 未核对，
     * 或不满足 [isComplete]（例如缺少新添加的项目）。系统时钟被往回调过也算过期，否则会一直显得新鲜。
     *
     * 会等快照读回再判断。没有登录时没有可刷新的内容，不算过期。
     */
    suspend fun isStaleAt(now: Instant, maxAge: Duration, isComplete: (T) -> Boolean = { true }): Boolean {
        val owner = session.value.clients ?: return false
        combine(session, restoredOwner) { state, restored -> state.clients !== owner || restored === owner }
            .first { it }
        val latest = entry.value?.takeIf { it.owner === owner } ?: return true
        val fetchedAt = latest.fetchedAt ?: return true
        return now - fetchedAt !in Duration.ZERO..maxAge || !isComplete(latest.value)
    }

    private fun modify(owner: ZzuClients, transform: (Entry<T>) -> Entry<T>) {
        entry.update { latest ->
            val signIn = session.value as? SignIn
            if (signIn?.clients !== owner) return@update latest
            val current = latest?.takeIf { it.owner === owner } ?: Entry(owner, signIn.session.account, null, initial)
            transform(current)
        }
    }

    /** 让 [signIn] 从它账号的快照开始。读盘期间已有更新或登录已结束时，快照作废。 */
    private suspend fun restore(signIn: SignIn) {
        val snapshot = file.read()?.takeIf { it.account == signIn.session.account }
        if (snapshot != null) {
            entry.update { latest ->
                val canRestore = latest?.owner !== signIn.clients && session.value.clients === signIn.clients
                if (canRestore) Entry(signIn.clients, snapshot.account, snapshot.fetchedAt, snapshot.value) else latest
            }
        }
        restoredOwner.value = signIn.clients
    }

    private fun valueFor(owner: ZzuClients?, latest: Entry<T>?): T =
        latest?.takeIf { it.owner === owner }?.value ?: initial
}

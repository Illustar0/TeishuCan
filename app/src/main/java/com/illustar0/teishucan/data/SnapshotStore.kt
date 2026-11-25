package com.illustar0.teishucan.data

import android.util.Log
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

private const val Tag = "TeishuCan.Snapshot"

/**
 * [signInId] 对应的登录期间查询到的 [value]，[fetchedAt] 是最近一次与服务端核对的时间。
 *
 * [refreshVersion] 是写下它的那次刷新开始的顺序，只在进程内有意义，所以不落盘，从磁盘读回时为 0。
 */
@Serializable
internal data class Snapshot<T>(
    val signInId: String,
    val fetchedAt: Instant?,
    val value: T,
    @Transient val refreshVersion: Long = 0,
)

/**
 * 一个数据域在当前这次登录期间查询到的数据，存在它独占的 DataStore 里。冷启动恢复登录后直接读回，刷新完成之前也有内容可显示。
 *
 * 每次写入都是对 DataStore 的一次原子更新：在同一次更新里核对写入方仍是当前登录，再改写属于它的快照，
 * 并发的写入不会互相覆盖，结束了的登录也写不进来。快照只属于写下它的那次登录，登录结束或换了登录就从磁盘删掉。
 */
internal class SnapshotStore<T>(
    private val store: JsonDataStore<Snapshot<T>?>,
    private val initial: T,
    private val session: StateFlow<SessionState>,
    private val scope: CoroutineScope,
) {
    private val refreshVersions = AtomicLong()

    val value: StateFlow<T> = combine(session, store.data) { state, snapshot -> snapshot.valueFor(state) }
        .stateIn(scope, SharingStarted.Eagerly, initial)

    init {
        scope.launch {
            // 会话恢复完成后才知道快照还有没有主人。存档更新会替换 SignIn 实例，只有换了登录才需要检查。
            session.filter { it != SessionState.Restoring }
                .map { (it as? SignIn)?.id }
                .distinctUntilChanged()
                .collect { discardOrphan() }
        }
    }

    fun nextRefreshVersion(): Long = refreshVersions.incrementAndGet()

    /** 写入 [owner] 刷新得到的数据。请求可以并行，写入以请求开始的顺序 [refreshVersion] 为准。 */
    suspend fun update(owner: ZzuClients, refreshVersion: Long = nextRefreshVersion(), transform: (T) -> T) {
        val fetchedAt = Clock.System.now()
        modify(owner) { signInId, current ->
            if (current != null && current.refreshVersion > refreshVersion) {
                current
            } else {
                Snapshot(signInId, fetchedAt, transform(current.valueOrInitial()), refreshVersion)
            }
        }
    }

    /** 补充一部分数据而不算作整体核对过，例如加载另一个学期。其余同 [update]。 */
    suspend fun amend(owner: ZzuClients, transform: (T) -> T) {
        modify(owner) { signInId, current ->
            current?.copy(value = transform(current.value)) ?: Snapshot(signInId, null, transform(initial))
        }
    }

    /** 让当前数据下次自动刷新时必定更新，数据本身照常显示。在调用之前开始的刷新不会再把它标回新鲜。 */
    fun expire() {
        val owner = session.value.clients ?: return
        val expireVersion = nextRefreshVersion()
        scope.launch {
            try {
                modify(owner) { _, current ->
                    if (current == null || current.refreshVersion > expireVersion) {
                        current
                    } else {
                        current.copy(fetchedAt = null, refreshVersion = expireVersion)
                    }
                }
            } catch (cause: IOException) {
                Log.w(Tag, "Could not expire the snapshot", cause)
            }
        }
    }

    /**
     * 当前登录的数据在 [now] 是否需要重新核对：从未核对、被 [expire] 过、超过 [maxAge] 未核对，
     * 或不满足 [isComplete]（例如缺少新添加的项目）。系统时钟被往回调过也算过期，否则会一直显得新鲜。
     *
     * 没有登录时没有可刷新的内容，不算过期。
     */
    suspend fun isStaleAt(now: Instant, maxAge: Duration, isComplete: (T) -> Boolean = { true }): Boolean {
        val signIn = session.value as? SignIn ?: return false
        val snapshot = store.data.first()?.takeIf { it.signInId == signIn.id }
        return snapshot == null || isStaleTimestamp(snapshot.fetchedAt, now, maxAge) || !isComplete(snapshot.value)
    }

    /**
     * 在一次原子更新里核对 [owner] 仍是当前登录，再以 [transform] 改写属于它的快照，不是就什么也不写。
     * 写入失败时抛出 [IOException]，保存的快照保持原样。
     */
    private suspend fun modify(
        owner: ZzuClients,
        transform: (signInId: String, current: Snapshot<T>?) -> Snapshot<T>?,
    ) {
        store.update { stored ->
            val signIn = session.value as? SignIn
            if (signIn == null || signIn.clients !== owner) return@update stored
            transform(signIn.id, stored?.takeIf { it.signInId == signIn.id })
        }
    }

    /** 删掉不属于当前登录的快照。按更新时的会话判断，晚到的通知不会删掉新登录刚写下的快照；快照仍有主人时不写盘。 */
    private suspend fun discardOrphan() {
        try {
            store.update { stored -> stored?.takeIf { it.signInId == (session.value as? SignIn)?.id } }
        } catch (cause: IOException) {
            Log.w(Tag, "Could not discard the snapshot", cause)
        }
    }

    private fun Snapshot<T>?.valueOrInitial(): T = if (this != null) value else initial

    private fun Snapshot<T>?.valueFor(state: SessionState): T =
        this?.takeIf { it.signInId == (state as? SignIn)?.id }.valueOrInitial()
}

/** [fetchedAt] 在 [now] 是否已经过期：从未核对、超过 [maxAge]，或系统时钟被往回调过。 */
internal fun isStaleTimestamp(fetchedAt: Instant?, now: Instant, maxAge: Duration): Boolean =
    fetchedAt == null || now - fetchedAt !in Duration.ZERO..maxAge

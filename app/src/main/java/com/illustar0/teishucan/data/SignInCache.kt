package com.illustar0.teishucan.data

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * 当前这次登录查询到的数据，每次更新都存一份快照到 [file]。
 *
 * 恢复同一次登录时读回快照，冷启动后刷新完成之前也有内容可显示。
 * 登录结束后读回 [initial]，上一次登录迟到的响应也绝不会落到下一次登录上。
 */
internal class SignInCache<T>(
    private val session: StateFlow<SessionState>,
    scope: CoroutineScope,
    private val initial: T,
    private val file: SignInSnapshotStore.SnapshotFile<T>,
) {
    private data class Entry<T>(
        val owner: ZzuClients,
        val signInId: String,
        val snapshot: Snapshot<T>? = null,
        val version: Long = 0,
        val isRestored: Boolean = false,
    )

    private val entry = MutableStateFlow<Entry<T>?>(null)
    private val versions = AtomicLong()

    val value: StateFlow<T> = combine(session, entry) { state, latest -> valueFor(state.clients, latest) }
        .stateIn(scope, SharingStarted.Eagerly, initial)

    init {
        scope.launch {
            // 存档更新会替换 SignIn 实例，只有换了登录才需要读快照。
            session.map { it as? SignIn }.distinctUntilChangedBy { it?.clients }.collectLatest { signIn ->
                entry.update { current -> current?.takeIf { it.owner === session.value.clients } }
                if (signIn != null) restore(signIn)
            }
        }
        scope.launch {
            // StateFlow 会合并来不及写的中间值，最后落盘的总是最新的一份。
            entry.filterNotNull().distinctUntilChangedBy { it.owner to it.snapshot }.collect { latest ->
                val snapshot = latest.snapshot ?: return@collect
                file.write(snapshot) {
                    session.value.clients === latest.owner
                }
            }
        }
    }

    fun nextRefreshVersion(): Long = versions.incrementAndGet()

    /** 请求在锁外并行，写入时以请求开始的顺序为准。 */
    fun update(
        owner: ZzuClients,
        refreshVersion: Long = nextRefreshVersion(),
        transform: (T) -> T,
    ) {
        val fetchedAt = Clock.System.now()
        modify(owner) { latest ->
            if (latest.version > refreshVersion) {
                latest
            } else {
                val current = if (latest.snapshot != null) latest.snapshot.value else initial
                latest.copy(
                    snapshot = Snapshot(latest.signInId, fetchedAt, transform(current)),
                    version = refreshVersion,
                )
            }
        }
    }

    /** 补充一部分数据而不算作整体核对过，例如加载另一个学期。其余同 [update]。 */
    fun amend(owner: ZzuClients, transform: (T) -> T) {
        modify(owner) { latest ->
            val current = if (latest.snapshot != null) latest.snapshot.value else initial
            latest.copy(snapshot = Snapshot(latest.signInId, latest.snapshot?.fetchedAt, transform(current)))
        }
    }

    /** 让当前数据下次自动刷新时必定更新，数据本身照常显示。 */
    fun expire() {
        val owner = session.value.clients ?: return
        val expireVersion = nextRefreshVersion()
        modify(owner) { latest ->
            if (latest.version > expireVersion) latest
            else latest.copy(snapshot = latest.snapshot?.copy(fetchedAt = null), version = expireVersion)
        }
    }

    /**
     * 当前登录的数据在 [now] 是否需要重新核对：从未核对、被 [expire] 过、超过 [maxAge] 未核对，
     * 或不满足 [isComplete]（例如缺少新添加的项目）。系统时钟被往回调过也算过期，否则会一直显得新鲜。
     *
     * 会等快照读回再判断。没有登录时没有可刷新的内容，不算过期。
     */
    suspend fun isStaleAt(now: Instant, maxAge: Duration, isComplete: (T) -> Boolean = { true }): Boolean {
        val owner = (session.value as? SignIn)?.clients ?: return false
        combine(session, entry) { state, current ->
            state.clients !== owner || (current?.owner === owner && current.isRestored)
        }.first { it }
        val snapshot = entry.value?.takeIf { it.owner === owner }?.snapshot
        return session.value.clients === owner &&
            (snapshot == null || isStaleTimestamp(snapshot.fetchedAt, now, maxAge) || !isComplete(snapshot.value))
    }

    private fun modify(owner: ZzuClients, transform: (Entry<T>) -> Entry<T>) {
        entry.update { latest ->
            val signIn = session.value as? SignIn
            if (signIn?.clients !== owner) return@update latest
            val current = latest?.takeIf { it.owner === owner } ?: Entry(owner, signIn.id)
            transform(current)
        }
    }

    private suspend fun restore(signIn: SignIn) {
        val saved = file.read(signIn.id)
        modify(signIn.clients) { latest ->
            val snapshot = latest.snapshot ?: saved?.let {
                if (latest.version == 0L) it else it.copy(fetchedAt = null)
            }
            latest.copy(snapshot = snapshot, isRestored = true)
        }
    }

    private fun valueFor(owner: ZzuClients?, latest: Entry<T>?): T {
        val snapshot = latest?.takeIf { it.owner === owner }?.snapshot
        return if (snapshot != null) snapshot.value else initial
    }
}

/** [fetchedAt] 在 [now] 是否已经过期：从未核对、超过 [maxAge]，或系统时钟被往回调过。 */
internal fun isStaleTimestamp(fetchedAt: Instant?, now: Instant, maxAge: Duration): Boolean =
    fetchedAt == null || now - fetchedAt !in Duration.ZERO..maxAge

package com.illustar0.teishucan.data

import java.util.concurrent.atomic.AtomicLong
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private data class Entry<T>(
        val owner: ZzuClients,
        val account: String,
        val snapshot: Snapshot<T>? = null,
        val version: Long = 0,
    )

    private val entry = MutableStateFlow<Entry<T>?>(null)
    private val versions = AtomicLong()
    private val publishLock = Mutex()

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
                val snapshot = latest.snapshot ?: return@collect
                file.write(snapshot) {
                    session.value.clients === latest.owner
                }
            }
        }
    }

    fun nextRefreshVersion(): Long = versions.incrementAndGet()

    /** 请求在锁外并行，写入时以请求开始的顺序为准。 */
    suspend fun update(
        owner: ZzuClients,
        refreshVersion: Long = nextRefreshVersion(),
        saveRelatedData: suspend () -> Unit = {},
        transform: (T) -> T,
    ): Unit = publishLock.withLock {
        if (session.value.clients !== owner) return@withLock
        val previous = entry.value
        if (previous?.owner === owner && previous.version > refreshVersion) return@withLock

        saveRelatedData()
        val fetchedAt = Clock.System.now()
        modify(owner) { latest ->
            if (latest.version > refreshVersion) {
                latest
            } else {
                val value = transform(latest.snapshot?.value ?: initial)
                latest.copy(snapshot = Snapshot(latest.account, fetchedAt, value), version = refreshVersion)
            }
        }
    }

    /** 补充一部分数据而不算作整体核对过，例如加载另一个学期。其余同 [update]。 */
    fun amend(owner: ZzuClients, transform: (T) -> T) {
        modify(owner) { latest ->
            val value = transform(latest.snapshot?.value ?: initial)
            latest.copy(snapshot = Snapshot(latest.account, latest.snapshot?.fetchedAt, value))
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
        val owner = session.value.clients ?: return false
        combine(session, restoredOwner) { state, restored -> state.clients !== owner || restored === owner }
            .first { it }
        val snapshot = entry.value?.takeIf { it.owner === owner }?.snapshot
        val fetchedAt = snapshot?.fetchedAt
        return snapshot == null || fetchedAt == null || now - fetchedAt !in Duration.ZERO..maxAge ||
            !isComplete(snapshot.value)
    }

    private fun modify(owner: ZzuClients, transform: (Entry<T>) -> Entry<T>) {
        entry.update { latest ->
            val signIn = session.value as? SignIn
            if (signIn?.clients !== owner) return@update latest
            val current = latest?.takeIf { it.owner === owner } ?: Entry(owner, signIn.session.account)
            transform(current)
        }
    }

    /** 让 [signIn] 从它账号的快照开始。读盘期间已有更新或登录已结束时，快照作废。 */
    private suspend fun restore(signIn: SignIn) {
        val snapshot = file.read()?.takeIf { it.account == signIn.session.account }
        if (snapshot != null) {
            entry.update { latest ->
                if (session.value.clients !== signIn.clients) return@update latest
                when {
                    latest?.owner !== signIn.clients -> Entry(signIn.clients, snapshot.account, snapshot)
                    latest.snapshot == null -> latest.copy(snapshot = snapshot.copy(fetchedAt = null))
                    else -> latest
                }
            }
        }
        restoredOwner.value = signIn.clients
    }

    private fun valueFor(owner: ZzuClients?, latest: Entry<T>?): T =
        latest?.takeIf { it.owner === owner }?.snapshot?.value ?: initial
}

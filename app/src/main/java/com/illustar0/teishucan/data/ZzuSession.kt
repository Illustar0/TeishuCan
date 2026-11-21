package com.illustar0.teishucan.data

import android.util.Log
import com.illustar0.teishucan.di.ApplicationScope
import com.illustar0.teishucan.domain.AccountState
import com.illustar0.teishucan.domain.CampusFeature
import com.illustar0.teishucan.domain.CampusRequestException
import com.illustar0.teishucan.domain.LoginResult
import com.illustar0.teishucan.domain.StudentProfile
import com.illustar0.zzu.AuthenticationErrorKind
import com.illustar0.zzu.ZzuException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlin.time.TimeSource

private const val Tag = "TeishuCan.Zzu"

/** 记录 [operation] 的开始与耗时。只有 started 没有 finished，就是卡住或失败在这一步。 */
internal suspend fun <T> logged(operation: String, block: suspend () -> T): T {
    val startedAt = TimeSource.Monotonic.markNow()
    Log.i(Tag, "$operation started")
    return block().also { Log.i(Tag, "$operation finished in ${startedAt.elapsedNow()}") }
}

private val SignInEndingFailures = setOf(
    AuthenticationErrorKind.INVALID_CREDENTIALS,
    AuthenticationErrorKind.TOKEN_EXPIRED,
    AuthenticationErrorKind.NOT_AUTHENTICATED,
    AuthenticationErrorKind.MFA_REQUIRED,
)

internal sealed interface SessionState {
    /** 这个状态持有的客户端。它的身份区分不同的登录，没有登录时为 null。 */
    val clients: ZzuClients?
        get() = null

    data object Restoring : SessionState

    data object SignedOut : SessionState

    /** 一次尚未完成的登录，可能正在等待短信验证。 */
    class LoggingIn(override val clients: ZzuClients) : SessionState
}

/** 一次已完成的登录，[session] 与磁盘上的存档相同。存档更新时实例随之替换，[clients] 不变。 */
internal class SignIn(override val clients: ZzuClients, val session: StoredSession) : SessionState {
    val id: String get() = session.signInId
}

/**
 * 账号的登录状态机，也是各功能仓库访问校园服务的唯一入口 [request]。
 *
 * 保存的会话与登录状态始终一致：已登录才有存档，登录结束即删除。因此启动时
 * 只读本机就能恢复登录，离线启动不会掉线，令牌留到第一次请求时再激活。
 *
 * 这里也是学生资料的唯一来源：资料与令牌一起存在 [SignIn.session] 中，界面从 [account] 读取。
 * 其余数据的快照 [snapshots] 同样只在登录期间存在，登录结束时一并删除。
 */
@Singleton
class ZzuSession @Inject constructor(
    private val store: EncryptedSessionStore,
    private val snapshots: SignInSnapshotStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Restoring)

    /** 串行化状态切换与存档读写，两者才能保持一致。持有期间只做本地操作，不等待网络。 */
    private val transitionLock = Mutex()

    val account: StateFlow<AccountState> = mutableState
        .map { state ->
            when (state) {
                SessionState.Restoring -> AccountState.Restoring
                SessionState.SignedOut, is SessionState.LoggingIn -> AccountState.SignedOut
                is SignIn -> AccountState.SignedIn(state.session.profile)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AccountState.Restoring)

    init {
        scope.launch {
            val restored = try {
                restoredState()
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                Log.w(Tag, "Could not restore the saved session", cause)
                SessionState.SignedOut
            }
            // 删除存档后、删除快照前进程被杀，快照就会失去主人。
            if (restored == SessionState.SignedOut) snapshots.clear()
            mutableState.value = restored
        }
    }

    private suspend fun restoredState(): SessionState {
        val saved = store.read() ?: return SessionState.SignedOut
        val clients = ZzuClients(saved.account, saved.password)
        try {
            clients.presetTokens(saved.tokens)
        } catch (cause: Throwable) {
            clients.close()
            throw cause
        }
        return SignIn(clients, saved)
    }

    internal fun <T : Any> createSnapshotCache(name: String, initial: T, serializer: KSerializer<T>) =
        SignInCache(mutableState, scope, initial, snapshots.file(name, serializer))

    suspend fun login(account: String, password: String): LoginResult {
        require(account.isNotBlank() && password.isNotEmpty())
        awaitRestored()
        return accountStep {
            val clients = ZzuClients(account, password)
            replaceState(SessionState.LoggingIn(clients))
            try {
                authenticate(clients)
            } catch (cause: Throwable) {
                end(clients)
                throw cause
            }
        }
    }

    private suspend fun authenticate(clients: ZzuClients): LoginResult = try {
        logged("CAS login") { clients.cas.login() }
        finishLogin(clients)
        LoginResult.SignedIn
    } catch (cause: ZzuException.Authentication) {
        if (cause.kind != AuthenticationErrorKind.MFA_REQUIRED) throw cause
        check(clients.cas.detectMfa().canUseSms) { "SMS verification is unavailable" }
        LoginResult.MfaRequired
    }

    /** 为返回 [LoginResult.MfaRequired] 的登录发送验证码。 */
    suspend fun sendMfaCode() {
        val clients = awaitingMfa()
        accountStep { logged("MFA code delivery") { clients.cas.sendSms() } }
    }

    /** 用 [code] 完成返回 [LoginResult.MfaRequired] 的登录。 */
    suspend fun verifyMfa(code: String) {
        val clients = awaitingMfa()
        accountStep {
            logged("MFA verification") {
                clients.cas.verifySms(code)
                clients.cas.login()
            }
            finishLogin(clients)
        }
    }

    /** 结束当前登录。SDK 的退出也只清除本地状态，所以删除存档并释放客户端就够了。 */
    suspend fun logout() {
        val ended = awaitRestored() as? SignIn ?: return
        end(ended.clients)
    }

    /**
     * 以已登录账号运行 [block]，并保证令牌可用。失败统一包装成 [CampusRequestException]，
     * 只在这里记录一次日志。
     */
    internal suspend fun <T> request(feature: CampusFeature, block: suspend ZzuClients.() -> T): T {
        val clients = (awaitRestored() as? SignIn)?.clients
            ?: throw CampusRequestException(feature, reason = null, IllegalStateException("Sign in first"))
        try {
            return logged("$feature request") {
                clients.ensureSignedIn { tokens -> updateSession(clients) { it.copy(tokens = tokens) } }
                clients.block()
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            endOnAuthenticationFailure(clients, cause)
            Log.w(Tag, "$feature request failed", cause)
            throw CampusRequestException(feature, (cause as? ZzuException)?.message, cause)
        }
    }

    internal suspend fun saveProfile(owner: ZzuClients, profile: StudentProfile) {
        updateSession(owner) { it.copy(profile = profile) }
    }

    /** 让登录各步骤的失败带上校园服务给出的原因。 */
    private inline fun <T> accountStep(step: () -> T): T = try {
        step()
    } catch (cause: ZzuException) {
        throw CampusRequestException(CampusFeature.Account, cause.message, cause)
    }

    private suspend fun finishLogin(clients: ZzuClients) {
        val profile = logged("Profile query") { clients.cas.userInfo().toStudentProfile() }
        val saved = clients.storedSession(profile)
        transition {
            check(mutableState.value.clients === clients) { "The login was abandoned" }
            store.write(saved)
            mutableState.value = SignIn(clients, saved)
        }
    }

    private suspend fun awaitRestored(): SessionState = mutableState.first { it != SessionState.Restoring }

    private fun awaitingMfa(): ZzuClients =
        checkNotNull((mutableState.value as? SessionState.LoggingIn)?.clients) { "Verification has expired" }

    /** 同步更新 [owner] 的存档与内存状态。[owner] 已结束时什么也不做，迟到的写入不会碰到下一次登录。 */
    private suspend fun updateSession(owner: ZzuClients, transform: (StoredSession) -> StoredSession) {
        transition<Unit> {
            val current = mutableState.value as? SignIn
            if (current == null || current.clients !== owner) return@transition
            val updated = transform(current.session)
            if (updated != current.session) {
                store.write(updated)
                mutableState.value = SignIn(owner, updated)
            }
        }
    }

    private suspend fun end(owner: ZzuClients) {
        replaceState(SessionState.SignedOut) { it.clients === owner }
    }

    private suspend fun endOnAuthenticationFailure(owner: ZzuClients, cause: Exception) {
        if (cause !is ZzuException.Authentication || cause.kind !in SignInEndingFailures) return
        try {
            end(owner)
        } catch (cleanupFailure: CancellationException) {
            throw cleanupFailure
        } catch (cleanupFailure: Exception) {
            cause.addSuppressed(cleanupFailure)
        }
    }

    /** 当前状态满足 [condition] 时切换到 [next]，同时删除被替换登录的存档与快照并释放它的客户端。 */
    private suspend fun replaceState(next: SessionState, condition: (SessionState) -> Boolean = { true }) {
        val replaced = transition {
            mutableState.value.takeIf(condition)?.also { current ->
                if (current is SignIn) store.clear()
                mutableState.value = next
                // 先切换状态再删快照：此后的写入都会发现登录已结束而放弃。
                if (current is SignIn) snapshots.clear()
            }
        }
        replaced?.clients?.close()
    }

    /** 在 [transitionLock] 内运行 [block]。调用方取消也要做完，否则状态会和存档不一致。 */
    private suspend fun <T> transition(block: suspend () -> T): T =
        withContext(NonCancellable) { transitionLock.withLock { block() } }
}

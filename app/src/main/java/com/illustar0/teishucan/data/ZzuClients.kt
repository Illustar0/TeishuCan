package com.illustar0.teishucan.data

import com.illustar0.teishucan.domain.StudentProfile
import com.illustar0.zzu.AuthenticationErrorKind
import com.illustar0.zzu.ZzuException
import com.illustar0.zzu.cas.CasClient
import com.illustar0.zzu.cas.Tokens
import com.illustar0.zzu.eas.EasClient
import com.illustar0.zzu.ecard.EcardClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 一个账号的全部 SDK 客户端，同生共死。每次登录都是新实例，引用相等即代表同一次登录。
 *
 * SDK 自己保证调用的并发安全，这里的锁只用来避免并发调用方重复登录或重复换新令牌，
 * 都不会跨越业务请求持有。
 */
internal class ZzuClients(private val account: String, private val password: String) : AutoCloseable {
    val cas = CasClient(account, password)
    private val easClient = EasClient(cas)
    private val ecardClient = EcardClient(cas)
    private val easLogin = Mutex()
    private val ecardLogin = Mutex()
    private val casAccess = Mutex()

    suspend fun eas(): EasClient = easLogin.withLock {
        easClient.apply { if (!isLoggedIn()) logged("EAS login") { login() } }
    }

    suspend fun ecard(): EcardClient = ecardLogin.withLock {
        ecardClient.apply { if (!isLoggedIn()) logged("Ecard login") { login() } }
    }

    /**
     * 让 CAS 会话可用：激活恢复出来的令牌，并在令牌将要过期时重新登录。SDK 把这两件事都留给了应用。
     *
     * 令牌因此变化时交给 [saveTokens]，保存完才让后面的调用方进来，新令牌不会被更旧的覆盖。
     */
    suspend fun ensureSignedIn(saveTokens: suspend (Tokens) -> Unit) {
        casAccess.withLock {
            if (!cas.isLoggedIn()) {
                logged("CAS login") { cas.login() }
                saveTokens(currentTokens())
            }
            try {
                cas.userToken()
            } catch (cause: ZzuException.Authentication) {
                if (cause.kind != AuthenticationErrorKind.TOKEN_EXPIRED) throw cause
                logged("CAS reauthentication") { cas.reauthenticate() }
                saveTokens(currentTokens())
            }
        }
    }

    suspend fun presetTokens(tokens: Tokens) {
        try {
            cas.setTokens(tokens)
        } catch (_: ZzuException.InvalidInput) {
            // 令牌已失效，ensureSignedIn 会改用密码登录。
        }
    }

    suspend fun storedSession(profile: StudentProfile) = StoredSession(account, password, currentTokens(), profile)

    private suspend fun currentTokens(): Tokens = checkNotNull(cas.tokens()) { "Missing session tokens" }

    override fun close() {
        easClient.close()
        ecardClient.close()
        cas.close()
    }
}

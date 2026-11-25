package com.illustar0.teishucan.data

import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.illustar0.teishucan.domain.StudentProfile
import com.illustar0.zzu.cas.Tokens
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlin.time.Instant

private const val SessionFileName = "zzu_session.enc"
private const val SessionKeyAlias = "teishucan.zzu.session"

/** 一次登录的全部存档。账号与密码也在其中：令牌失效时要靠它们静默重新登录。 */
@Serializable
internal data class StoredSession(
    val signInId: String,
    val account: String,
    val password: String,
    val tokens: Tokens,
    val profile: StudentProfile,
    val profileFetchedAt: Instant? = null,
) {
    override fun toString(): String = "StoredSession(<redacted>)"
}

/**
 * 把会话保存在 DataStore 中，内容用 Android KeyStore 里的 AES-256-GCM 密钥加密。
 *
 * 密钥不能导出，备份或迁移到别的设备的密文也就无法解密，所以该文件不在备份白名单里。
 */
@Singleton
class EncryptedSessionStore @Inject constructor(stores: JsonDataStoreFactory) {
    private val store = stores.createEncrypted(
        name = SessionFileName,
        defaultValue = null,
        serializer = StoredSession.serializer().nullable,
        createAead = ::sessionAead,
    )

    /** 读取保存的会话。没有保存时为 null；内容已无法解密时同样为 null，并把文件换成空存档。 */
    internal suspend fun read(): StoredSession? = store.data.first()

    internal suspend fun write(session: StoredSession) {
        store.update { session }
    }

    /** 删除保存的会话。DataStore 以原子替换写入，旧密文不会残留在文件里。 */
    suspend fun clear() {
        store.update { null }
    }
}

/** 不要求用户认证：启动时要静默恢复登录，每次解密都验证身份会让保存会话失去意义。 */
private fun sessionAead(): Aead {
    if (!AndroidKeystore.hasKey(SessionKeyAlias)) AndroidKeystore.generateNewAes256GcmKey(SessionKeyAlias)
    return AndroidKeystore.getAead(SessionKeyAlias)
}

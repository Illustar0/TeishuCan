package com.illustar0.teishucan.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Log
import androidx.core.util.writeBytes
import com.illustar0.teishucan.di.IoDispatcher
import com.illustar0.teishucan.domain.StudentProfile
import com.illustar0.zzu.cas.Tokens
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.time.Instant

private const val Tag = "TeishuCan.Session"

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

/** 把会话保存在用 Android KeyStore 密钥加密的文件中，该文件不参与备份。 */
@Singleton
class EncryptedSessionStore @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "zzu_session.enc"))

    /** 读取保存的会话。没有保存，或内容已无法解密时为 null，后者同时删除该文件。 */
    internal suspend fun read(): StoredSession? = withContext(ioDispatcher) {
        val encrypted = try {
            file.readFully()
        } catch (_: FileNotFoundException) {
            return@withContext null
        }
        try {
            decrypt(encrypted)
        } catch (_: GeneralSecurityException) {
            discard()
        } catch (_: ProviderException) {
            discard()
        } catch (_: SerializationException) {
            discard()
        }
    }

    internal suspend fun write(session: StoredSession) = withContext(ioDispatcher) {
        file.writeBytes(encrypt(session))
    }

    suspend fun clear() = withContext(ioDispatcher) {
        // 先撤销密钥。即使文件清理中断，旧凭据也无法恢复。
        KeyStore.getInstance(KeyStoreProvider).apply { load(null) }.deleteEntry(SessionKeyAlias)
        try {
            file.delete()
        } catch (cause: SecurityException) {
            Log.w(Tag,"Could not remove the revoked session file", cause)
        }
    }

    private fun discard(): StoredSession? {
        file.delete()
        return null
    }
}

private fun encrypt(session: StoredSession): ByteArray {
    val cipher = Cipher.getInstance(SessionCipher)
    cipher.init(Cipher.ENCRYPT_MODE, sessionKey())
    return cipher.iv + cipher.doFinal(StoreJson.encodeToString(session).encodeToByteArray())
}

private fun decrypt(encrypted: ByteArray): StoredSession {
    val cipher = Cipher.getInstance(SessionCipher)
    cipher.init(Cipher.DECRYPT_MODE, sessionKey(), GCMParameterSpec(SessionTagBits, encrypted, 0, SessionIvSize))
    val plaintext = cipher.doFinal(encrypted, SessionIvSize, encrypted.size - SessionIvSize)
    return StoreJson.decodeFromString(plaintext.decodeToString())
}

/** 不要求用户认证：启动时要静默恢复登录，每次解密都验证身份会让保存会话失去意义。 */
private fun sessionKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KeyStoreProvider).apply { load(null) }
    (keyStore.getKey(SessionKeyAlias, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KeyStoreProvider).apply {
        init(
            KeyGenParameterSpec.Builder(SessionKeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(SessionKeyBits)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
    }.generateKey()
}

private const val KeyStoreProvider = "AndroidKeyStore"
private const val SessionKeyAlias = "teishucan.zzu.session"
private const val SessionCipher = "AES/GCM/NoPadding"
private const val SessionKeyBits = 256
private const val SessionIvSize = 12
private const val SessionTagBits = 128

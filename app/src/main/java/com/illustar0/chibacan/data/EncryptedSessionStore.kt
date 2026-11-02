package com.illustar0.chibacan.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.illustar0.chibacan.di.IoDispatcher
import com.illustar0.chibacan.domain.StudentProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.zzu.cas.Tokens
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
import kotlinx.serialization.json.Json

/** 恢复登录所需的全部内容；[toString] 不输出凭据。 */
@Serializable
internal data class StoredSession(
    val account: String,
    val password: String,
    val tokens: Tokens,
    val profile: StudentProfile,
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
        val encrypted = encrypt(session)
        val output = file.startWrite()
        try {
            output.write(encrypted)
            file.finishWrite(output)
        } catch (cause: Throwable) {
            file.failWrite(output)
            throw cause
        }
    }

    suspend fun clear() = withContext(ioDispatcher) {
        file.delete()
        KeyStore.getInstance(KeyStoreProvider).apply { load(null) }.deleteEntry(SessionKeyAlias)
    }

    private fun discard(): StoredSession? {
        file.delete()
        return null
    }
}

private fun encrypt(session: StoredSession): ByteArray {
    val cipher = Cipher.getInstance(SessionCipher)
    cipher.init(Cipher.ENCRYPT_MODE, sessionKey())
    return cipher.iv + cipher.doFinal(Json.encodeToString(session).encodeToByteArray())
}

private fun decrypt(encrypted: ByteArray): StoredSession {
    val cipher = Cipher.getInstance(SessionCipher)
    cipher.init(Cipher.DECRYPT_MODE, sessionKey(), GCMParameterSpec(SessionTagBits, encrypted, 0, SessionIvSize))
    val plaintext = cipher.doFinal(encrypted, SessionIvSize, encrypted.size - SessionIvSize)
    return Json.decodeFromString(plaintext.decodeToString())
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
private const val SessionKeyAlias = "chibacan.zzu.session"
private const val SessionCipher = "AES/GCM/NoPadding"
private const val SessionKeyBits = 256
private const val SessionIvSize = 12
private const val SessionTagBits = 128

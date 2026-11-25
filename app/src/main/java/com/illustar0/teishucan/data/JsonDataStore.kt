package com.illustar0.teishucan.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.google.crypto.tink.Aead
import com.illustar0.teishucan.di.ApplicationScope
import com.illustar0.teishucan.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json

/** 本机存储共用的 JSON 格式：容忍旧版本写下的多余字段和未知枚举值。 */
internal val StoreJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/**
 * 本机存储的唯一入口：每个文件一个 DataStore，在应用作用域内的 IO 线程上读写，与进程同寿命。
 *
 * DataStore 要求同一个文件在进程里只有一个实例，所以只能由单例调用这里的 create 方法，每个文件一次。
 */
@Singleton
class JsonDataStoreFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope applicationScope: CoroutineScope,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(applicationScope.coroutineContext + ioDispatcher)

    /** 以明文 JSON 存在 [name] 里的值。 */
    internal fun <T> create(name: String, defaultValue: T, serializer: KSerializer<T>): JsonDataStore<T> =
        JsonDataStore(context.dataStoreFile(name), JsonSerializer(defaultValue, serializer), scope)

    /**
     * 以 [createAead] 给出的密钥加密后存在 [name] 里的值。
     *
     * 密文以 [name] 为关联数据，挪到别的文件里就解不开。密钥第一次读写时才在 IO 线程上取得。
     */
    internal fun <T> createEncrypted(
        name: String,
        defaultValue: T,
        serializer: KSerializer<T>,
        createAead: () -> Aead,
    ): JsonDataStore<T> {
        val encrypted = EncryptedSerializer(
            plain = JsonSerializer(defaultValue, serializer),
            associatedData = name.encodeToByteArray(),
            createAead = createAead,
        )
        return JsonDataStore(context.dataStoreFile(name), encrypted, scope)
    }

    /** 以明文 JSON 存在 [name] 里、跟随 [session] 的登录快照，见 [SnapshotStore]。 */
    internal fun <T> createSnapshotStore(
        name: String,
        initial: T,
        serializer: KSerializer<T>,
        session: StateFlow<SessionState>,
    ): SnapshotStore<T> {
        val store = create(name, defaultValue = null, Snapshot.serializer(serializer).nullable)
        return SnapshotStore(store, initial, session, scope)
    }
}

/**
 * 存在 [file] 里的一个值，读写都经过 DataStore。
 *
 * 内容无法解析时整个文件换回默认值，单个未知枚举值只退回该字段的默认值。文件读不出来时也读作默认值，
 * 本机存储出问题不应阻止应用启动。
 */
internal class JsonDataStore<T>(file: File, serializer: Serializer<T>, scope: CoroutineScope) {
    private val defaultValue = serializer.defaultValue

    private val store = DataStoreFactory.create(
        serializer = serializer,
        corruptionHandler = ReplaceFileCorruptionHandler { defaultValue },
        scope = scope,
        produceFile = { file },
    )

    val data: Flow<T> = store.data.catch { cause ->
        if (cause is IOException) emit(defaultValue) else throw cause
    }

    /** 以 [transform] 原子地更新保存的值。写入失败时抛出 [IOException]，保存的值保持原样。 */
    suspend fun update(transform: (T) -> T): T = store.updateData { transform(it) }
}

/** 把 DataStore 的内容存成 [StoreJson]。无法解析时抛出 [CorruptionException]，交给 DataStore 的损坏处理。 */
internal class JsonSerializer<T>(
    override val defaultValue: T,
    private val serializer: KSerializer<T>,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        StoreJson.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (cause: IllegalArgumentException) {
        // 包括 JSON 本身损坏时的 SerializationException，以及日期等字段解析失败。
        throw CorruptionException("Cannot decode the stored JSON", cause)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(StoreJson.encodeToString(serializer, t).encodeToByteArray())
    }
}

/**
 * 用 AEAD 加密 [plain] 写下的内容，[associatedData] 把密文绑定到它的用途上。
 *
 * 密文无法解密时抛出 [CorruptionException]，DataStore 随即把文件换回默认值，损坏或换过密钥的文件就此自愈。
 * 取不到密钥本身不算损坏，原样抛出，文件留待下次再试。
 */
internal class EncryptedSerializer<T>(
    private val plain: Serializer<T>,
    private val associatedData: ByteArray,
    createAead: () -> Aead,
) : Serializer<T> {
    private val aead by lazy(createAead)

    override val defaultValue: T
        get() = plain.defaultValue

    override suspend fun readFrom(input: InputStream): T {
        // 先在 decrypt 之外取得密钥，取不到时的异常不会被当成密文损坏。
        val plaintext = decrypt(aead, input.readBytes())
        return plain.readFrom(plaintext.inputStream())
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        val plaintext = ByteArrayOutputStream().also { plain.writeTo(t, it) }.toByteArray()
        output.write(aead.encrypt(plaintext, associatedData))
    }

    private fun decrypt(key: Aead, ciphertext: ByteArray): ByteArray = try {
        key.decrypt(ciphertext, associatedData)
    } catch (cause: GeneralSecurityException) {
        throw CorruptionException("Cannot decrypt the stored data", cause)
    } catch (cause: ProviderException) {
        // Android KeyStore 的硬件实现以 ProviderException 报告解密失败。
        throw CorruptionException("Cannot decrypt the stored data", cause)
    }
}

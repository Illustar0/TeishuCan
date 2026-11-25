package com.illustar0.teishucan.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 本机存储共用的 JSON 格式：容忍旧版本写下的多余字段和未知枚举值。 */
internal val StoreJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/**
 * 以 JSON 存在 [file] 里的一个值，读写都经过 DataStore。
 *
 * 内容无法解析时整个文件换回 [defaultValue]，单个未知枚举值只退回该字段的默认值。文件读不出来时也读作
 * [defaultValue]，本机存储出问题不应阻止应用启动。同一个文件在进程里只能有一个实例，所以只由单例持有。
 */
internal class JsonDataStore<T>(
    file: File,
    private val defaultValue: T,
    serializer: KSerializer<T>,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private val store = DataStoreFactory.create(
        serializer = JsonSerializer(defaultValue, serializer),
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
    } catch (cause: SerializationException) {
        throw CorruptionException("Cannot decode the stored JSON", cause)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(StoreJson.encodeToString(serializer, t).encodeToByteArray())
    }
}

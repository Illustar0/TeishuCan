package com.illustar0.chibacan.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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

/** 把 DataStore 的内容存成 JSON。内容损坏时读作 [defaultValue]，单个未知枚举值退回该字段的默认值。 */
class JsonSerializer<T>(
    override val defaultValue: T,
    private val serializer: KSerializer<T>,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        StoreJson.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (_: SerializationException) {
        defaultValue
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(StoreJson.encodeToString(serializer, t).encodeToByteArray())
    }
}

/** 读取失败时给出 [fallback]，存储文件不可读不应阻止应用启动。 */
fun <T> DataStore<T>.dataOr(fallback: T): Flow<T> = data.catch { cause ->
    if (cause is IOException) emit(fallback) else throw cause
}

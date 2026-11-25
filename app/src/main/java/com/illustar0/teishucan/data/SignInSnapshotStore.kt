package com.illustar0.teishucan.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.core.util.writeBytes
import com.illustar0.teishucan.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant

private const val Tag = "TeishuCan.Snapshot"

/** [signInId] 对应的登录期间查询到的 [value]，[fetchedAt] 是最近一次与服务端核对的时间。 */
@Serializable
internal data class Snapshot<T>(val signInId: String, val fetchedAt: Instant?, val value: T)

/**
 * 登录期间查询到的数据在本机的快照，冷启动恢复登录后先显示它，再等刷新。
 *
 * 与 [EncryptedSessionStore] 的存档同生同灭：登录结束时 [ZzuSession] 调用 [clear]。
 * 快照不参与备份，因为会话也不备份，恢复到新设备的快照没有登录可以对应。
 */
@Singleton
class SignInSnapshotStore @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val directory = File(context.noBackupFilesDir, "sign_in_snapshots")

    private val lock = Mutex()

    internal fun <T> file(name: String, serializer: KSerializer<T>) =
        SnapshotFile(name, Snapshot.serializer(serializer))

    suspend fun clear(): Unit = withContext(ioDispatcher) {
        lock.withLock {
            try {
                if (!directory.deleteRecursively()) Log.w(Tag, "Could not clear sign-in snapshots")
            } catch (cause: SecurityException) {
                Log.w(Tag, "Could not clear sign-in snapshots", cause)
            }
        }
    }

    internal inner class SnapshotFile<T>(name: String, private val serializer: KSerializer<Snapshot<T>>) {
        private val file = AtomicFile(File(directory, name))

        /** 只有属于这次登录的快照才可恢复。 */
        suspend fun read(signInId: String): Snapshot<T>? = withContext(ioDispatcher) {
            try {
                val content = lock.withLock { file.readFully() }
                StoreJson.decodeFromString(serializer, content.decodeToString())
                    .takeIf { it.signInId == signInId }
            } catch (_: FileNotFoundException) {
                null
            } catch (cause: IOException) {
                Log.w(Tag, "Could not read the snapshot", cause)
                null
            } catch (cause: SecurityException) {
                Log.w(Tag, "Could not read the snapshot", cause)
                null
            } catch (_: IllegalArgumentException) {
                // 包括 JSON 本身损坏时的 SerializationException，以及日期等字段解析失败。
                null
            }
        }

        /** 写入 [snapshot]。[isCurrent] 在持锁后判断，为 false 说明那次登录已经结束，放弃写入。 */
        suspend fun write(snapshot: Snapshot<T>, isCurrent: () -> Boolean): Unit = withContext(ioDispatcher) {
            val content = StoreJson.encodeToString(serializer, snapshot).encodeToByteArray()
            lock.withLock {
                if (!isCurrent()) return@withLock
                try {
                    directory.mkdirs()
                    file.writeBytes(content)
                } catch (cause: IOException) {
                    Log.w(Tag, "Could not write the snapshot", cause)
                }
            }
        }
    }
}

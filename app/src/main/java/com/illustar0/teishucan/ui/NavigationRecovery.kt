package com.illustar0.teishucan.ui

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import androidx.core.util.writeBytes
import com.illustar0.teishucan.data.StoreJson
import com.illustar0.teishucan.di.IoDispatcher
import com.illustar0.teishucan.platform.PlatformCapabilities
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
import kotlinx.serialization.Serializable

private const val Tag = "NavigationRecovery"

/** MemoryLimiter 结束进程时，退出原因描述里带着的标记。 */
private const val MemoryLimiterExitMarker = "MemoryLimiter:AnonSwap"

@Serializable
internal data class NavigationSnapshot(
    val routes: List<Route> = listOf(Route.Main),
    val destination: AppDestination = AppDestination.Home,
) {
    init {
        require(routes.firstOrNull() == Route.Main && routes.distinct().size == routes.size)
    }
}

@Singleton
internal class NavigationRecovery @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    @Serializable
    private data class Record(val pid: Int, val navigation: NavigationSnapshot, val isKillRequested: Boolean = false)

    private val file by lazy { AtomicFile(File(context.noBackupFilesDir, "navigation-recovery.json")) }
    private val lock = Mutex()
    private val pid = Process.myPid()
    private var isKillRequested = false

    @Volatile
    private var latestNavigation: NavigationSnapshot? = null

    suspend fun record(navigation: NavigationSnapshot) {
        latestNavigation = navigation
        if (PlatformCapabilities.isMemoryLimiterPresent) {
            withContext(ioDispatcher) {
                lock.withLock { write(Record(pid, navigation, isKillRequested)) }
            }
        }
    }

    suspend fun saveBeforeKill(): Boolean = withContext(ioDispatcher) {
        lock.withLock {
            val navigation = latestNavigation ?: return@withLock true
            write(Record(pid, navigation, isKillRequested = true)).also { saved ->
                if (saved) isKillRequested = true
            }
        }
    }

    suspend fun restore(): NavigationSnapshot = withContext(ioDispatcher) {
        lock.withLock {
            val record = read() ?: return@withLock NavigationSnapshot()
            if (record.pid == pid) return@withLock NavigationSnapshot()
            file.delete()
            if (record.isKillRequested || wasMemoryLimited(record.pid)) record.navigation else NavigationSnapshot()
        }
    }

    private fun wasMemoryLimited(previousPid: Int): Boolean {
        if (!PlatformCapabilities.isMemoryLimiterPresent) return false
        val manager = context.getSystemService(ActivityManager::class.java)
        val exit = manager?.getHistoricalProcessExitReasons(context.packageName, previousPid, 1)?.firstOrNull()
        return exit?.reason == ApplicationExitInfo.REASON_OTHER &&
            exit.description.orEmpty().contains(MemoryLimiterExitMarker)
    }

    private fun read(): Record? = try {
        StoreJson.decodeFromString<Record>(file.readFully().decodeToString())
    } catch (_: FileNotFoundException) {
        null
    } catch (cause: IOException) {
        Log.w(Tag, "Could not read saved navigation", cause)
        null
    } catch (cause: IllegalArgumentException) {
        Log.w(Tag, "Invalid saved navigation", cause)
        file.delete()
        null
    }

    private fun write(record: Record): Boolean = try {
        val content = StoreJson.encodeToString(record).encodeToByteArray()
        file.writeBytes(content)
        file.readFully().contentEquals(content)
    } catch (cause: IOException) {
        Log.w(Tag, "Could not save navigation", cause)
        false
    }
}

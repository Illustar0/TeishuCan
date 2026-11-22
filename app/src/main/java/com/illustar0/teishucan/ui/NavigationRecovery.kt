package com.illustar0.teishucan.ui

import android.content.Context
import android.os.Process
import android.util.AtomicFile
import android.util.Log
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val Tag = "NavigationRecovery"

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

    @Volatile
    private var latestNavigation: NavigationSnapshot? = null

    fun record(navigation: NavigationSnapshot) {
        latestNavigation = navigation
    }

    suspend fun saveBeforeKill(): Boolean = withContext(ioDispatcher) {
        lock.withLock {
            val navigation = latestNavigation ?: return@withLock true
            write(Record(pid, navigation, isKillRequested = true))
        }
    }

    suspend fun restore(): NavigationSnapshot = withContext(ioDispatcher) {
        lock.withLock {
            val record = read() ?: return@withLock NavigationSnapshot()
            if (record.pid == pid) return@withLock NavigationSnapshot()
            file.delete()
            if (record.isKillRequested) record.navigation else NavigationSnapshot()
        }
    }

    private fun read(): Record? = try {
        Json.decodeFromString<Record>(file.readFully().decodeToString())
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
        val content = Json.encodeToString(record).encodeToByteArray()
        val output = file.startWrite()
        try {
            output.write(content)
            file.finishWrite(output)
        } catch (cause: Throwable) {
            file.failWrite(output)
            throw cause
        }
        file.readFully().contentEquals(content)
    } catch (cause: IOException) {
        Log.w(Tag, "Could not save navigation", cause)
        false
    }
}

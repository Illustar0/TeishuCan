package com.illustar0.teishucan.memory

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BadParcelableException
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.illustar0.teishucan.di.ApplicationScope
import com.illustar0.teishucan.ui.NavigationRecovery
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

private const val Tag = "FairMemory"
private const val TrimAction = "itgsa.intent.action.TRIM"
private const val KillAction = "itgsa.intent.action.KILL"
private const val PhysicalMemory = 1000
private const val JavaHeap = 2000
private const val Handled = 0
private const val Failed = 1
private val SaveTimeout = 2.5.seconds

@Singleton
internal class FairMemory @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val navigationRecovery: NavigationRecovery,
) {
    private val releaseCallbacks = mutableSetOf<() -> Unit>()

    var areHeavyEffectsEnabled by mutableStateOf(true)
        private set

    @MainThread
    fun trim(): Boolean {
        areHeavyEffectsEnabled = false
        var released = true
        releaseCallbacks.forEach { release ->
            try {
                release()
            } catch (cause: IllegalStateException) {
                released = false
                Log.w(Tag, "Could not release a graphics layer", cause)
            }
        }
        return released
    }

    @MainThread
    fun addReleaseCallback(release: () -> Unit) {
        releaseCallbacks.add(release)
    }

    @MainThread
    fun removeReleaseCallback(release: () -> Unit) {
        releaseCallbacks.remove(release)
    }

    /**
     * 开始响应内存回收：进程进入后台、系统回收内存或厂商下发回收通知时 [trim]，回到前台时恢复重特效。
     *
     * 在 [android.app.Application.onCreate] 中调用一次。
     */
    fun register() {
        context.registerComponentCallbacks(trimCallbacks)
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
        val filter = IntentFilter(TrimAction).apply { addAction(KillAction) }
        // 回调 Binder 来自广播发送方，只接收系统或持有平台特权权限的发送方。
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            Manifest.permission.DUMP,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private val trimCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) trim()
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = Unit
    }

    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            areHeavyEffectsEnabled = true
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != TrimAction && action != KillAction) return
            val notice = try {
                readNotice(intent)
            } catch (cause: BadParcelableException) {
                Log.w(Tag, "Invalid memory notification", cause)
                null
            } ?: return

            val trimmed = trim()
            if (action == TrimAction) {
                notice.reply(if (trimmed) Handled else Failed)
                return
            }

            val pending = goAsync()
            applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    val save = async { navigationRecovery.saveBeforeKill() }
                    val saved = withTimeoutOrNull(SaveTimeout) { save.await() } == true
                    save.cancel()
                    notice.reply(if (saved) Handled else Failed)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}

private fun readNotice(intent: Intent): MemoryNotice? {
    val common = intent.getBundleExtra("common") ?: return null
    val callback = common.getBinder("callback") ?: return null
    val type = common.getInt("notifyType")
    if (type != PhysicalMemory && type != JavaHeap) return null
    if (!common.containsKey("notifyId")) return null
    return MemoryNotice(type, common.getInt("notifyId"), callback)
}

private class MemoryNotice(private val type: Int, private val id: Int, private val callback: IBinder) {
    fun reply(result: Int) {
        val data = Parcel.obtain()
        try {
            data.writeInt(type)
            data.writeInt(id)
            data.writeInt(result)
            data.writeBundle(null)
            callback.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
        } catch (cause: RemoteException) {
            Log.w(Tag, "Could not acknowledge memory notification", cause)
        } finally {
            data.recycle()
        }
    }
}

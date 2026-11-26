package com.illustar0.teishucan

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.illustar0.teishucan.data.CampusRefresher
import com.illustar0.teishucan.reminder.ClassReminders
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.flow.map

@HiltAndroidApp
class TeishuCanApplication : Application() {
    @Inject
    lateinit var refresher: CampusRefresher

    @Inject
    lateinit var classReminders: ClassReminders

    override fun onCreate() {
        super.onCreate()
        classReminders.syncOnChanges()
        // 监听进程而不是页面生命周期，切换分页、进出子页面和转屏都不算进入前台。
        refresher.refreshOnForeground(
            ProcessLifecycleOwner.get().lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) },
        )
    }
}

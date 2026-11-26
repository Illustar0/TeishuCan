package com.illustar0.teishucan.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.illustar0.teishucan.platform.PlatformCapabilities

private const val Tag = "TeishuCan.ClassReminder"

/** 上课提醒要用、用户可以在系统设置里关掉的能力，按影响从大到小排列。 */
enum class ClassReminderAccess {
    /** 没有它就什么也看不到。 */
    Notifications,

    /** 没有它实时动态只显示为普通通知，不进状态栏。 */
    LiveUpdates,

    /** 没有它闹钟不精确，换阶段可能推迟几分钟。 */
    ExactAlarms,
}

/** 此刻用户没有允许的 [ClassReminderAccess]。系统里没有这项设置的不算。 */
fun missingClassReminderAccess(context: Context): List<ClassReminderAccess> {
    val notifications = NotificationManagerCompat.from(context)
    val alarmManager = checkNotNull(context.getSystemService<AlarmManager>())
    return buildList {
        if (!notifications.areNotificationsEnabled() || notifications.isClassReminderChannelMuted()) {
            add(ClassReminderAccess.Notifications)
        }
        if (PlatformCapabilities.isLiveUpdateSupported && !notifications.canPostPromotedNotifications()) {
            add(ClassReminderAccess.LiveUpdates)
        }
        if (!AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) add(ClassReminderAccess.ExactAlarms)
    }
}

/**
 * 打开让用户允许这一项的系统设置页。系统定制得没有这一页时，退而打开本应用的通知设置。
 *
 * [context] 要能启动 Activity，比如当前的页面。
 */
fun ClassReminderAccess.openSettings(context: Context) {
    try {
        context.startActivity(settingsIntent(context))
    } catch (cause: ActivityNotFoundException) {
        Log.w(Tag, "No settings page for $this", cause)
        context.startActivity(appNotificationSettingsIntent(context))
    }
}

// 这些常量会内联进代码，只有系统支持这项设置时才会缺少它、走到这里。
@SuppressLint("InlinedApi")
private fun ClassReminderAccess.settingsIntent(context: Context): Intent = when (this) {
    ClassReminderAccess.Notifications -> notificationSettingsIntent(context)
    ClassReminderAccess.LiveUpdates ->
        Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    ClassReminderAccess.ExactAlarms ->
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
}

private fun notificationSettingsIntent(context: Context): Intent {
    val notifications = NotificationManagerCompat.from(context)
    if (notifications.areNotificationsEnabled() && notifications.isClassReminderChannelMuted()) {
        return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, ClassReminderChannelId)
    }
    return appNotificationSettingsIntent(context)
}

private fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun NotificationManagerCompat.isClassReminderChannelMuted(): Boolean =
    getNotificationChannel(ClassReminderChannelId)?.importance == NotificationManagerCompat.IMPORTANCE_NONE

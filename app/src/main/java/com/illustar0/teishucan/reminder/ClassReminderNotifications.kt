package com.illustar0.teishucan.reminder

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.illustar0.teishucan.MainActivity
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.ClassOccurrenceId
import com.illustar0.teishucan.domain.ClassReminder
import com.illustar0.teishucan.domain.ClassReminderStage
import com.illustar0.teishucan.platform.PlatformCapabilities
import com.illustar0.teishucan.ui.component.classTimeSkeleton
import com.illustar0.teishucan.ui.component.localizedFormatter
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlinx.datetime.toJavaLocalTime

private const val ChannelId = "class_reminders"

/** 每次课的通知以 [tag] 区分，id 都相同。 */
private const val NotificationId = 1

/**
 * 上课提醒在通知栏里的样子。实时动态按 Live Update 的要求构建：常驻、请求提升、标准样式、有标题、不着色，
 * 渠道重要性高于 MIN。
 */
internal class ClassReminderNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /**
     * 让本渠道正在显示的通知恰好是 [reminders]：更新或补发其中每一条，撤下其余的。[now] 用来算出下课前还剩多久。
     *
     * 用户没允许通知时只撤不发。
     */
    fun show(reminders: List<ClassReminder>, now: Instant) {
        val tags = reminders.mapTo(HashSet()) { it.occurrence.id.tag }
        manager.activeNotifications
            .filter { it.notification.channelId == ChannelId && it.tag !in tags }
            .forEach { manager.cancel(it.tag, it.id) }
        if (reminders.isEmpty()) return
        if (PlatformCapabilities.isNotificationPermissionRequired &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // 每次都重建渠道，切换语言后渠道名也跟着更新。
        manager.createNotificationChannel(channel())
        val timeFormatter = localizedFormatter(classTimeSkeleton(context), context.resources.configuration.locales[0])
        for (reminder in reminders) {
            manager.notify(reminder.occurrence.id.tag, NotificationId, build(reminder, now, timeFormatter))
        }
    }

    private fun channel(): NotificationChannelCompat =
        NotificationChannelCompat.Builder(ChannelId, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.class_reminder_channel))
            .setDescription(context.getString(R.string.class_reminder_channel_description))
            .build()

    private fun build(reminder: ClassReminder, now: Instant, timeFormatter: DateTimeFormatter): Notification {
        val occurrence = reminder.occurrence
        val entry = occurrence.entry
        val isLiveUpdate = reminder.stage != ClassReminderStage.Started
        val status = when (reminder.stage) {
            ClassReminderStage.Upcoming -> {
                val startTime = timeFormatter.format(entry.startTime.toJavaLocalTime())
                context.getString(R.string.class_reminder_upcoming, startTime)
            }
            ClassReminderStage.Imminent -> context.getString(R.string.class_reminder_imminent)
            ClassReminderStage.Started -> context.getString(R.string.class_reminder_started)
        }
        val location = entry.location?.takeIf(String::isNotBlank)
        val text = location?.let { context.getString(R.string.class_reminder_status_at, status, it) } ?: status
        return NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_lucide_calendar_clock)
            .setContentTitle(entry.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setWhen(occurrence.startsAt.toEpochMilliseconds())
            .setShowWhen(true)
            // 实时动态期间倒数到上课，状态栏胶囊里也显示这个倒计时，不必定时更新通知。
            .setUsesChronometer(isLiveUpdate)
            .setChronometerCountDown(isLiveUpdate)
            .setOngoing(isLiveUpdate)
            .setRequestPromotedOngoing(isLiveUpdate)
            // 只在第一次出现时提示，之后换阶段、补发都静默更新。
            .setOnlyAlertOnce(true)
            // 闹钟迟到或没能触发时，通知也不会留到下课以后；至少留 1 毫秒，避免 0 被系统当成不设超时。
            .setTimeoutAfter((occurrence.endsAt - now).inWholeMilliseconds.coerceAtLeast(1))
            .setContentIntent(openAppIntent())
            .setDeleteIntent(ClassReminderReceiver.dismissIntent(context, occurrence.id))
            .build()
    }

    /** 像从桌面点开一样回到应用：已有任务时切回去，不叠一个新的页面。 */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}

/** 这次课在通知与 PendingIntent 里的标识。 */
internal val ClassOccurrenceId.tag: String
    get() = "$classId@$startsAt"

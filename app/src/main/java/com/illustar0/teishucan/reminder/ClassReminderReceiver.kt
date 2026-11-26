package com.illustar0.teishucan.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.illustar0.teishucan.di.ApplicationScope
import com.illustar0.teishucan.domain.ClassOccurrenceId
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

private const val ActionSync = "com.illustar0.teishucan.action.SYNC_CLASS_REMINDERS"
private const val ActionDismiss = "com.illustar0.teishucan.action.DISMISS_CLASS_REMINDER"
private const val ExtraClassId = "classId"
private const val ExtraStartsAt = "startsAt"

/**
 * 应用进程不在时替 [ClassReminders] 接住时间的推移：闹钟到点、开机、应用更新、用户调了时钟或改了精确闹钟权限时
 * 重新对齐提醒；用户划掉提醒时记下那次课，不再为它提醒。
 */
@AndroidEntryPoint
class ClassReminderReceiver : BroadcastReceiver() {
    @Inject
    lateinit var reminders: ClassReminders

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val dismissedId = if (intent.action == ActionDismiss) intent.occurrenceId() else null
        val pendingResult = goAsync()
        scope.launch {
            try {
                dismissedId?.let { reminders.dismiss(it) }
                reminders.sync()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /** 唤起对齐的闹钟。只有这一个，重新设定时替换掉前一个。 */
        internal fun syncIntent(context: Context): PendingIntent {
            val intent = Intent(context, ClassReminderReceiver::class.java).setAction(ActionSync)
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }

        /** 用户划掉 [id] 这次课的提醒时发出。 */
        internal fun dismissIntent(context: Context, id: ClassOccurrenceId): PendingIntent {
            val intent = Intent(context, ClassReminderReceiver::class.java)
                .setAction(ActionDismiss)
                // 每次课一个 PendingIntent，extras 不参与区分。
                .setIdentifier(id.tag)
                .putExtra(ExtraClassId, id.classId)
                .putExtra(ExtraStartsAt, id.startsAt.toString())
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

/** 读出 [ClassReminderReceiver.dismissIntent] 记下的那次课。这个 Intent 只由应用自己发出，不会缺少内容。 */
private fun Intent.occurrenceId(): ClassOccurrenceId {
    val startsAt = checkNotNull(getStringExtra(ExtraStartsAt)) { "Missing $ExtraStartsAt" }
    check(hasExtra(ExtraClassId)) { "Missing $ExtraClassId" }
    return ClassOccurrenceId(getLongExtra(ExtraClassId, 0), LocalDateTime.parse(startsAt))
}

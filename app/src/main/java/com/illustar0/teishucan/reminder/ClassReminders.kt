package com.illustar0.teishucan.reminder

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.getSystemService
import com.illustar0.teishucan.data.CampusZone
import com.illustar0.teishucan.data.JsonDataStoreFactory
import com.illustar0.teishucan.data.TimetableRepository
import com.illustar0.teishucan.di.ApplicationScope
import com.illustar0.teishucan.domain.ClassOccurrence
import com.illustar0.teishucan.domain.ClassOccurrenceId
import com.illustar0.teishucan.domain.ClassReminderSchedule
import com.illustar0.teishucan.domain.classOccurrences
import com.illustar0.teishucan.domain.remindersAt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

private const val Tag = "TeishuCan.ClassReminder"

/** 上课提醒的开关，以及用户划掉了提醒、不再为之提醒的那几次课 [dismissed]。 */
@Serializable
internal data class SavedClassReminders(
    val isEnabled: Boolean = false,
    val dismissed: Set<ClassOccurrenceId> = emptySet(),
)

/**
 * 上课提醒：上课前 30 分钟起显示实时动态，前 15 分钟进入在路上、即将上课的阶段，上课时实时动态结束，
 * 转为一条普通通知，下课时消失。用户在设置里打开后才提醒，划掉的那次课不再提醒。
 *
 * 通知要跟上课表、置顶选择和时间的推移。[sync] 负责对齐，并把闹钟定在下次需要对齐的时刻：
 * 应用进程里由 [syncOnChanges] 在数据变化时调用，进程不在时由 [ClassReminderReceiver] 在闹钟和系统广播到来时调用。
 */
@Singleton
class ClassReminders @Inject constructor(
    @ApplicationContext private val context: Context,
    timetable: TimetableRepository,
    stores: JsonDataStoreFactory,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val store = stores.create(
        name = "class_reminders.json",
        defaultValue = SavedClassReminders(),
        serializer = SavedClassReminders.serializer(),
    )
    private val notifications = ClassReminderNotifications(context)

    /** 串行化对齐：数据变化与闹钟可能同时到来，后一次要看到前一次发出的通知。 */
    private val syncLock = Mutex()

    private val inputs: Flow<SyncInputs> = combine(store.data, timetable.restoredSchedule) { saved, schedule ->
        val (terms, conflictChoices) = schedule
        SyncInputs(saved, terms.classOccurrences(conflictChoices, CampusZone))
    }.distinctUntilChanged()

    val isEnabled: Flow<Boolean> = store.data.map { it.isEnabled }.distinctUntilChanged()

    suspend fun setEnabled(isEnabled: Boolean) {
        update { it.copy(isEnabled = isEnabled) }
    }

    /** 不再为 [id] 这次课提醒。顺带忘掉今天以前的课，记录不会越积越多。 */
    suspend fun dismiss(id: ClassOccurrenceId) {
        val today = Clock.System.now().toLocalDateTime(CampusZone).date
        update { saved ->
            val upcoming = saved.dismissed.filterTo(HashSet()) { it.startsAt.date >= today }
            saved.copy(dismissed = upcoming + id)
        }
    }

    /** 在应用进程的生命周期内，每当开关、课表或置顶选择变化就对齐一次。进程启动时调用一次。 */
    fun syncOnChanges() {
        scope.launch { inputs.collect { sync(it) } }
    }

    /** 按此刻的时间与数据对齐通知，并把闹钟定在下次需要对齐的时刻。会先等会话恢复。 */
    suspend fun sync() {
        sync(inputs.first())
    }

    private suspend fun sync(inputs: SyncInputs) = syncLock.withLock {
        val now = Clock.System.now()
        val schedule = if (inputs.saved.isEnabled) {
            inputs.occurrences.filterNot { it.id in inputs.saved.dismissed }.remindersAt(now)
        } else {
            ClassReminderSchedule.None
        }
        notifications.show(schedule.reminders, now)
        scheduleSync(schedule.nextChangeAt)
    }

    /**
     * 在 [at] 唤起 [ClassReminderReceiver] 再对齐一次，[at] 为 null 时取消。同一时间只有一个闹钟。
     *
     * 用户没允许精确闹钟时退而用非精确的，系统可能把它推迟几分钟。
     */
    private fun scheduleSync(at: Instant?) {
        val alarmManager = checkNotNull(context.getSystemService<AlarmManager>())
        val operation = ClassReminderReceiver.syncIntent(context)
        if (at == null) {
            alarmManager.cancel(operation)
            return
        }
        val type = AlarmManager.RTC_WAKEUP
        val triggerAtMillis = at.toEpochMilliseconds()
        if (AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) {
            AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, type, triggerAtMillis, operation)
        } else {
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, type, triggerAtMillis, operation)
        }
    }

    /** 写入失败时保持原样：错过一次设置不值得让应用崩溃。 */
    private suspend fun update(transform: (SavedClassReminders) -> SavedClassReminders) {
        try {
            store.update(transform)
        } catch (cause: IOException) {
            Log.w(Tag, "Could not save the class reminder settings", cause)
        }
    }

    private data class SyncInputs(val saved: SavedClassReminders, val occurrences: List<ClassOccurrence>)
}

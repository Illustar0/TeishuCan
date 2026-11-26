package com.illustar0.teishucan.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable

/** 上课前多久开始以实时动态提醒。 */
val ClassReminderLead: Duration = 30.minutes

/** 上课前多久进入在路上、即将上课的阶段：这时学生多半已经出发去教室。 */
val ClassImminentLead: Duration = 15.minutes

/** 教学班 [classId] 在 [startsAt]（校园时区）开始的那一次课。 */
@Serializable
data class ClassOccurrenceId(val classId: Long, val startsAt: LocalDateTime)

/** [entry] 这一次课实际的开始时刻 [startsAt] 与结束时刻 [endsAt]。 */
data class ClassOccurrence(val entry: TimetableEntry, val startsAt: Instant, val endsAt: Instant) {
    val id: ClassOccurrenceId
        get() = ClassOccurrenceId(entry.classId, entry.startsAt)
}

/** 一次课的提醒所处的阶段，按时间先后排列。 */
enum class ClassReminderStage {
    /** 上课前 30 到 15 分钟：实时动态开始，提醒快要上课了。 */
    Upcoming,

    /** 上课前 15 分钟内：在路上、即将上课，实时动态继续显示。 */
    Imminent,

    /** 已经上课：实时动态结束，转为一条普通通知，下课时消失。 */
    Started,
}

data class ClassReminder(val occurrence: ClassOccurrence, val stage: ClassReminderStage)

/**
 * 某一时刻要显示的 [reminders]，以及之后最早有提醒出现、换阶段或消失的时刻 [nextChangeAt]。
 * 之后再也没有课时 [nextChangeAt] 为 null。
 */
data class ClassReminderSchedule(val reminders: List<ClassReminder>, val nextChangeAt: Instant?) {
    companion object {
        val None = ClassReminderSchedule(emptyList(), nextChangeAt = null)
    }
}

/**
 * 这些学期里每一次已知的课，课程时刻按 [zone] 换算。
 *
 * 几门课同时开始时只留一门：用户在 [conflictChoices] 里置顶的那门，没有置顶就取教务数据里排在前面的那门，
 * 与首页的下一节课一致。开始时刻不同的冲突课程各自保留。
 */
fun List<TimetableTerm>.classOccurrences(
    conflictChoices: List<ConflictChoice>,
    zone: TimeZone,
): List<ClassOccurrence> =
    flatMap { term -> term.weeks.flatMap { it.entries } }
        .groupBy { it.startsAt }
        .map { (_, sameStart) -> conflictChoices.frontIn(sameStart) ?: sameStart.first() }
        .map { ClassOccurrence(it, it.startsAt.toInstant(zone), it.endsAt.toInstant(zone)) }

/** 这些课在 [now] 要显示的提醒。一次课从上课前 30 分钟提醒到下课，几次课的提醒可以同时显示。 */
fun List<ClassOccurrence>.remindersAt(now: Instant): ClassReminderSchedule {
    val reminders = mapNotNull { occurrence ->
        occurrence.stageAt(now)?.let { stage -> ClassReminder(occurrence, stage) }
    }
    val nextChangeAt = asSequence()
        .flatMap { it.stageBoundaries }
        .filter { it > now }
        .minOrNull()
    return ClassReminderSchedule(reminders, nextChangeAt)
}

private fun ClassOccurrence.stageAt(now: Instant): ClassReminderStage? = when {
    now < startsAt - ClassReminderLead -> null
    now < startsAt - ClassImminentLead -> ClassReminderStage.Upcoming
    now < startsAt -> ClassReminderStage.Imminent
    now < endsAt -> ClassReminderStage.Started
    else -> null
}

/** 提醒出现、每次换阶段和消失的时刻，与 [stageAt] 的分界一一对应。 */
private val ClassOccurrence.stageBoundaries: Sequence<Instant>
    get() = sequenceOf(startsAt - ClassReminderLead, startsAt - ClassImminentLead, startsAt, endsAt)

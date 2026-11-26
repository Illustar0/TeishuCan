package com.illustar0.teishucan.domain

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassReminderTest {
    private val zone = TimeZone.of("Asia/Shanghai")
    private val wednesday = LocalDate(2026, 11, 25)

    private fun entry(classId: Long, start: LocalTime, end: LocalTime) = TimetableEntry(
        classId = classId,
        day = DayOfWeek.WEDNESDAY,
        date = wednesday,
        startPeriod = 1,
        startTime = start,
        endTime = end,
        title = "Course $classId",
        teacher = "",
    )

    private fun term(vararg entries: TimetableEntry) = TimetableTerm(
        id = "1",
        schoolYear = "2026-2027",
        number = "1",
        startDate = wednesday,
        endDate = wednesday.plus(1, DateTimeUnit.WEEK),
        weeks = listOf(TimetableWeek(number = 1, dates = listOf(wednesday), entries = entries.toList())),
        isLoaded = true,
    )

    private fun at(hour: Int, minute: Int): Instant = wednesday.atTime(hour, minute).toInstant(zone)

    private val morning = entry(classId = 1, start = LocalTime(8, 0), end = LocalTime(9, 40))
    private val lateMorning = entry(classId = 2, start = LocalTime(10, 0), end = LocalTime(11, 40))

    private fun occurrencesOf(vararg entries: TimetableEntry): List<ClassOccurrence> =
        listOf(term(*entries)).classOccurrences(emptyList(), zone)

    private fun stagesAt(now: Instant, vararg entries: TimetableEntry): List<ClassReminderStage> =
        occurrencesOf(*entries).remindersAt(now).reminders.map { it.stage }

    @Test
    fun classOccurrences_convertsCampusTimesWithZone() {
        val occurrence = occurrencesOf(morning).single()

        assertEquals(Instant.parse("2026-11-25T00:00:00Z"), occurrence.startsAt)
        assertEquals(Instant.parse("2026-11-25T01:40:00Z"), occurrence.endsAt)
        assertEquals(ClassOccurrenceId(classId = 1, startsAt = LocalDateTime(2026, 11, 25, 8, 0)), occurrence.id)
    }

    @Test
    fun classOccurrences_sameStartWithoutChoice_keepsFirstEntry() {
        val conflicting = entry(classId = 3, start = morning.startTime, end = morning.endTime)

        assertEquals(listOf(morning), occurrencesOf(morning, conflicting).map { it.entry })
    }

    @Test
    fun classOccurrences_sameStartWithChoice_keepsFrontEntry() {
        val conflicting = entry(classId = 3, start = morning.startTime, end = morning.endTime)
        val choice = ConflictChoice(classIds = setOf(1, 3), frontClassId = 3)

        val occurrences = listOf(term(morning, conflicting)).classOccurrences(listOf(choice), zone)

        assertEquals(listOf(conflicting), occurrences.map { it.entry })
    }

    @Test
    fun classOccurrences_overlappingWithDifferentStarts_keepsBoth() {
        val overlapping = entry(classId = 3, start = LocalTime(8, 55), end = LocalTime(9, 40))

        assertEquals(listOf(morning, overlapping), occurrencesOf(morning, overlapping).map { it.entry })
    }

    @Test
    fun remindersAt_beforeLead_showsNothingUntilLead() {
        val schedule = occurrencesOf(morning).remindersAt(at(7, 29))

        assertEquals(emptyList<ClassReminder>(), schedule.reminders)
        assertEquals(at(7, 30), schedule.nextChangeAt)
    }

    @Test
    fun remindersAt_fromLeadUntilImminentLead_isUpcoming() {
        assertEquals(listOf(ClassReminderStage.Upcoming), stagesAt(at(7, 30), morning))
        assertEquals(listOf(ClassReminderStage.Upcoming), stagesAt(at(7, 45) - 1.seconds, morning))
    }

    @Test
    fun remindersAt_withinImminentLead_isImminentUntilStart() {
        val schedule = occurrencesOf(morning).remindersAt(at(7, 45))

        assertEquals(listOf(ClassReminderStage.Imminent), schedule.reminders.map { it.stage })
        assertEquals(at(8, 0), schedule.nextChangeAt)
        assertEquals(listOf(ClassReminderStage.Imminent), stagesAt(at(8, 0) - 1.seconds, morning))
    }

    @Test
    fun remindersAt_fromStartUntilEnd_isStarted() {
        val schedule = occurrencesOf(morning).remindersAt(at(8, 0))

        assertEquals(listOf(ClassReminderStage.Started), schedule.reminders.map { it.stage })
        assertEquals(at(9, 40), schedule.nextChangeAt)
    }

    @Test
    fun remindersAt_afterLastClassEnds_showsNothingAndSchedulesNothing() {
        val schedule = occurrencesOf(morning).remindersAt(at(9, 40))

        assertEquals(emptyList<ClassReminder>(), schedule.reminders)
        assertNull(schedule.nextChangeAt)
    }

    @Test
    fun remindersAt_nextClassLeadDuringCurrentClass_showsBoth() {
        val schedule = occurrencesOf(morning, lateMorning).remindersAt(at(9, 30) + 1.minutes)

        assertEquals(
            listOf(morning to ClassReminderStage.Started, lateMorning to ClassReminderStage.Upcoming),
            schedule.reminders.map { it.occurrence.entry to it.stage },
        )
        assertEquals(at(9, 40), schedule.nextChangeAt)
    }

    @Test
    fun remindersAt_betweenClasses_nextChangeIsEarliestBoundary() {
        val schedule = occurrencesOf(morning, lateMorning).remindersAt(at(9, 20))

        assertEquals(at(9, 30), schedule.nextChangeAt)
    }
}

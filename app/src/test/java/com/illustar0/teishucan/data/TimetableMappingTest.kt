package com.illustar0.teishucan.data

import com.illustar0.zzu.eas.ClassOrigin
import com.illustar0.zzu.eas.ClassSession
import com.illustar0.zzu.eas.Course
import com.illustar0.zzu.eas.ExperimentProject
import com.illustar0.zzu.eas.NamedEntity
import com.illustar0.zzu.eas.PeriodInfo
import com.illustar0.zzu.eas.Room
import com.illustar0.zzu.eas.ScheduleState
import com.illustar0.zzu.eas.Semester
import com.illustar0.zzu.eas.SemesterId
import com.illustar0.zzu.eas.SemesterSeason
import com.illustar0.zzu.eas.SessionKind
import com.illustar0.zzu.eas.SessionTeacher
import com.illustar0.zzu.eas.TeachingClass
import com.illustar0.zzu.eas.Timetable
import java.time.DayOfWeek as JavaDayOfWeek
import java.time.LocalDate as JavaLocalDate
import java.time.LocalTime as JavaLocalTime
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableMappingTest {
    private val semester = Semester(
        id = SemesterId(194),
        code = "2026-2027-1",
        nameZh = "2026-2027学年第一学期",
        schoolYear = "2026-2027",
        season = SemesterSeason.AUTUMN,
        startDate = LocalDate(2026, 9, 7),
        endDate = LocalDate(2026, 9, 20),
        startsWeekOnSunday = false,
        isCountedInTerm = true,
        weekIndices = listOf(1, 2),
    )

    private val regularClass = TeachingClass(
        id = 1001,
        code = "CS101-01",
        origin = ClassOrigin.REGULAR,
        course = Course(
            id = 2,
            code = "CS101",
            nameZh = "程序设计",
            periodInfo = PeriodInfo(total = 32),
        ),
        scheduleState = ScheduleState.PUBLISHED,
    )

    private val experimentClass = TeachingClass(
        id = 1002,
        code = "PHY201-01",
        origin = ClassOrigin.STANDALONE_EXPERIMENT,
        course = Course(
            id = 3,
            code = "PHY201",
            nameZh = "大学物理实验",
            periodInfo = PeriodInfo(total = 16),
        ),
        scheduleState = ScheduleState.PUBLISHED,
    )

    @Test
    fun semesterToTerm_withoutTimetable_buildsUnloadedCalendarWeeks() {
        val term = semester.toTerm()

        assertEquals("194", term.id)
        assertEquals("2026-2027", term.schoolYear)
        assertEquals("1", term.number)
        assertFalse(term.isLoaded)
        assertEquals(listOf(1, 2), term.weeks.map { it.number })
        assertEquals(JavaLocalDate.of(2026, 9, 7), term.weeks[0].dates.first())
        assertEquals(JavaLocalDate.of(2026, 9, 13), term.weeks[0].dates.last())
        assertEquals(JavaLocalDate.of(2026, 9, 14), term.weeks[1].dates.first())
        assertTrue(term.weeks.all { it.entries.isEmpty() })
    }

    @Test
    fun semesterToTerm_regularSession_mapsScheduleTeachersAndRoom() {
        val regularSession = ClassSession(
            classId = regularClass.id,
            scheduleGroupId = 1,
            weekIndex = 1,
            date = LocalDate(2026, 9, 7),
            weekday = DayOfWeek.MONDAY,
            startTime = Instant.parse("2026-09-07T00:00:00Z"),
            endTime = Instant.parse("2026-09-07T01:50:00Z"),
            startUnit = 1,
            endUnit = 2,
            teachers = listOf(
                SessionTeacher(nameZh = "张老师"),
                SessionTeacher(nameZh = " "),
                SessionTeacher(nameZh = "李老师"),
            ),
            room = Room(
                id = 7,
                nameZh = "南101",
                building = NamedEntity(id = 8, nameZh = "综合楼", code = "B"),
                campus = NamedEntity(id = 9, nameZh = "主校区", code = "C"),
            ),
            kind = SessionKind.THEORY,
        )
        val timetable = Timetable(
            semester = semester,
            classes = listOf(regularClass),
            sessions = listOf(regularSession),
        )

        val term = semester.toTerm(timetable)

        assertTrue(term.isLoaded)
        val entry = term.weeks[0].entries.single()
        assertEquals(JavaDayOfWeek.MONDAY, entry.day)
        assertEquals(JavaLocalDate.of(2026, 9, 7), entry.date)
        assertEquals(1, entry.startPeriod)
        assertEquals(2, entry.periodSpan)
        assertEquals(JavaLocalTime.of(8, 0), entry.startTime)
        assertEquals(JavaLocalTime.of(9, 50), entry.endTime)
        assertEquals("程序设计", entry.title)
        assertEquals("张老师、李老师", entry.teacher)
        assertEquals("南101", entry.location)
    }

    @Test
    fun semesterToTerm_experimentSessionInExtraWeek_usesDisplayTitleCustomPlaceAndExtendsWeeks() {
        val extraWeekExperimentSession = ClassSession(
            classId = experimentClass.id,
            scheduleGroupId = 2,
            weekIndex = 3,
            date = LocalDate(2026, 9, 23),
            weekday = DayOfWeek.WEDNESDAY,
            startTime = Instant.parse("2026-09-23T06:30:00Z"),
            endTime = Instant.parse("2026-09-23T08:10:00Z"),
            startUnit = 5,
            endUnit = 7,
            teachers = emptyList(),
            customPlace = "物理实验中心 302",
            kind = SessionKind.EXPERIMENT,
            experimentProject = ExperimentProject(id = 10, code = "E1", nameZh = "迈克尔逊干涉仪"),
        )
        val timetable = Timetable(
            semester = semester,
            classes = listOf(experimentClass),
            sessions = listOf(extraWeekExperimentSession),
        )

        val term = semester.toTerm(timetable)

        assertEquals(listOf(1, 2, 3), term.weeks.map { it.number })
        assertEquals(JavaLocalDate.of(2026, 9, 21), term.weeks[2].dates.first())
        val entry = term.weeks[2].entries.single()
        assertEquals(JavaDayOfWeek.WEDNESDAY, entry.day)
        assertEquals(5, entry.startPeriod)
        assertEquals(3, entry.periodSpan)
        assertEquals(JavaLocalTime.of(14, 30), entry.startTime)
        assertEquals(JavaLocalTime.of(16, 10), entry.endTime)
        assertEquals("大学物理实验 - 迈克尔逊干涉仪", entry.title)
        assertEquals("", entry.teacher)
        assertEquals("物理实验中心 302", entry.location)
    }
}

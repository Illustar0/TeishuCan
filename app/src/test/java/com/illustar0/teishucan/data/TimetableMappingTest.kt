package com.illustar0.teishucan.data

import com.illustar0.teishucan.domain.ScheduleDayPart
import com.illustar0.teishucan.domain.ScheduleUnit
import com.illustar0.zzu.eas.ClassOrigin
import com.illustar0.zzu.eas.ClassSession
import com.illustar0.zzu.eas.Course
import com.illustar0.zzu.eas.CourseUnit
import com.illustar0.zzu.eas.DayPart
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
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
        assertEquals(LocalDate(2026, 9, 7), term.weeks[0].dates.first())
        assertEquals(LocalDate(2026, 9, 13), term.weeks[0].dates.last())
        assertEquals(LocalDate(2026, 9, 14), term.weeks[1].dates.first())
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
        assertEquals(regularClass.id, entry.classId)
        assertEquals(DayOfWeek.MONDAY, entry.day)
        assertEquals(LocalDate(2026, 9, 7), entry.date)
        assertEquals(1, entry.startPeriod)
        assertEquals(2, entry.periodSpan)
        assertEquals(LocalTime(8, 0), entry.startTime)
        assertEquals(LocalTime(9, 50), entry.endTime)
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
        assertEquals(LocalDate(2026, 9, 21), term.weeks[2].dates.first())
        val entry = term.weeks[2].entries.single()
        assertEquals(DayOfWeek.WEDNESDAY, entry.day)
        assertEquals(5, entry.startPeriod)
        assertEquals(3, entry.periodSpan)
        assertEquals(LocalTime(14, 30), entry.startTime)
        assertEquals(LocalTime(16, 10), entry.endTime)
        assertEquals("大学物理实验 - 迈克尔逊干涉仪", entry.title)
        assertEquals("", entry.teacher)
        assertEquals("物理实验中心 302", entry.location)
    }

    @Test
    fun semesterToTerm_extraWeekAfterCalendarGap_datesWeekFromItsSessions() {
        // 第 5 周的课在 10 月 14 日（周三），校历只到第 2 周，按周序号外推会算成 10 月 5 日开始。
        val gapWeekSession = ClassSession(
            classId = regularClass.id,
            scheduleGroupId = 3,
            weekIndex = 5,
            date = LocalDate(2026, 10, 14),
            weekday = DayOfWeek.WEDNESDAY,
            startTime = Instant.parse("2026-10-14T00:00:00Z"),
            endTime = Instant.parse("2026-10-14T01:50:00Z"),
            startUnit = 1,
            endUnit = 2,
            teachers = emptyList(),
            kind = SessionKind.THEORY,
        )
        val timetable = Timetable(
            semester = semester,
            classes = listOf(regularClass),
            sessions = listOf(gapWeekSession),
        )

        val term = semester.toTerm(timetable)

        val gapWeek = term.weeks.single { it.number == 5 }
        assertEquals(LocalDate(2026, 10, 12), gapWeek.dates.first())
        assertEquals(LocalDate(2026, 10, 14), gapWeek.entries.single().date)
    }

    @Test
    fun semesterToTerm_withExtendedUnits_mapsShortLabelsDayPartsAndTimes() {
        val units = listOf(
            courseUnit(1, "第一节", DayPart.MORNING, LocalTime(8, 0), LocalTime(8, 45)),
            courseUnit(5, "第+1节", DayPart.NOON, LocalTime(12, 0), LocalTime(12, 45)),
            courseUnit(7, "第五节", DayPart.AFTERNOON, LocalTime(14, 0), LocalTime(14, 45)),
            courseUnit(13, "第十节", DayPart.EVENING, LocalTime(19, 55), LocalTime(20, 40)),
            courseUnit(14, "第+4节", DayPart.EVENING, LocalTime(20, 50), LocalTime(21, 35)),
            courseUnit(15, "加试", DayPart("late_night"), LocalTime(21, 45), LocalTime(22, 30)),
        )
        val timetable = Timetable(
            semester = semester,
            units = units,
            classes = listOf(regularClass),
            sessions = emptyList(),
        )

        val term = semester.toTerm(timetable)

        assertEquals(
            listOf(
                ScheduleUnit(1, "1", ScheduleDayPart.Morning, LocalTime(8, 0), LocalTime(8, 45)),
                ScheduleUnit(5, "+1", ScheduleDayPart.Noon, LocalTime(12, 0), LocalTime(12, 45)),
                ScheduleUnit(7, "5", ScheduleDayPart.Afternoon, LocalTime(14, 0), LocalTime(14, 45)),
                ScheduleUnit(13, "10", ScheduleDayPart.Evening, LocalTime(19, 55), LocalTime(20, 40)),
                ScheduleUnit(14, "+4", ScheduleDayPart.Evening, LocalTime(20, 50), LocalTime(21, 35)),
                ScheduleUnit(15, "15", ScheduleDayPart.Evening, LocalTime(21, 45), LocalTime(22, 30)),
            ),
            term.units,
        )
        assertEquals("12", extractUnitShortLabel("第十二节", index = 12))
        assertEquals("20", extractUnitShortLabel("第二十节", index = 20))
        assertTrue(term.units[1].isExtra)
        assertFalse(term.units[0].isExtra)
    }

    private fun courseUnit(
        index: Int,
        nameZh: String,
        dayPart: DayPart,
        start: LocalTime,
        end: LocalTime,
    ) = CourseUnit(
        index = index,
        nameZh = nameZh,
        dayPart = dayPart,
        segmentIndex = 1,
        startTime = start,
        endTime = end,
    )
}

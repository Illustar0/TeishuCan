package com.illustar0.teishucan.domain

import com.illustar0.teishucan.data.classroomRequestKey
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassroomFilterTest {
    private val morningOccupied = ClassroomItem(
        id = 1,
        code = "N1-101",
        name = "南核心101",
        buildingId = 10,
        buildingName = "南核心教学楼",
        campusId = 1,
        campusName = "主校区",
        occupations = listOf(
            ClassroomOccupation(
                activityKind = ClassroomActivityKind.Lesson,
                activityName = "高等数学",
                teacherName = "王老师",
                startTime = LocalTime(8, 0),
                endTime = LocalTime(9, 50),
            ),
        ),
    )

    private val allDayFree = ClassroomItem(
        id = 2,
        code = "N1-1002",
        name = "南核心1002",
        buildingId = 10,
        buildingName = "南核心教学楼",
        campusId = 1,
        campusName = "主校区",
        occupations = emptyList(),
    )

    private val afternoonOccupied = ClassroomItem(
        id = 3,
        code = "N1-201",
        name = "南核心201",
        buildingId = 10,
        buildingName = "南核心教学楼",
        campusId = 1,
        campusName = "主校区",
        occupations = listOf(
            ClassroomOccupation(
                activityKind = ClassroomActivityKind.Experiment,
                activityName = "物理实验",
                teacherName = "李老师",
                startTime = LocalTime(14, 30),
                endTime = LocalTime(16, 20),
            ),
        ),
    )

    private val southCampusRoom = ClassroomItem(
        id = 4,
        code = "S2-301",
        name = "2号楼301",
        buildingId = 20,
        buildingName = "2号教学楼",
        campusId = 2,
        campusName = "南校区",
        occupations = emptyList(),
    )

    @Test
    fun halfOpenInterval_adjacentBoundaryDoesNotConflict() {
        assertFalse(morningOccupied.isFreeDuring(LocalTime(8, 0), LocalTime(9, 50)))
        assertFalse(morningOccupied.isFreeDuring(LocalTime(9, 0), LocalTime(10, 0)))
        assertTrue(morningOccupied.isFreeDuring(LocalTime(9, 50), LocalTime(11, 0)))
        assertTrue(morningOccupied.isFreeDuring(LocalTime(7, 0), LocalTime(8, 0)))
    }

    @Test
    fun periodsFilter_checksEverySelectedPeriod() {
        assertFalse(morningOccupied.isFreeForPeriods(setOf(1, 2)))
        assertFalse(morningOccupied.isFreeForPeriods(setOf(2, 3)))
        assertTrue(morningOccupied.isFreeForPeriods(setOf(3, 4)))
        assertEquals(8, morningOccupied.freePeriodCount())
    }

    @Test
    fun filterClassrooms_appliesCampusBuildingTimeAndNaturalOrdering() {
        val all = listOf(allDayFree, afternoonOccupied, morningOccupied, southCampusRoom)

        val morningFree = filterClassrooms(
            items = all,
            filter = ClassroomFilter(
                campusId = 1,
                buildingIds = setOf(10L),
                timeFilter = ClassroomTimeFilter.Periods(setOf(1, 2)),
                onlyAvailable = true,
            ),
            currentTime = LocalTime(8, 30),
            isToday = true,
        )

        // 自然排序下 201 排在 1002 前面；只选 10 号楼时不含南校区 20 号楼。
        assertEquals(listOf("南核心201", "南核心1002"), morningFree.map(ClassroomItem::name))
    }

    @Test
    fun filterClassrooms_nowModeUsesExactHalfOpenMomentAndFallsBackOffToday() {
        val all = listOf(morningOccupied, afternoonOccupied, allDayFree)

        // 08:00 刚开始占用；09:50 结束占用，立刻恢复空闲。
        assertFalse(morningOccupied.matchesAvailability(ClassroomTimeFilter.Now, LocalTime(8, 0), isToday = true))
        assertTrue(morningOccupied.matchesAvailability(ClassroomTimeFilter.Now, LocalTime(9, 50), isToday = true))
        assertTrue(
            morningOccupied.matchesAvailability(ClassroomTimeFilter.Now, LocalTime(7, 59, 30), isToday = true),
        )

        val freeAtNine = filterClassrooms(
            items = all,
            filter = ClassroomFilter(timeFilter = ClassroomTimeFilter.Now, onlyAvailable = true),
            currentTime = LocalTime(9, 0),
            isToday = true,
        )
        assertEquals(listOf("南核心201", "南核心1002"), freeAtNine.map(ClassroomItem::name))

        // 查非今天时 Now 回退为全天无占用。
        assertFalse(morningOccupied.matchesAvailability(ClassroomTimeFilter.Now, LocalTime(12, 0), isToday = false))
        assertTrue(allDayFree.matchesAvailability(ClassroomTimeFilter.Now, LocalTime(12, 0), isToday = false))
    }

    @Test
    fun filterHasServerAttributes_detectsEachServerDimensionIndependently() {
        assertFalse(ClassroomFilter(campusId = 1, buildingIds = setOf(10)).hasServerAttributes)
        assertTrue(ClassroomFilter(floors = setOf(3)).hasServerAttributes)
        assertTrue(ClassroomFilter(roomTypeIds = setOf(100)).hasServerAttributes)
        assertTrue(ClassroomFilter(minSeats = 60).hasServerAttributes)
        assertTrue(ClassroomFilter(maxSeats = 120).hasServerAttributes)
    }

    @Test
    fun typedRequestKey_ignoresInsertionOrderOfFilterSets() {
        val date = LocalDate(2026, 11, 24)
        val first = classroomRequestKey(
            date = date,
            filter = ClassroomFilter(floors = linkedSetOf(1, 2), buildingIds = linkedSetOf(10, 20)),
        )
        val second = classroomRequestKey(
            date = date,
            filter = ClassroomFilter(floors = linkedSetOf(2, 1), buildingIds = linkedSetOf(20, 10)),
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun matchesClassroomQuery_multiTokenMatchesAcrossFieldsIgnoringCase() {
        assertTrue(matchesClassroomQuery("主校区 n1-101", morningOccupied))
        assertTrue(matchesClassroomQuery("南核心 101", morningOccupied))
        assertFalse(matchesClassroomQuery("南校区 101", morningOccupied))
    }

    @Test
    fun groupClassroomsByBuilding_preservesOrderedGroups() {
        val filtered = filterClassrooms(
            items = listOf(allDayFree, southCampusRoom, afternoonOccupied),
            filter = ClassroomFilter(onlyAvailable = false),
            currentTime = LocalTime(10, 0),
            isToday = true,
        )

        val groups = groupClassroomsByBuilding(filtered)

        assertEquals(2, groups.size)
        assertEquals("2号教学楼", groups[0].buildingName)
        assertEquals("南核心教学楼", groups[1].buildingName)
        assertEquals(listOf("南核心201", "南核心1002"), groups[1].rooms.map(ClassroomItem::name))
    }

    @Test
    fun compareNatural_sortsChineseByPinyinAndNumbersByValue() {
        // UTF-16 码位里「南」(U+5357) 小于「博」(U+535A)，按汉语拼音排时「博」(bo) 应排在「南」(nan) 前面。
        assertTrue(compareNatural("博雅楼", "南核心教学楼") < 0)
        assertTrue(compareNatural("南核心201", "南核心1002") < 0)
        assertTrue(compareNatural("A2", "a10") < 0)
        assertTrue(compareNatural("A2", "A02") < 0)
        assertTrue(compareNatural("Room 005", "Room 12") < 0)
        assertTrue(compareNatural("R99999999999999999999", "R100000000000000000000") < 0)
    }

    @Test
    fun searchClassroomsByBuilding_matchesPreSortedListAndGroupsResults() {
        val preSorted = sortClassrooms(listOf(allDayFree, afternoonOccupied, morningOccupied, southCampusRoom))

        val results = searchClassroomsByBuilding(preSorted, "主校区 0")

        assertEquals(1, results.size)
        assertEquals(listOf("南核心101", "南核心201", "南核心1002"), results.single().rooms.map(ClassroomItem::name))
    }

    @Test
    fun meaningfulRoomCode_hidesSnowflakeIdsAndDuplicatesWhileKeepingRealCodes() {
        val snowflakeRoom = ClassroomItem(
            id = 5202,
            code = "1800525420555276288",
            name = "北5_202",
            buildingId = 10,
            buildingName = "北5",
            campusId = 1,
            campusName = "主校区",
        )
        val labRoom = ClassroomItem(
            id = 5119,
            code = "公共卫生学院B区119",
            name = "预防医学实验室V",
            buildingId = 13,
            buildingName = "公共卫生学院",
            campusId = 1,
            campusName = "主校区",
        )

        assertEquals("", snowflakeRoom.displayCode)
        assertFalse(matchesClassroomQuery("1800525420555276288", snowflakeRoom))
        assertEquals("公共卫生学院B区119", labRoom.displayCode)
        assertTrue(matchesClassroomQuery("B区119", labRoom))
        assertEquals("", meaningfulRoomCode("中3302", "中3302"))
        assertEquals("3302", meaningfulRoomCode("3302", "中3302"))
    }

    @Test
    fun parseOccupationActivity_stripsRedundantPrefixesAndExtractsExperimentTitles() {
        val lessonWithRomanNumeral = parseOccupationActivity("课程：乐器演奏 (III) (08001103-001, 音乐学院)")
        assertEquals(
            ParsedOccupationActivity(
                title = "乐器演奏 (III)",
                department = "音乐学院",
                code = "08001103-001",
                isExperimentCode = false,
            ),
            lessonWithRomanNumeral,
        )

        val numericExperiment = parseOccupationActivity("实验项目：52011025(人体成分测定与分析)")
        assertEquals(
            ParsedOccupationActivity(
                title = "人体成分测定与分析",
                code = "52011025",
                isExperimentCode = true,
            ),
            numericExperiment,
        )

        val alphaNumericExperiment = parseOccupationActivity("实验项目：06YZ015(简谐振动幅值测量)")
        assertEquals(
            ParsedOccupationActivity(
                title = "简谐振动幅值测量",
                code = "06YZ015",
                isExperimentCode = true,
            ),
            alphaNumericExperiment,
        )

        val plainBorrow = parseOccupationActivity("借用：社团排练 (II)")
        assertEquals(ParsedOccupationActivity(title = "社团排练 (II)"), plainBorrow)

        assertEquals(
            ParsedOccupationActivity(title = "研究生专业课"),
            parseOccupationActivity("活动名称：研究生专业课"),
        )
        assertEquals(
            ParsedOccupationActivity(title = "大学物理B 期末考试"),
            parseOccupationActivity("考试：大学物理B 期末考试"),
        )
        assertEquals(
            ParsedOccupationActivity(title = "神经病学"),
            parseOccupationActivity("调课：神经病学"),
        )
    }

    @Test
    fun customScheduleUnits_evaluatesAvailabilityStripRangesAndContiguousLabels() {
        val gappedUnits = listOf(
            ScheduleUnit(1, "1", ScheduleDayPart.Morning, LocalTime(8, 0), LocalTime(8, 45)),
            ScheduleUnit(3, "+1", ScheduleDayPart.Morning, LocalTime(10, 10), LocalTime(10, 55)),
            ScheduleUnit(7, "5", ScheduleDayPart.Afternoon, LocalTime(14, 0), LocalTime(14, 45)),
        )
        val morningSecondOccupied = ClassroomItem(
            id = 8,
            code = "N1-305",
            name = "南核心305",
            buildingId = 10,
            buildingName = "南核心教学楼",
            campusId = 1,
            campusName = "主校区",
            occupations = listOf(
                ClassroomOccupation(
                    activityKind = ClassroomActivityKind.Exam,
                    activityName = "高等数学随堂测验",
                    teacherName = "",
                    startTime = LocalTime(10, 10),
                    endTime = LocalTime(10, 55),
                ),
            ),
        )

        assertEquals(2, morningSecondOccupied.freePeriodCount(gappedUnits))
        assertFalse(morningSecondOccupied.isFreeForPeriods(setOf(3), gappedUnits))
        assertTrue(morningSecondOccupied.isFreeForPeriods(setOf(1, 7), gappedUnits))

        // 上午虽然跳过了 index = 2，节次条仍只画实际存在的 2 格（0..1），全天共 3 格。
        assertEquals(listOf(0..1, 2..2), unitPositionRanges(gappedUnits))

        // 按作息表顺序连选 {1, 3} 或 {3, 7} 都算紧挨着的连续节次；跳选 {1, 7} 不算连续。
        assertEquals("1" to "+1", contiguousPeriodEndpoints(setOf(1, 3), gappedUnits))
        assertEquals("+1" to "5", contiguousPeriodEndpoints(setOf(3, 7), gappedUnits))
        assertNull(contiguousPeriodEndpoints(setOf(1, 7), gappedUnits))
        assertNull(contiguousPeriodEndpoints(setOf(1), gappedUnits))

        val availableAtMorningExtra = filterClassrooms(
            items = listOf(morningSecondOccupied, allDayFree),
            filter = ClassroomFilter(
                timeFilter = ClassroomTimeFilter.Periods(setOf(3)),
                onlyAvailable = true,
            ),
            currentTime = LocalTime(10, 0),
            isToday = true,
            units = gappedUnits,
        )
        assertEquals(listOf("南核心1002"), availableAtMorningExtra.map(ClassroomItem::name))
    }

    @Test
    fun mergeClassroomOccupations_combinesIdenticalCourseSlotsAndSplitsTeachers() {
        val teachers = "康乔瑄,赵阳,巩笑天,巴彦蒙赫,张振渝,郭雨,马明霞,施熠,韩旭,肖智中,陈放"
        val raw = listOf(
            ClassroomOccupation(
                activityKind = ClassroomActivityKind.Lesson,
                activityName = "课程：乐器演奏 (III) (08001103-001, 音乐学院)",
                teacherName = teachers,
                startTime = LocalTime(17, 50),
                endTime = LocalTime(18, 35),
            ),
            ClassroomOccupation(
                activityKind = ClassroomActivityKind.Lesson,
                activityName = "课程：乐器演奏 (III) (08001103-001, 音乐学院)",
                teacherName = teachers,
                startTime = LocalTime(19, 0),
                endTime = LocalTime(19, 45),
            ),
            ClassroomOccupation(
                activityKind = ClassroomActivityKind.Lesson,
                activityName = "课程：乐器演奏 (III) (08001103-001, 音乐学院)",
                teacherName = teachers,
                startTime = LocalTime(19, 55),
                endTime = LocalTime(20, 40),
            ),
        )

        val merged = mergeClassroomOccupations(raw)

        assertEquals(1, merged.size)
        val single = merged.single()
        assertEquals("乐器演奏 (III)", single.activity.title)
        assertEquals(11, single.teachers.size)
        assertEquals(listOf("康乔瑄", "赵阳", "巩笑天"), single.teachers.take(3))
        assertEquals(
            listOf(
                OccupationTimeSlot(LocalTime(17, 50), LocalTime(18, 35)),
                OccupationTimeSlot(LocalTime(19, 0), LocalTime(19, 45)),
                OccupationTimeSlot(LocalTime(19, 55), LocalTime(20, 40)),
            ),
            single.slots,
        )
    }

    @Test
    fun buildCalendarMonthGrid_alwaysProduces42MondayAlignedCellsWithNeighborDays() {
        // 2026 年 11 月 1 日是周日：开头补 10 月 26 至 31 日共 6 天，末尾补 12 月 1 至 6 日共 6 天。
        val nov2026 = buildCalendarMonthGrid(YearMonth(2026, 11))
        assertEquals(CalendarGridCellCount, nov2026.size)
        assertEquals(DayOfWeek.MONDAY, nov2026.first().date.dayOfWeek)
        assertEquals(LocalDate(2026, 10, 26), nov2026.first().date)
        assertFalse(nov2026.first().isInDisplayedMonth)
        assertEquals(LocalDate(2026, 11, 1), nov2026[6].date)
        assertTrue(nov2026[6].isInDisplayedMonth)
        assertEquals(LocalDate(2026, 12, 6), nov2026.last().date)
        assertFalse(nov2026.last().isInDisplayedMonth)

        // 2026 年 12 月跨年：末尾补齐 2027 年 1 月前 10 天。
        val dec2026 = buildCalendarMonthGrid(YearMonth(2026, 12))
        assertEquals(CalendarGridCellCount, dec2026.size)
        assertEquals(LocalDate(2026, 11, 30), dec2026.first().date)
        assertEquals(LocalDate(2027, 1, 10), dec2026.last().date)
    }

    @Test
    fun loadProgressPercentage_scalesValidFractionAndClampsBounds() {
        assertNull(ClassroomLoadProgress(loadedPages = 0, totalPages = null).percentage)
        assertNull(ClassroomLoadProgress(loadedPages = 1, totalPages = 0).percentage)
        assertEquals(0, ClassroomLoadProgress(loadedPages = 0, totalPages = 8).percentage)
        assertEquals(65, ClassroomLoadProgress(loadedPages = 13, totalPages = 20).percentage)
        assertEquals(100, ClassroomLoadProgress(loadedPages = 12, totalPages = 10).percentage)
    }
}

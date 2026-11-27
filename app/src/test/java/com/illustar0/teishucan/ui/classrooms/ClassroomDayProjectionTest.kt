package com.illustar0.teishucan.ui.classrooms

import com.illustar0.teishucan.data.classroomRequestKey
import com.illustar0.teishucan.domain.ClassroomActivityKind
import com.illustar0.teishucan.domain.ClassroomDaySnapshot
import com.illustar0.teishucan.domain.ClassroomFilter
import com.illustar0.teishucan.domain.ClassroomItem
import com.illustar0.teishucan.domain.ClassroomLoadProgress
import com.illustar0.teishucan.domain.ClassroomOccupation
import com.illustar0.teishucan.domain.ClassroomTimeFilter
import com.illustar0.teishucan.domain.DefaultScheduleUnits
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassroomDayProjectionTest {
    private val today = LocalDate(2026, 11, 24)
    private val tomorrow = LocalDate(2026, 11, 25)
    private val yesterday = LocalDate(2026, 11, 23)
    private val fetchedAt = Instant.parse("2026-11-24T01:00:00Z")

    private val morningBusyRoom = ClassroomItem(
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

    private val allDayFreeRoom = ClassroomItem(
        id = 2,
        code = "N1-102",
        name = "南核心102",
        buildingId = 10,
        buildingName = "南核心教学楼",
        campusId = 1,
        campusName = "主校区",
    )

    @Test
    fun effectiveFor_degradesNowOffTodayWithoutMutatingRawFilter() {
        val raw = ClassroomFilter(
            campusId = 1L,
            buildingIds = setOf(10L),
            timeFilter = ClassroomTimeFilter.Now,
            onlyAvailable = true,
        )

        val onToday = raw.effectiveFor(isToday = true)
        val offToday = raw.effectiveFor(isToday = false)

        assertEquals(ClassroomTimeFilter.Now, onToday.timeFilter)
        assertEquals(ClassroomTimeFilter.Periods(), offToday.timeFilter)
        assertEquals(1L, offToday.campusId)
        assertEquals(setOf(10L), offToday.buildingIds)
        assertEquals(ClassroomTimeFilter.Now, raw.timeFilter)
    }

    @Test
    fun projectClassroomDayPages_rendersCachedNeighborsIndependentlyAndDefaultsUncachedToLoading() {
        val cache = mapOf(
            today to ClassroomDaySnapshot(today, fetchedAt, listOf(morningBusyRoom, allDayFreeRoom)),
            tomorrow to ClassroomDaySnapshot(tomorrow, fetchedAt, listOf(morningBusyRoom, allDayFreeRoom)),
        )
        val rawFilter = ClassroomFilter(timeFilter = ClassroomTimeFilter.Now, onlyAvailable = true)
        val context = DayProjectionContext(
            selectedDate = today,
            today = today,
            currentTime = LocalTime(10, 30),
            rawFilter = rawFilter,
            units = DefaultScheduleUnits,
            activeLoad = null,
        )

        val pages = projectClassroomDayPages(context) { date, _ -> cache[date] }
        val state = AvailableClassroomsState(
            selectedDate = today,
            today = today,
            currentTime = LocalTime(10, 30),
            rawFilter = rawFilter,
            dayPages = pages,
        )

        // 今天 10:30 时两间教室都空闲。
        val todayPage = state.pageFor(today)
        assertEquals(ClassroomBrowserStatus.Ready, todayPage.status)
        assertEquals(ClassroomTimeFilter.Now, todayPage.effectiveFilter.timeFilter)
        assertEquals(listOf("南核心101", "南核心102"), todayPage.filteredRooms.map(ClassroomItem::name))

        // 明天非今天，「此刻空闲」降级为全天无占用，只剩全天无课的 102。
        val tomorrowPage = state.pageFor(tomorrow)
        assertEquals(ClassroomBrowserStatus.Ready, tomorrowPage.status)
        assertEquals(ClassroomTimeFilter.Periods(), tomorrowPage.effectiveFilter.timeFilter)
        assertEquals(listOf("南核心102"), tomorrowPage.filteredRooms.map(ClassroomItem::name))

        // 昨天没缓存，不在投影表里，由 pageFor 兜底为加载态。
        assertFalse(yesterday in pages)
        val yesterdayPage = state.pageFor(yesterday)
        assertEquals(ClassroomBrowserStatus.Loading, yesterdayPage.status)
        assertTrue(yesterdayPage.filteredRooms.isEmpty())
        assertEquals(ClassroomTimeFilter.Periods(), yesterdayPage.effectiveFilter.timeFilter)
    }

    @Test
    fun projectClassroomDayPages_isolatesActiveLoadingAndFailureToMatchingDate() {
        val cache = mapOf(
            today to ClassroomDaySnapshot(today, fetchedAt, listOf(allDayFreeRoom)),
        )
        val filter = ClassroomFilter()
        val loadingProgress = ClassroomLoadProgress(loadedPages = 2, totalPages = 5)
        val loadingContext = DayProjectionContext(
            selectedDate = tomorrow,
            today = today,
            currentTime = LocalTime(10, 30),
            rawFilter = filter,
            units = DefaultScheduleUnits,
            activeLoad = ActiveLoad.Loading(
                key = classroomRequestKey(tomorrow, filter),
                progress = loadingProgress,
            ),
        )

        val loadingPages = projectClassroomDayPages(loadingContext) { date, _ -> cache[date] }

        assertEquals(ClassroomBrowserStatus.Loading, loadingPages.getValue(tomorrow).status)
        assertTrue(loadingPages.getValue(tomorrow).isRefreshing)
        assertEquals(loadingProgress, loadingPages.getValue(tomorrow).loadProgress)
        // 相邻已缓存的今天保持 Ready，不受明天加载中影响。
        assertEquals(ClassroomBrowserStatus.Ready, loadingPages.getValue(today).status)
        assertFalse(loadingPages.getValue(today).isRefreshing)
        assertNull(loadingPages.getValue(today).loadProgress)

        val failedContext = loadingContext.copy(
            activeLoad = ActiveLoad.Failed(key = classroomRequestKey(tomorrow, filter)),
        )
        val failedPages = projectClassroomDayPages(failedContext) { date, _ -> cache[date] }

        assertEquals(ClassroomBrowserStatus.Failed, failedPages.getValue(tomorrow).status)
        assertEquals(ClassroomBrowserStatus.Ready, failedPages.getValue(today).status)
    }
}

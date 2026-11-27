package com.illustar0.teishucan.data

import com.illustar0.teishucan.domain.ClassroomActivityKind
import com.illustar0.teishucan.domain.ClassroomCacheSnapshot
import com.illustar0.teishucan.domain.ClassroomCacheTtl
import com.illustar0.teishucan.domain.ClassroomDaySnapshot
import com.illustar0.teishucan.domain.ScheduleDayPart
import com.illustar0.teishucan.domain.effectiveUnits
import com.illustar0.teishucan.domain.matchesClassroomQuery
import com.illustar0.zzu.eas.Building
import com.illustar0.zzu.eas.Campus
import com.illustar0.zzu.eas.ClassroomCatalog
import com.illustar0.zzu.eas.ClassroomOccupancy
import com.illustar0.zzu.eas.ClassroomPage
import com.illustar0.zzu.eas.CourseUnit
import com.illustar0.zzu.eas.DayPart
import com.illustar0.zzu.eas.RoomActivityType
import com.illustar0.zzu.eas.RoomOccupation
import com.illustar0.zzu.eas.RoomType
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClassroomRepositoryLogicTest {
    private val sampleCatalog = ClassroomCatalog(
        campuses = listOf(
            Campus(id = 1, code = "C1", nameZh = "主校区", isEnabled = true),
            Campus(id = 9, code = "C9", nameZh = "停用校区", isEnabled = false),
        ),
        buildings = listOf(
            Building(id = 10, code = "B10", nameZh = "南核心教学楼", campusId = 1, isEnabled = true),
            Building(id = 11, code = "B11", nameZh = "已停用楼", campusId = 1, isEnabled = false),
        ),
        roomTypes = listOf(
            RoomType(id = 100, code = "RT1", nameZh = "多媒体教室", isEnabled = true),
            RoomType(id = 101, code = "RT2", nameZh = "旧类型", isEnabled = false),
        ),
        floors = listOf(3, 1, 2, 2),
    )

    @Test
    fun catalogAndOccupancyMapping_filtersDisabledEntriesClipsOvernightAndMapsTimes() {
        val domainCatalog = sampleCatalog.toDomain()
        val queryDate = LocalDate(2026, 11, 24)

        assertEquals(listOf(1L), domainCatalog.campuses.map { it.id })
        assertEquals(listOf(10L), domainCatalog.buildings.map { it.id })
        assertEquals(listOf(100L), domainCatalog.roomTypes.map { it.id })
        assertEquals(listOf(1, 2, 3), domainCatalog.floors)

        val occupancy = ClassroomOccupancy(
            id = 501,
            code = "N1-101",
            nameZh = "南核心101",
            buildingId = 10,
            campusNameZh = "主校区",
            occupations = listOf(
                RoomOccupation(
                    activityId = 1,
                    activityType = RoomActivityType.ROOM_BORROW,
                    activityName = "社团答辩",
                    date = queryDate,
                    startTime = Instant.parse("2026-11-24T06:30:00Z"),
                    endTime = Instant.parse("2026-11-24T08:20:00Z"),
                    teacherName = "赵老师",
                ),
                // 跨午夜 23:30 到次日 00:30（UTC+8）裁进当天末尾，不出现结束早于开始。
                RoomOccupation(
                    activityId = 2,
                    activityType = RoomActivityType.EXPERIMENT,
                    activityName = "通宵测试",
                    date = queryDate,
                    startTime = Instant.parse("2026-11-24T15:30:00Z"),
                    endTime = Instant.parse("2026-11-24T16:30:00Z"),
                    teacherName = "",
                ),
            ),
        )

        val item = listOf(occupancy).toDomainList(domainCatalog, queryDate).single()
        assertEquals("南核心教学楼", item.buildingName)
        assertEquals(1L, item.campusId)
        assertEquals("主校区", item.campusName)
        assertEquals(2, item.occupations.size)
        assertEquals(ClassroomActivityKind.RoomBorrow, item.occupations[0].activityKind)
        assertEquals(LocalTime(14, 30), item.occupations[0].startTime)
        assertEquals(LocalTime(16, 20), item.occupations[0].endTime)
        assertEquals(LocalTime(23, 30), item.occupations[1].startTime)
        assertEquals(LocalTime.parse("23:59:59.999999999"), item.occupations[1].endTime)
        assertFalse(item.isFreeAtMoment(LocalTime(23, 45)))
    }

    @Test
    fun occupancyMapping_mapsExamScheduleChangeAndUnknownActivityTypes() {
        val domainCatalog = sampleCatalog.toDomain()
        val queryDate = LocalDate(2026, 11, 24)
        val occupancy = ClassroomOccupancy(
            id = 502,
            code = "N1-102",
            nameZh = "南核心102",
            buildingId = 10,
            campusNameZh = "主校区",
            occupations = listOf(
                RoomOccupation(
                    activityId = 1,
                    activityType = RoomActivityType.SCHEDULE_CHANGE,
                    activityName = "神经病学",
                    date = queryDate,
                    startTime = Instant.parse("2026-11-24T08:00:00Z"),
                    endTime = Instant.parse("2026-11-24T09:40:00Z"),
                    teacherName = "苗旺",
                ),
                RoomOccupation(
                    activityId = 2,
                    activityType = RoomActivityType.EXAM,
                    activityName = "大学物理B 期末考试",
                    date = queryDate,
                    startTime = Instant.parse("2026-11-24T11:00:00Z"),
                    endTime = Instant.parse("2026-11-24T12:40:00Z"),
                    teacherName = "",
                ),
                RoomOccupation(
                    activityId = 3,
                    activityType = RoomActivityType("custom_event"),
                    activityName = "学术讲座",
                    date = queryDate,
                    startTime = Instant.parse("2026-11-24T13:00:00Z"),
                    endTime = Instant.parse("2026-11-24T14:00:00Z"),
                    teacherName = "",
                ),
            ),
        )

        val kinds = listOf(occupancy)
            .toDomainList(domainCatalog, queryDate)
            .single()
            .occupations
            .map { it.activityKind }

        assertEquals(
            listOf(ClassroomActivityKind.ScheduleChange, ClassroomActivityKind.Exam, ClassroomActivityKind.Other),
            kinds,
        )
    }

    @Test
    fun fetchAllPages_enforcesConcurrencyLimitMergesAndDeduplicates() = runTest {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        val gates = (2..5).associateWith { CompletableDeferred<Unit>() }
        val progressHistory = mutableListOf<Pair<Int, Int>>()

        val job = launch {
            val merged = fetchAllPages(
                limiter = Semaphore(2),
                fetchPage = { page ->
                    val current = inFlight.incrementAndGet()
                    maxInFlight.accumulateAndGet(current, ::maxOf)
                    try {
                        gates[page]?.await()
                        when (page) {
                            1 -> ClassroomPage(listOf(room(1, "A101"), room(2, "A102")), 1, 2, 6, 5)
                            2 -> ClassroomPage(listOf(room(2, "A102-dup"), room(3, "A103")), 2, 2, 6, 5)
                            else -> ClassroomPage(listOf(room(page.toLong() + 1, "A10$page")), page, 2, 6, 5)
                        }
                    } finally {
                        inFlight.decrementAndGet()
                    }
                },
                onProgress = { loaded, total -> progressHistory += loaded to total },
            )
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), merged.map(ClassroomOccupancy::id))
            assertEquals("A102", merged.first { it.id == 2L }.nameZh)
        }

        runCurrent()
        // 第 1 页拉完后，剩下 4 页受 Semaphore(2) 限制，同时最多跑 2 个请求。
        assertEquals(2, inFlight.get())
        assertEquals(2, maxInFlight.get())

        gates.getValue(2).complete(Unit)
        gates.getValue(3).complete(Unit)
        runCurrent()
        assertEquals(2, maxInFlight.get())

        gates.getValue(4).complete(Unit)
        gates.getValue(5).complete(Unit)
        runCurrent()

        job.join()
        assertEquals(2, maxInFlight.get())
        assertEquals(1 to 5, progressHistory.first())
        assertEquals(5 to 5, progressHistory.last())
        assertEquals(5, progressHistory.size)
    }

    @Test
    fun fetchAllPages_cancellingCallerCancelsInFlightPages() = runTest {
        val page2Started = CompletableDeferred<Unit>()
        val page2Cancelled = CompletableDeferred<Unit>()

        val job = launch {
            fetchAllPages(
                limiter = Semaphore(2),
                fetchPage = { page ->
                    if (page == 1) {
                        ClassroomPage(listOf(room(1, "A101")), 1, 1, 2, 2)
                    } else {
                        page2Started.complete(Unit)
                        try {
                            CompletableDeferred<ClassroomPage>().await()
                        } finally {
                            page2Cancelled.complete(Unit)
                        }
                    }
                },
                onProgress = { _, _ -> },
            )
        }

        page2Started.await()
        job.cancel()
        job.join()
        assertTrue(page2Cancelled.isCompleted)
    }

    @Test
    fun pruneCachedDays_keepsMostRecentDaysUpToLimit() {
        val base = Instant.parse("2026-11-24T00:00:00Z")
        val days = (0 until 10).associate { offset ->
            val date = LocalDate(2026, 11, 1).plus(offset, DateTimeUnit.DAY)
            date to ClassroomDaySnapshot(date = date, fetchedAt = base + offset.hours, items = emptyList())
        }
        val prunedDays = pruneCachedDays(days, maxDays = MaxCachedClassroomDays)
        assertEquals(MaxCachedClassroomDays, prunedDays.size)
        assertFalse(LocalDate(2026, 11, 1) in prunedDays)
        assertTrue(LocalDate(2026, 11, 10) in prunedDays)
    }

    @Test
    fun serializationRoundTrip_preservesOccupationsAndMatchesQuery() {
        val fetchedAt = Instant.parse("2026-11-24T01:00:00Z")
        val queryDate = LocalDate(2026, 11, 24)
        assertFalse(isStaleTimestamp(fetchedAt, fetchedAt + 12.hours, ClassroomCacheTtl))
        assertTrue(isStaleTimestamp(fetchedAt, fetchedAt + 13.hours, ClassroomCacheTtl))

        val domainCatalog = sampleCatalog.toDomain()
        val occupiedRoom = ClassroomOccupancy(
            id = 1,
            code = "N1-101",
            nameZh = "南核心101",
            buildingId = 10,
            campusNameZh = "主校区",
            occupations = listOf(
                RoomOccupation(
                    activityId = 1,
                    activityType = RoomActivityType.LESSON,
                    activityName = "高等数学",
                    date = queryDate,
                    startTime = Instant.parse("2026-11-24T00:00:00Z"),
                    endTime = Instant.parse("2026-11-24T01:50:00Z"),
                    teacherName = "王老师",
                ),
            ),
        )
        val daySnapshot = ClassroomDaySnapshot(
            date = queryDate,
            fetchedAt = fetchedAt,
            items = listOf(occupiedRoom).toDomainList(domainCatalog, queryDate),
        )
        val cacheSnapshot = ClassroomCacheSnapshot(
            catalog = domainCatalog,
            catalogFetchedAt = fetchedAt,
            days = mapOf(queryDate to daySnapshot),
        )
        val encoded = StoreJson.encodeToString(ClassroomCacheSnapshot.serializer(), cacheSnapshot)
        val decoded = StoreJson.decodeFromString(ClassroomCacheSnapshot.serializer(), encoded)
        val decodedItem = decoded.days.getValue(queryDate).items.single()

        assertEquals(cacheSnapshot, decoded)
        assertTrue(matchesClassroomQuery("主校区 南核心 101", decodedItem))
        // 第 1、2 节占用，其余 8 节空闲。
        assertEquals(8, decodedItem.freePeriodCount())
        assertFalse(decodedItem.isFreeAtPeriodNumber(1))
        assertFalse(decodedItem.isFreeAtPeriodNumber(2))
        assertTrue(decodedItem.isFreeAtPeriodNumber(3))
    }

    @Test
    fun serializationRoundTrip_withCustomCatalogUnits_evaluatesOccupancyAgainstCatalogSchedule() {
        val fetchedAt = Instant.parse("2026-11-24T01:00:00Z")
        val queryDate = LocalDate(2026, 11, 24)
        val catalogWithNoonUnits = ClassroomCatalog(
            campuses = sampleCatalog.campuses,
            buildings = sampleCatalog.buildings,
            roomTypes = sampleCatalog.roomTypes,
            floors = sampleCatalog.floors,
            units = listOf(
                CourseUnit(1, "第一节", null, DayPart.MORNING, 1, LocalTime(8, 0), LocalTime(8, 45)),
                CourseUnit(5, "第+1节", null, DayPart.NOON, 3, LocalTime(12, 0), LocalTime(12, 45)),
                CourseUnit(7, "第五节", null, DayPart.AFTERNOON, 4, LocalTime(14, 0), LocalTime(14, 45)),
            ),
        ).toDomain()

        assertEquals(listOf("1", "+1", "5"), catalogWithNoonUnits.units.map { it.label })
        assertEquals(ScheduleDayPart.Noon, catalogWithNoonUnits.units[1].dayPart)

        val noonOccupied = ClassroomOccupancy(
            id = 2,
            code = "E1-202",
            nameZh = "东区1_202",
            buildingId = 10,
            campusNameZh = "主校区",
            occupations = listOf(
                RoomOccupation(
                    activityId = 9,
                    activityType = RoomActivityType.LESSON,
                    activityName = "医学文献检索",
                    date = queryDate,
                    startTime = Instant.parse("2026-11-24T04:10:00Z"),
                    endTime = Instant.parse("2026-11-24T04:40:00Z"),
                    teacherName = "陈敏",
                ),
            ),
        )
        val cacheSnapshot = ClassroomCacheSnapshot(
            catalog = catalogWithNoonUnits,
            catalogFetchedAt = fetchedAt,
            days = mapOf(
                queryDate to ClassroomDaySnapshot(
                    date = queryDate,
                    fetchedAt = fetchedAt,
                    items = listOf(noonOccupied).toDomainList(catalogWithNoonUnits, queryDate),
                ),
            ),
        )

        val encoded = StoreJson.encodeToString(ClassroomCacheSnapshot.serializer(), cacheSnapshot)
        val decoded = StoreJson.decodeFromString(ClassroomCacheSnapshot.serializer(), encoded)
        val restoredUnits = decoded.catalog.effectiveUnits
        val decodedItem = decoded.days.getValue(queryDate).items.single()

        assertEquals(cacheSnapshot, decoded)
        assertTrue(decodedItem.isFreeAtPeriodNumber(1, restoredUnits))
        assertFalse(decodedItem.isFreeAtPeriodNumber(5, restoredUnits))
        assertTrue(decodedItem.isFreeAtPeriodNumber(7, restoredUnits))
        assertEquals(2, decodedItem.freePeriodCount(restoredUnits))
    }

    private fun room(id: Long, name: String) = ClassroomOccupancy(
        id = id,
        code = "R$id",
        nameZh = name,
        buildingId = 10,
        campusNameZh = "主校区",
    )
}

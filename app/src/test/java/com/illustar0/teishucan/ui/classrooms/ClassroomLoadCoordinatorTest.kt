package com.illustar0.teishucan.ui.classrooms

import com.illustar0.teishucan.data.classroomRequestKey
import com.illustar0.teishucan.domain.ClassroomDaySnapshot
import com.illustar0.teishucan.domain.ClassroomFilter
import com.illustar0.teishucan.domain.ClassroomLoadProgress
import java.io.IOException
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClassroomLoadCoordinatorTest {
    private val dayA = LocalDate(2026, 11, 24)
    private val dayB = LocalDate(2026, 11, 25)
    private val dayC = LocalDate(2026, 11, 26)
    private val dayD = LocalDate(2026, 11, 27)
    private val filter = ClassroomFilter()
    private val fetchedAt = Instant.parse("2026-11-24T01:00:00Z")

    @Test
    fun returningToAWhileBIsLoading_keepsBAliveAndDeduplicatesReentry() = runTest {
        var activeDate = dayA
        val bGate = CompletableDeferred<ClassroomDaySnapshot>()
        val callCounts = mutableMapOf<LocalDate, Int>()
        val coordinator = ClassroomLoadCoordinator(
            maxConcurrentLoads = 3,
            currentActiveKey = { classroomRequestKey(activeDate, filter) },
            loadDay = { date, _, _, onProgress ->
                callCounts[date] = (callCounts[date] ?: 0) + 1
                if (date == dayA) {
                    // 模拟命中本地新鲜缓存：直接返回，不触发 onProgress。
                    snapshot(dayA)
                } else {
                    onProgress(ClassroomLoadProgress(loadedPages = 2, totalPages = 5))
                    bGate.await()
                }
            },
            onActiveFailure = {},
        )

        // 先在 A（缓存命中），滑到 B 开始联网加载。
        coordinator.enqueue(this, dayA, filter, force = false)
        runCurrent()

        activeDate = dayB
        coordinator.enqueue(this, dayB, filter, force = false)
        runCurrent()

        val keyB = classroomRequestKey(dayB, filter)
        assertEquals(
            ActiveLoad.Loading(keyB, ClassroomLoadProgress(loadedPages = 2, totalPages = 5)),
            coordinator.loads.value[keyB],
        )

        // 没加载完就滑回 A：B 不被打断，状态仍在 Loading。
        activeDate = dayA
        coordinator.enqueue(this, dayA, filter, force = false)
        runCurrent()
        assertEquals(
            ActiveLoad.Loading(keyB, ClassroomLoadProgress(loadedPages = 2, totalPages = 5)),
            coordinator.loads.value[keyB],
        )

        // 再次滑回 B：复用在飞请求，不会发起第二次拉取。
        activeDate = dayB
        coordinator.enqueue(this, dayB, filter, force = false)
        runCurrent()
        assertEquals(1, callCounts[dayB])

        // B 完成后状态切到 Ready。
        val loadedB = snapshot(dayB)
        bGate.complete(loadedB)
        runCurrent()
        assertEquals(ActiveLoad.Ready(keyB, loadedB), coordinator.loads.value[keyB])
    }

    @Test
    fun forceRefresh_cancelsPreviousInFlightAndIgnoresStaleCallbacks() = runTest {
        val activeDate = dayB
        val firstGate = CompletableDeferred<ClassroomDaySnapshot>()
        val secondGate = CompletableDeferred<ClassroomDaySnapshot>()
        var firstCancelled = false
        var attempt = 0
        val coordinator = ClassroomLoadCoordinator(
            maxConcurrentLoads = 3,
            currentActiveKey = { classroomRequestKey(activeDate, filter) },
            loadDay = { _, _, _, onProgress ->
                val currentAttempt = ++attempt
                if (currentAttempt == 1) {
                    onProgress(ClassroomLoadProgress(loadedPages = 1, totalPages = 4))
                    try {
                        firstGate.await()
                    } finally {
                        firstCancelled = true
                    }
                } else {
                    onProgress(ClassroomLoadProgress(loadedPages = 3, totalPages = 4))
                    secondGate.await()
                }
            },
            onActiveFailure = {},
        )

        val keyB = classroomRequestKey(dayB, filter)
        coordinator.enqueue(this, dayB, filter, force = false)
        runCurrent()
        assertEquals(
            ActiveLoad.Loading(keyB, ClassroomLoadProgress(loadedPages = 1, totalPages = 4)),
            coordinator.loads.value[keyB],
        )

        coordinator.enqueue(this, dayB, filter, force = true)
        runCurrent()
        assertTrue(firstCancelled)
        assertEquals(
            ActiveLoad.Loading(keyB, ClassroomLoadProgress(loadedPages = 3, totalPages = 4)),
            coordinator.loads.value[keyB],
        )

        val refreshed = snapshot(dayB)
        secondGate.complete(refreshed)
        runCurrent()
        assertEquals(ActiveLoad.Ready(keyB, refreshed), coordinator.loads.value[keyB])
    }

    @Test
    fun exceedingNetworkLimit_evictsOldestInFlightWhileCacheHitsNeverEvict() = runTest {
        var activeDate = dayB
        val gates = mapOf(
            dayB to CompletableDeferred<ClassroomDaySnapshot>(),
            dayC to CompletableDeferred<ClassroomDaySnapshot>(),
            dayD to CompletableDeferred<ClassroomDaySnapshot>(),
        )
        val cancelledDays = mutableSetOf<LocalDate>()
        val coordinator = ClassroomLoadCoordinator(
            maxConcurrentLoads = 2,
            currentActiveKey = { classroomRequestKey(activeDate, filter) },
            loadDay = { date, _, _, onProgress ->
                val gate = gates[date] ?: return@ClassroomLoadCoordinator snapshot(date)
                onProgress(ClassroomLoadProgress(loadedPages = 1, totalPages = 5))
                try {
                    gate.await()
                } finally {
                    if (!gate.isCompleted) cancelledDays += date
                }
            },
            onActiveFailure = {},
        )

        // 连续发起 B、C（占满 2 个联网配额）。
        activeDate = dayB
        coordinator.enqueue(this, dayB, filter, force = false)
        runCurrent()
        activeDate = dayC
        coordinator.enqueue(this, dayC, filter, force = false)
        runCurrent()

        // 滑回命中缓存的 A：不触发联网，不会把 B 或 C 挤掉。
        activeDate = dayA
        coordinator.enqueue(this, dayA, filter, force = false)
        runCurrent()
        assertTrue(cancelledDays.isEmpty())

        // 再滑到未缓存的 D：超过 2 个配额，淘汰最老的 B，保留 C 和 D。
        activeDate = dayD
        coordinator.enqueue(this, dayD, filter, force = false)
        runCurrent()

        assertEquals(setOf(dayB), cancelledDays)
        assertNull(coordinator.loads.value[classroomRequestKey(dayB, filter)])
        assertTrue(coordinator.loads.value[classroomRequestKey(dayC, filter)] is ActiveLoad.Loading)
        assertTrue(coordinator.loads.value[classroomRequestKey(dayD, filter)] is ActiveLoad.Loading)

        gates.getValue(dayC).complete(snapshot(dayC))
        gates.getValue(dayD).complete(snapshot(dayD))
        runCurrent()
    }

    @Test
    fun failure_reportsOnlyWhenStillActiveAndSilentlyClearsBackgroundFailure() = runTest {
        var activeDate = dayB
        val gateB = CompletableDeferred<ClassroomDaySnapshot>()
        val gateC = CompletableDeferred<ClassroomDaySnapshot>()
        val reportedFailures = mutableListOf<Exception>()
        val coordinator = ClassroomLoadCoordinator(
            maxConcurrentLoads = 3,
            currentActiveKey = { classroomRequestKey(activeDate, filter) },
            loadDay = { date, _, _, onProgress ->
                onProgress(ClassroomLoadProgress(loadedPages = 0, totalPages = null))
                when (date) {
                    dayB -> gateB.await()
                    dayC -> gateC.await()
                    else -> snapshot(date)
                }
            },
            onActiveFailure = { reportedFailures += it },
        )

        // 在 B 启动后滑回 A，随后 B 在后台失败：静默清除状态且不弹错。
        coordinator.enqueue(this, dayB, filter, force = false)
        runCurrent()
        activeDate = dayA
        gateB.completeExceptionally(IOException("timeout"))
        runCurrent()

        assertNull(coordinator.loads.value[classroomRequestKey(dayB, filter)])
        assertTrue(reportedFailures.isEmpty())

        // 停留在 C 时失败：进入 Failed 并通知前台报错。
        activeDate = dayC
        coordinator.enqueue(this, dayC, filter, force = false)
        runCurrent()
        gateC.completeExceptionally(IOException("network down"))
        runCurrent()

        assertEquals(
            ActiveLoad.Failed(classroomRequestKey(dayC, filter)),
            coordinator.loads.value[classroomRequestKey(dayC, filter)],
        )
        assertEquals(1, reportedFailures.size)
        assertFalse(reportedFailures.isEmpty())
    }

    private fun snapshot(date: LocalDate) = ClassroomDaySnapshot(
        date = date,
        fetchedAt = fetchedAt,
        items = emptyList(),
    )
}

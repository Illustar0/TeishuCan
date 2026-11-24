package com.illustar0.teishucan.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshTrackerTest {
    @Test
    fun overlappingRefreshes_stayRefreshingUntilAllFinish() = runTest {
        val tracker = RefreshTracker()
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()

        assertFalse(tracker.isRefreshing.value)

        val firstJob = launch { tracker.track { first.await() } }
        val secondJob = launch { tracker.track { second.await() } }
        runCurrent()
        assertTrue(tracker.isRefreshing.value)

        first.complete(Unit)
        firstJob.join()
        assertTrue(tracker.isRefreshing.value)

        second.complete(Unit)
        secondJob.join()
        assertFalse(tracker.isRefreshing.value)
    }

    @Test
    fun failedAndCancelledRefreshes_resetRefreshingState() = runTest {
        val tracker = RefreshTracker()
        val gate = CompletableDeferred<Unit>()

        val failed = runCatching {
            tracker.track<Unit> { throw IllegalStateException("boom") }
        }
        assertEquals("boom", failed.exceptionOrNull()?.message)
        assertFalse(tracker.isRefreshing.value)

        val cancelledJob = async { tracker.track { gate.await() } }
        runCurrent()
        assertTrue(tracker.isRefreshing.value)

        cancelledJob.cancel(CancellationException("cancelled"))
        runCurrent()
        assertFalse(tracker.isRefreshing.value)
    }
}

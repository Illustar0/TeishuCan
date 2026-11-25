package com.illustar0.teishucan.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshQueueTest {
    @Test
    fun requestsDuringCancelledRefresh_runOnceAfterward() = runTest {
        val finishFirstRefresh = CompletableDeferred<Unit>()
        var calls = 0
        val queue = RefreshQueue {
            calls++
            if (calls == 1) {
                finishFirstRefresh.await()
                throw CancellationException("The first refresh was cancelled")
            }
        }

        backgroundScope.launch { queue.run() }
        queue.request()
        runCurrent()
        assertTrue(queue.isRefreshing.value)

        repeat(3) { queue.request() }
        runCurrent()
        assertEquals(1, calls)

        finishFirstRefresh.complete(Unit)
        runCurrent()
        assertEquals(2, calls)
        assertFalse(queue.isRefreshing.value)
    }

    @Test
    fun cancelledRun_leavesLaterRequestsForTheNextRun() = runTest {
        var calls = 0
        val queue = RefreshQueue { calls++ }

        val firstRun = backgroundScope.launch { queue.run() }
        runCurrent()
        firstRun.cancel()
        queue.request()
        runCurrent()
        assertEquals(0, calls)

        backgroundScope.launch { queue.run() }
        runCurrent()
        assertEquals(1, calls)
    }
}

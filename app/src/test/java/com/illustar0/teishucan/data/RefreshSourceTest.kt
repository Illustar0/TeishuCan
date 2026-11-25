package com.illustar0.teishucan.data

import com.illustar0.teishucan.concurrency.RefreshTracker
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshSourceTest {
    private val t0 = Instant.parse("2026-11-24T10:00:00Z")

    @Test
    fun staleTimestamp_handlesNullBoundsAndClockRollback() {
        val maxAge = 5.minutes

        assertTrue(isStaleTimestamp(fetchedAt = null, now = t0, maxAge = maxAge))
        assertFalse(isStaleTimestamp(fetchedAt = t0, now = t0, maxAge = maxAge))
        assertFalse(isStaleTimestamp(fetchedAt = t0, now = t0 + 5.minutes, maxAge = maxAge))
        assertTrue(isStaleTimestamp(fetchedAt = t0, now = t0 + 6.minutes, maxAge = maxAge))
        assertTrue(isStaleTimestamp(fetchedAt = t0, now = t0 - 1.minutes, maxAge = maxAge))
    }

    @Test
    fun failingSource_doesNotPoisonFreshnessOfOtherSources() = runTest {
        val cardTracker = RefreshTracker()
        val mailTracker = RefreshTracker()
        var cardFetchedAt: Instant? = null
        var mailFetchedAt: Instant? = null
        var cardCalls = 0
        var mailCalls = 0

        val card = RefreshSource(
            maxAge = 5.minutes,
            isRefreshing = cardTracker.isRefreshing,
            isStale = { now, maxAge -> isStaleTimestamp(cardFetchedAt, now, maxAge) },
            refresh = {
                cardTracker.track {
                    cardCalls++
                    cardFetchedAt = t0
                }
            },
        )
        val mail = RefreshSource(
            maxAge = 5.minutes,
            isRefreshing = mailTracker.isRefreshing,
            isStale = { now, maxAge -> isStaleTimestamp(mailFetchedAt, now, maxAge) },
            refresh = {
                mailTracker.track {
                    mailCalls++
                    throw IllegalStateException("TLS handshake failed")
                }
            },
        )

        card.refreshIfStale(backgroundScope, t0)
        mail.refreshIfStale(backgroundScope, t0)
        runCurrent()

        assertEquals(1, cardCalls)
        assertEquals(1, mailCalls)
        assertFalse(cardTracker.isRefreshing.value)
        assertFalse(mailTracker.isRefreshing.value)

        // 1 分钟后回到前台：校园卡仍然新鲜不再重刷，只有失败未更新 fetchedAt 的邮箱会重试。
        val later = t0 + 1.minutes
        card.refreshIfStale(backgroundScope, later)
        mail.refreshIfStale(backgroundScope, later)
        runCurrent()

        assertEquals(1, cardCalls)
        assertEquals(2, mailCalls)
    }

    @Test
    fun slowSource_doesNotBlockNewlyExpiredSourceAndDeduplicatesItself() = runTest {
        val mailGate = CompletableDeferred<Unit>()
        val cardTracker = RefreshTracker()
        val mailTracker = RefreshTracker()
        var cardFetchedAt: Instant? = t0
        var cardCalls = 0
        var mailCalls = 0

        val card = RefreshSource(
            maxAge = 5.minutes,
            isRefreshing = cardTracker.isRefreshing,
            isStale = { now, maxAge -> isStaleTimestamp(cardFetchedAt, now, maxAge) },
            refresh = {
                cardTracker.track {
                    cardCalls++
                    cardFetchedAt = t0 + 1.minutes
                }
            },
        )
        val mail = RefreshSource(
            maxAge = 5.minutes,
            isRefreshing = mailTracker.isRefreshing,
            isStale = { _, _ -> true },
            refresh = {
                mailTracker.track {
                    mailCalls++
                    mailGate.await()
                }
            },
        )

        card.refreshIfStale(backgroundScope, t0)
        mail.refreshIfStale(backgroundScope, t0)
        runCurrent()

        assertEquals(0, cardCalls)
        assertEquals(1, mailCalls)
        assertFalse(cardTracker.isRefreshing.value)
        assertTrue(mailTracker.isRefreshing.value)

        // 邮箱仍在阻塞时，校园卡因外部充值被标记过期并回到前台：校园卡应立即刷新，邮箱不重复发起。
        cardFetchedAt = null
        card.refreshIfStale(backgroundScope, t0 + 1.minutes)
        mail.refreshIfStale(backgroundScope, t0 + 1.minutes)
        runCurrent()

        assertEquals(1, cardCalls)
        assertEquals(1, mailCalls)
        assertFalse(cardTracker.isRefreshing.value)
        assertTrue(mailTracker.isRefreshing.value)

        mailGate.complete(Unit)
        runCurrent()
        assertFalse(mailTracker.isRefreshing.value)
    }

    @Test
    fun cancel_stopsInFlightAutoRefreshAndAllowsRestart() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val tracker = RefreshTracker()
        var calls = 0

        val source = RefreshSource(
            maxAge = 5.minutes,
            isRefreshing = tracker.isRefreshing,
            isStale = { _, _ -> true },
            refresh = {
                tracker.track {
                    calls++
                    if (calls == 1) firstGate.await()
                }
            },
        )

        source.refreshIfStale(backgroundScope, t0)
        runCurrent()
        assertEquals(1, calls)
        assertTrue(tracker.isRefreshing.value)

        source.cancel()
        runCurrent()
        assertFalse(tracker.isRefreshing.value)

        source.refreshIfStale(backgroundScope, t0)
        runCurrent()
        assertEquals(2, calls)
        assertFalse(tracker.isRefreshing.value)
    }
}

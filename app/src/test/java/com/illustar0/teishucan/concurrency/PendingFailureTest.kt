package com.illustar0.teishucan.concurrency

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PendingFailureTest {
    @Test
    fun capture_failingOperation_keepsFailureUntilAcknowledged() = runTest {
        val pending = PendingFailure()
        val failure = IOException("offline")

        pending.capture { throw failure }
        assertSame(failure, pending.failure.value)

        pending.acknowledge(failure)
        assertNull(pending.failure.value)
    }

    @Test
    fun acknowledge_olderFailure_keepsNewerOne() = runTest {
        val pending = PendingFailure()
        val older = IOException("first")
        val newer = IOException("second")

        pending.capture { throw older }
        pending.capture { throw newer }
        pending.acknowledge(older)

        assertSame(newer, pending.failure.value)
    }

    @Test
    fun capture_cancellation_propagatesWithoutRecording() {
        val pending = PendingFailure()

        assertThrows(CancellationException::class.java) {
            runBlocking { pending.capture { throw CancellationException("left the page") } }
        }
        assertNull(pending.failure.value)
    }
}

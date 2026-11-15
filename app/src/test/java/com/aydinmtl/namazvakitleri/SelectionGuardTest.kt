package com.aydinmtl.namazvakitleri

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SelectionGuardTest {
    @Test
    fun lateDownloadCannotOverwriteNewCity() {
        val guard = SelectionGuard()
        var city = "A"
        var times = "A"
        val started = CountDownLatch(1)
        val finishDownload = CountDownLatch(1)
        val done = CountDownLatch(1)
        var accepted = true
        Thread {
            val request = guard.capture { city }
            started.countDown()
            finishDownload.await()
            accepted = guard.apply(request.first) { times = request.second }
            done.countDown()
        }.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        guard.change { city = "B"; times = "B" }
        finishDownload.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertFalse(accepted)
        assertEquals("B", times)
    }

    @Test
    fun returningToSameCityDoesNotReviveOldRequest() {
        val guard = SelectionGuard()
        val request = guard.capture { "A" }
        guard.change { }
        guard.change { }
        assertFalse(guard.apply(request.first) { fail("stale request applied") })
    }

    @Test
    fun currentRequestIsAcceptedAndCancelledRequestIsRejected() {
        val guard = SelectionGuard()
        val token = guard.capture { "A" }.first
        var written = false
        assertTrue(guard.apply(token) { written = true })
        assertTrue(written)
        guard.change { }
        assertFalse(guard.apply(token) { fail("cancelled request applied") })
    }
}

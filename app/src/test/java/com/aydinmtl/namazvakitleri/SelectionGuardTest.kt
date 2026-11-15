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
        guard.changePlace { city = "B"; times = "B" }
        finishDownload.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertFalse(accepted)
        assertEquals("B", times)
    }

    @Test
    fun returningToSameCityDoesNotReviveOldRequest() {
        val guard = SelectionGuard()
        val request = guard.capture { "A" }
        guard.changePlace { }
        guard.changePlace { }
        assertFalse(guard.apply(request.first) { fail("stale request applied") })
    }

    @Test
    fun currentRequestIsAcceptedAndCancelledRequestIsRejected() {
        val guard = SelectionGuard()
        val token = guard.capture { "A" }.first
        var written = false
        assertTrue(guard.apply(token) { written = true })
        assertTrue(written)
        guard.changePlace { }
        assertFalse(guard.apply(token) { fail("cancelled request applied") })
    }

    @Test
    fun settingChangeDoesNotDiscardRunningPlaceRequest() {
        // Bildirim ayarı, hesap yöntemi gibi değişiklikler yeri değiştirmez: süren iş sonucunu yazabilmeli.
        val guard = SelectionGuard()
        val request = guard.capture { "A" }
        guard.change { }
        guard.change { }
        assertTrue(guard.isCurrentRequest(request.first))
        var written = false
        assertTrue(guard.apply(request.first) { written = true })
        assertTrue(written)
    }

    @Test
    fun acceptedResultForSamePlaceDoesNotDiscardAnotherRunningRequest() {
        // İki iş aynı yer için sürüyor (ör. vakit yenileme ve koordinat bulma): ikisi de yazabilmeli.
        val guard = SelectionGuard()
        val first = guard.capture { "A" }
        val second = guard.capture { "A" }
        assertTrue(guard.apply(first.first) {})
        assertTrue(guard.apply(second.first) {})
    }

    @Test
    fun placeChangeInsideAcceptedResultDiscardsOtherRunningRequests() {
        // Bir iş yeri değiştirdiyse (saveLocation = changePlace), eski yer için süren öteki iş yazamaz.
        val guard = SelectionGuard()
        val moving = guard.capture { "A" }
        val refreshOfOldPlace = guard.capture { "A" }
        assertTrue(guard.apply(moving.first) { guard.changePlace { } })
        assertFalse(guard.apply(refreshOfOldPlace.first) { fail("old place result applied") })
    }

    @Test
    fun conditionalWriteChecksConditionUnderLockAndInvalidatesAlarmData() {
        val guard = SelectionGuard()
        var district = "A"
        var times = "old"
        val alarm = guard.captureData { times }
        assertTrue(guard.changeIf({ district == "A" }) { times = "new A" })
        assertEquals("new A", times)
        assertFalse(guard.ifCurrent(alarm.first) { fail("alarm computed from old times published") })
        // İlçe değiştiyse yazılmaz ve alarm kaydı geçerli kalır.
        guard.changePlace { district = "B"; times = "B" }
        val alarmB = guard.captureData { times }
        assertFalse(guard.changeIf({ district == "A" }) { fail("times of old district written") })
        assertEquals("B", times)
        assertTrue(guard.ifCurrent(alarmB.first) {})
    }
}

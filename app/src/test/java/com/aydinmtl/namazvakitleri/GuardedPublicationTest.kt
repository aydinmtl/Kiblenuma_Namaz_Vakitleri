package com.aydinmtl.namazvakitleri

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardedPublicationTest {
    @Test
    fun oldCityEventCannotReplaceNewCityAlarm() = stalePublication("A event", changeCity = true)
    @Test
    fun oldEmptyTimetableCannotCancelNewCityAlarm() = stalePublication(null, changeCity = true)
    @Test
    fun refreshedSameCityDataInvalidatesOldEvent() = stalePublication("A event", changeCity = false)
    @Test
    fun refreshedSameCityDataInvalidatesOldCancellation() = stalePublication(null, changeCity = false)

    private fun stalePublication(oldEvent: String?, changeCity: Boolean) {
        val guard = SelectionGuard()
        val publisher = GuardedPublication(guard)
        var city = "A"
        var event = oldEvent
        var installed: String? = "previous"
        val computed = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val accepted = AtomicReference<Boolean>()
        val old = Thread {
            try {
                accepted.set(publisher.run(
                    capture = { guard.capture { city to event } },
                    compute = { state ->
                        computed.countDown()
                        check(resume.await(5, TimeUnit.SECONDS))
                        state.second
                    },
                    publish = { installed = it },
                ))
            } catch (t: Throwable) { failure.set(t) }
        }
        old.start()
        try {
            assertTrue(computed.await(5, TimeUnit.SECONDS))
            if (changeCity) guard.change { city = "B"; event = "new event" }
            else assertTrue(guard.apply(guard.capture { city }.first) { event = "new event" })
            assertTrue(publisher.run(
                capture = { guard.capture { city to event } },
                compute = { it.second },
                publish = { installed = it },
            ))
            assertEquals("new event", installed)
        } finally {
            resume.countDown()
            old.join(6000)
        }
        assertFalse(old.isAlive)
        failure.get()?.let { throw AssertionError(it) }
        assertEquals(false, accepted.get())
        assertEquals("new event", installed)
    }

    @Test
    fun laterSchedulingCallForSameSnapshotWinsOverEarlierComputation() {
        val guard = SelectionGuard()
        val publisher = GuardedPublication(guard)
        var installed = "initial"
        val accepted = publisher.run(
            capture = { guard.capture { "same data" } },
            compute = {
                assertTrue(publisher.run(
                    capture = { guard.capture { "same data" } },
                    compute = { "later event" },
                    publish = { installed = it },
                ))
                "earlier event"
            },
            publish = { installed = it },
        )
        assertFalse(accepted)
        assertEquals("later event", installed)
    }
}

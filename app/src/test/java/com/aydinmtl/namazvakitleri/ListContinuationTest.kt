package com.aydinmtl.namazvakitleri

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diyanet listesi bitince (uzun süre internet yok) vakitler boş kalmamalı: yerin koordinatı
 * biliniyorsa devamı Diyanet ölçüleriyle hesaplanır ve "hesaplandı" diye işaretlenir.
 */
class ListContinuationTest {
    private val zurich = ZoneId.of("Europe/Zurich")
    private val today = LocalDate.of(2026, 11, 15)
    private val geneva = 46.2044 to 6.1432

    /** [first] gününden başlayan [days] günlük Diyanet listesi (her gün aynı saatler). */
    private fun list(first: LocalDate, days: Int, noon: String = "12:24"): String =
        (0 until days).joinToString(",", "[", "]") { i ->
            val d = first.plusDays(i.toLong())
            val date = "%02d.%02d.%04d".format(d.dayOfMonth, d.monthValue, d.year)
            """{"MiladiTarihKisa":"$date","Imsak":"05:58","Gunes":"07:39","Ogle":"$noon","Ikindi":"14:43","Aksam":"17:05","Yatsi":"18:39"}"""
        }

    private fun snapshot(json: String?, point: Pair<Double, Double>? = geneva) =
        PrayerSnapshot("14550", json, point, CalcMethod.DIYANET, false)

    private fun reader() = PrayerTimeReader { "hicri" }

    @Test
    fun listThatCoversTheNextDaysIsUsedUnchanged() {
        val days = reader().days(snapshot(list(today.minusDays(2), 10)), today, zurich)
        assertEquals(10, days.size)
        assertTrue(days.none { it.computed })
    }

    @Test
    fun whenListEndedYesterdayTodayIsCalculatedAndMarked() {
        val days = reader().days(snapshot(list(today.minusDays(10), 10)), today, zurich)
        val listed = days.filter { !it.computed }
        val computed = days.filter { it.computed }
        assertEquals(10, listed.size)
        assertEquals(today.minusDays(1), listed.last().date)
        assertEquals(today, computed.first().date)
        assertEquals(today.plusDays(30), computed.last().date)
        // Hesaplanan gün, Diyanet ölçüleriyle yapılan hesabın aynısıdır.
        val expected = PrayerCalc.calculatedDay(today, geneva.first, geneva.second, zurich, CalcMethod.DIYANET, 1)!!
        assertEquals(expected.instants, computed.first().instants)
        assertEquals(expected.times, computed.first().times)
        // Günler tarih sırasında ve tekrarsız
        assertEquals(days.map { it.date }, days.map { it.date }.distinct().sorted())
    }

    @Test
    fun whenListEndsTodayNextPrayerAfterIshaIsTomorrowsCalculatedFajr() {
        val days = reader().days(snapshot(list(today.minusDays(5), 6)), today, zurich)
        assertFalse(Prayers.day(days, today)!!.computed) // bugün hâlâ resmi listeden
        val lateEvening = today.atTime(23, 0).atZone(zurich).toInstant()
        val next = Prayers.next(days, lateEvening)
        assertNotNull("yatsıdan sonra sıradaki vakit boş kalmamalı", next)
        assertEquals(0, next!!.index)
        assertEquals(today.plusDays(1), next.day)
        assertTrue(Prayers.day(days, next.day)!!.computed)
    }

    @Test
    fun withoutCoordinatesNothingIsCalculated() {
        val days = reader().days(snapshot(list(today.minusDays(10), 10), point = null), today, zurich)
        assertEquals(10, days.size)
        assertTrue(days.none { it.computed })
    }

    @Test
    fun missingListWithCoordinatesIsFullyCalculated() {
        val days = reader().days(snapshot(null), today, zurich)
        assertEquals(32, days.size) // dün … 30 gün sonrası
        assertTrue(days.all { it.computed })
        assertEquals(today.minusDays(1), days.first().date)
    }

    @Test
    fun placeInAnotherTimeZoneKeepsThePlaceClockForCalculatedDays() {
        // Telefon Zürih saatinde, seçili yer İstanbul (UTC+3). Listedeki saatler İstanbul saatidir;
        // hesaplanan devam günleri de İstanbul saatiyle yazılmalı (10:5x değil 12:5x).
        val istanbul = 41.0082 to 28.9784
        val days = reader().days(snapshot(list(today.minusDays(10), 10, noon = "12:54"), istanbul), today, zurich)
        val listed = days.last { !it.computed }
        val computed = Prayers.day(days, today)!!
        assertTrue(computed.computed)
        assertEquals(-120, listed.shiftMinutes) // telefon +1, yer +3
        assertEquals(-120, computed.shiftMinutes)
        assertEquals(12, computed.times[2].hour)
        // Gerçek an değişmez: öğle İstanbul'da 12:5x = UTC 09:5x
        assertEquals(9, computed.instants!![2].atZone(ZoneId.of("UTC")).hour)
    }
}

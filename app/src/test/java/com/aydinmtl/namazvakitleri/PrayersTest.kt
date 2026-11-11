package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Prayers.next (sıradaki vakit) ve Prayers.current (içinde bulunulan vakit). */
class PrayersTest {

    private fun day(date: LocalDate, vararg times: String) =
        PrayerDay(date, times.map { LocalTime.parse(it) }, "")

    private val d1 = LocalDate.of(2026, 11, 11)
    private val d2 = d1.plusDays(1)
    private val days = listOf(
        // Sırasız verilse de doğru çalışmalı
        day(d2, "06:01", "07:31", "12:21", "14:51", "17:01", "18:26"),
        day(d1, "06:00", "07:30", "12:20", "14:50", "17:00", "18:25"),
    )

    private fun at(date: LocalDate, hhmm: String) = LocalDateTime.of(date, LocalTime.parse(hhmm))

    @Test
    fun ogleden_once_siradaki_ogle() {
        val n = Prayers.next(days, at(d1, "10:00"))!!
        assertEquals("Öğle", n.name)
        assertEquals(at(d1, "12:20"), n.at)
        assertEquals("Güneş", Prayers.current(days, at(d1, "10:00"))!!.name)
    }

    @Test
    fun yatsidan_sonra_siradaki_ertesi_gunun_imsaki() {
        val n = Prayers.next(days, at(d1, "23:00"))!!
        assertEquals(0, n.index)
        assertEquals(at(d2, "06:01"), n.at)
        assertEquals(at(d1, "18:25"), Prayers.current(days, at(d1, "23:00"))!!.at)
    }

    @Test
    fun tam_vakit_aninda_vakit_girmis_sayilir() {
        // 12:20:00'de öğle girmiştir: current = Öğle, next = İkindi
        assertEquals("Öğle", Prayers.current(days, at(d1, "12:20"))!!.name)
        assertEquals("İkindi", Prayers.next(days, at(d1, "12:20"))!!.name)
    }

    @Test
    fun ilk_imsaktan_once_current_yok() {
        assertNull(Prayers.current(days, at(d1, "05:00")))
        assertEquals("İmsak", Prayers.next(days, at(d1, "05:00"))!!.name)
    }

    @Test
    fun veri_bitince_next_yok() {
        assertNull(Prayers.next(days, at(d2, "19:00")))
        assertNull(Prayers.next(emptyList(), at(d1, "12:00")))
    }

    @Test
    fun moments_zaman_sirasinda() {
        val m = Prayers.moments(days)
        assertEquals(12, m.size)
        assertEquals(m.sortedBy { it.at }, m)
    }

    @Test
    fun day_bulma() {
        assertEquals(d2, Prayers.day(days, d2)!!.date)
        assertNull(Prayers.day(days, d1.minusDays(1)))
    }

    @Test
    fun sure_bicimi() {
        assertEquals("01:02:05", formatDuration(3725))
        assertEquals("00:00:00", formatDuration(-5))
        assertEquals("25:00:00", formatDuration(90000))
    }
}

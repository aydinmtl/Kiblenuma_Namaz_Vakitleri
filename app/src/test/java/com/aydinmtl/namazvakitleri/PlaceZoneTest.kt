package com.aydinmtl.namazvakitleri

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Yerin saat dilimini Diyanet'in öğle saatinden bulma (PlaceZone).
 * Öğle saatleri Diyanet servisinden alınan gerçek değerlerdir (Kasım 2026).
 */
class PlaceZoneTest {

    private val cenevreLon = 6.1432
    private val istanbulLon = 28.9784
    private val zurich = ZoneId.of("Europe/Zurich")
    private val istanbulZone = ZoneId.of("Europe/Istanbul")

    @Test
    fun cenevre_kisin_utc_arti_1() {
        // Diyanet, CENEVRE, 07.11.2026: Öğle 12:24
        assertEquals(60, PlaceZone.offsetMinutes(LocalDate.of(2026, 11, 7), LocalTime.of(12, 24), cenevreLon))
    }

    @Test
    fun istanbul_utc_arti_3() {
        // Diyanet, İSTANBUL, 08.11.2026: Öğle 12:53
        assertEquals(180, PlaceZone.offsetMinutes(LocalDate.of(2026, 11, 8), LocalTime.of(12, 53), istanbulLon))
    }

    @Test
    fun cenevre_yazin_utc_arti_2() {
        // Yaz saatinde öğle bir saat ileri kayar (21 Haziran'da ~13:42)
        assertEquals(120, PlaceZone.offsetMinutes(LocalDate.of(2026, 6, 21), LocalTime.of(13, 42), cenevreLon))
    }

    @Test
    fun tutarsiz_saat_guvenilmez() {
        // Öğle 12:32: en yakın 15 dakikalık farka 8 dk uzak -> güvenilmez
        assertNull(PlaceZone.offsetMinutes(LocalDate.of(2026, 11, 7), LocalTime.of(12, 32), cenevreLon))
    }

    @Test
    fun ayni_saat_diliminde_fark_yok() {
        assertEquals(0, PlaceZone.shiftMinutes(LocalDate.of(2026, 11, 7), LocalTime.of(12, 24), cenevreLon, zurich))
        assertEquals(0, PlaceZone.shiftMinutes(LocalDate.of(2026, 11, 8), LocalTime.of(12, 53), istanbulLon, istanbulZone))
    }

    @Test
    fun cenevredeyken_istanbul_iki_saat_geri() {
        assertEquals(-120, PlaceZone.shiftMinutes(LocalDate.of(2026, 11, 8), LocalTime.of(12, 53), istanbulLon, zurich))
    }

    @Test
    fun istanbuldayken_cenevre_yazin_bir_saat_ileri() {
        // Yazın İsviçre +2, Türkiye +3: Cenevre'nin 13:42'si İstanbul'da 14:42
        assertEquals(60, PlaceZone.shiftMinutes(LocalDate.of(2026, 6, 21), LocalTime.of(13, 42), cenevreLon, istanbulZone))
    }

    @Test
    fun alarm_ani_telefonun_saatine_cevrilir_ekranda_yerin_saati_kalir() {
        val d = LocalDate.of(2026, 11, 8)
        // Diyanet, İSTANBUL, 08.11.2026
        val times = listOf("06:09", "07:36", "12:53", "15:34", "18:00", "19:21").map { LocalTime.parse(it) }
        val days = PlaceZone.apply(listOf(PrayerDay(d, times, "")), istanbulLon, zurich)
        assertEquals(-120, days[0].shiftMinutes)
        val ogle = Prayers.moments(days).first { it.index == 2 }
        assertEquals(LocalDateTime.of(d, LocalTime.of(10, 53)), ogle.at) // Cenevre saatiyle gerçek an
        assertEquals(LocalTime.of(12, 53), ogle.clock)                    // ekranda Diyanet'in saati
        // Cenevre'de saat 11:00 iken sıradaki vakit artık ikindi olmalı (İstanbul'da 13:00)
        assertEquals(3, Prayers.next(days, LocalDateTime.of(d, LocalTime.of(11, 0)))!!.index)
    }
}

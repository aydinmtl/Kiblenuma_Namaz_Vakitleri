package com.aydinmtl.namazvakitleri

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dini günler listesi. Uygulama Android'in Ümmü'l-Kurâ takvimini kullanır; bilgisayarda o yok.
 * Java'nın kendi Ümmü'l-Kurâ takvimi (HijrahDate) aynı hesabı yapar, testte onu veriyoruz.
 *
 * Not: Diyanet takvimi Ümmü'l-Kurâ'dan bazen 1 gün sapar (ör. Ramazan 1447 başlangıcı);
 * uygulama bunu Diyanet verisindeki Hicri tarihle hizalar. Bu test hizalamasız hesabı dener.
 */
class HijriTest {

    private val umalqura: (LocalDate) -> Hijri.HDate = { d ->
        val h = HijrahDate.from(d)
        Hijri.HDate(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    }

    private val list = Hijri.holyDays(LocalDate.of(2026, 1, 1), 430, umalqura)

    private fun dateOf(name: String): LocalDate =
        list.firstOrNull { it.name == name }?.date ?: throw AssertionError("$name listede yok: $list")

    @Test
    fun bayramlar() {
        assertEquals(LocalDate.of(2026, 3, 19), dateOf("Ramazan Bayramı Arefesi"))
        assertEquals(LocalDate.of(2026, 3, 20), dateOf("Ramazan Bayramı 1. Gün"))
        assertEquals(LocalDate.of(2026, 3, 22), dateOf("Ramazan Bayramı 3. Gün"))
        assertEquals(LocalDate.of(2026, 5, 26), dateOf("Kurban Bayramı Arefesi"))
        assertEquals(LocalDate.of(2026, 5, 27), dateOf("Kurban Bayramı 1. Gün"))
        assertEquals(LocalDate.of(2026, 5, 30), dateOf("Kurban Bayramı 4. Gün"))
    }

    @Test
    fun hicri_yilbasi_ve_asure() {
        assertEquals(LocalDate.of(2026, 6, 16), dateOf("Hicri Yılbaşı (1448)"))
        assertEquals(LocalDate.of(2026, 6, 25), dateOf("Aşure Günü"))
    }

    @Test
    fun kandiller_bir_onceki_gunun_aksami() {
        // 12 Rebiülevvel 1448 = 25 Ağustos 2026 -> Mevlid Kandili 24 Ağustos gecesi
        val mevlid = list.first { it.name == "Mevlid Kandili" }
        assertEquals(LocalDate.of(2026, 8, 24), mevlid.date)
        assertTrue(mevlid.night)
        // 27 Recep 1448 = 5 Ocak 2027 -> Miraç Kandili 4 Ocak gecesi
        // (listede 1447 yılının Miraç Kandili de var: 15 Ocak 2026)
        assertEquals(
            listOf(LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 4)),
            list.filter { it.name == "Miraç Kandili" }.map { it.date },
        )
    }

    @Test
    fun regaib_recebin_ilk_cuma_gecesi() {
        // 1 Recep 1448 = 10 Aralık 2026 Perşembe; ilk cuma 11 Aralık -> kandil 10 Aralık gecesi
        val regaib = dateOf("Regaib Kandili")
        assertEquals(LocalDate.of(2026, 12, 10), regaib)
        assertEquals(DayOfWeek.THURSDAY, regaib.dayOfWeek)
    }

    @Test
    fun ramazan_baslangici_1448() {
        val r = list.filter { it.name == "Ramazan Başlangıcı" }.map { it.date }
        assertTrue(LocalDate.of(2027, 2, 8) in r)
    }

    @Test
    fun her_gun_bir_kez_ve_sirali() {
        assertEquals(list.sortedBy { it.date }, list)
        assertEquals(3, list.count { it.name.startsWith("Ramazan Bayramı") && it.name.endsWith("Gün") && it.date.year == 2026 })
        assertEquals(4, list.count { it.name.startsWith("Kurban Bayramı") && it.name.endsWith("Gün") && it.date.year == 2026 })
    }

    // ---- Diyanet listesiyle hizalama (Hijri.aligned) ----

    /** Test takvimi: Java'nın Ümmü'l-Kurâ takvimi, istenen gün kadar kaydırılmış. */
    private val shifted: (LocalDate, Long) -> Hijri.HDate = { d, shift -> umalqura(d.plusDays(shift)) }

    /** Diyanet'in takvimi hesap takviminden 1 gün ileride olan beş günlük bir liste. */
    private fun listOneDayAhead(start: LocalDate): List<PrayerDay> = (0L..4L).map { i ->
        val d = start.plusDays(i)
        val h = umalqura(d.plusDays(1))
        PrayerDay(d, emptyList(), "", "${h.day}.${h.month}.${h.year}")
    }

    @Test
    fun listedeki_gunlerde_Diyanetin_kendi_hicri_tarihi_kullanilir() {
        val start = LocalDate.of(2026, 11, 10)
        val hijriOf = Hijri.aligned(listOneDayAhead(start), shifted)
        assertEquals(umalqura(start.plusDays(3)), hijriOf(start.plusDays(2)))
    }

    @Test
    fun liste_disindaki_gunler_en_yakin_uctaki_farkla_kaydirilir() {
        val start = LocalDate.of(2026, 11, 10)
        val hijriOf = Hijri.aligned(listOneDayAhead(start), shifted)
        // Listeden sonra: son günün farkı (+1)
        assertEquals(umalqura(start.plusDays(41)), hijriOf(start.plusDays(40)))
        // Listeden önce: ilk günün farkı (+1)
        assertEquals(umalqura(start.minusDays(4)), hijriOf(start.minusDays(5)))
    }

    @Test
    fun liste_yoksa_hesap_takvimi_kullanilir() {
        Hijri.offsetDays = 0
        val d = LocalDate.of(2026, 11, 10)
        assertEquals(umalqura(d), Hijri.aligned(emptyList(), shifted)(d))
    }

    @Test
    fun kisa_hicri_tarih_ayristirilir() {
        assertEquals(Hijri.HDate(1448, 6, 2), Hijri.parseShort("2.6.1448"))
        assertEquals(null, Hijri.parseShort(""))
        assertEquals(null, Hijri.parseShort("2.13.1448"))
    }
}

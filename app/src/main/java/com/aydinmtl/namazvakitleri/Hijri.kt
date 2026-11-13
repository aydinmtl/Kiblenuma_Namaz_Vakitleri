package com.aydinmtl.namazvakitleri

import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Hicri takvim. Android'in yerleşik Ümmü'l-Kurâ takvimini kullanır; Diyanet verisi varsa
 * ondaki Hicri tarihe göre gün kaydırması yapılarak Diyanet takvimiyle hizalanır.
 */
object Hijri {
    val MONTHS = listOf(
        "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir",
        "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce",
    )

    data class HDate(val year: Int, val month: Int, val day: Int) // month: 1..12

    /** Diyanet takvimiyle aradaki gün farkı (genelde 0, bazen ±1). */
    @Volatile
    var offsetDays: Long = 0

    fun of(date: LocalDate): HDate = ofShifted(date, offsetDays)

    /** [date] için Hicri tarih, [shift] gün kaydırarak. Ortak [offsetDays] değişkenine dokunmaz. */
    private fun ofShifted(date: LocalDate, shift: Long): HDate {
        val cal = IslamicCalendar()
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        val d = date.plusDays(shift)
        cal.timeInMillis = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return HDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    fun format(date: LocalDate): String {
        val h = of(date)
        return "${h.day} ${MONTHS[h.month - 1]} ${h.year}"
    }

    /**
     * Diyanet'in bir gün için verdiği kısa Hicri tarihle ("2.6.1448" gibi) karşılaştırıp
     * gün kaydırmasını bulur.
     */
    fun alignWithDiyanet(date: LocalDate, diyanetShort: String) {
        val m = Regex("(\\d{1,2})\\D(\\d{1,2})\\D(\\d{4})").find(diyanetShort) ?: return
        val target = HDate(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        // Denemeler ortak değişkeni değiştirmeden yapılır; yalnızca bulunan sonuç bir kez yazılır.
        // (Eskiden döngüde geçici olarak değiştiriliyordu; aynı anda tarih okuyan başka bir
        // iş parçacığı bir an yanlış gün görebiliyordu.)
        val k = longArrayOf(0, 1, -1, 2, -2).firstOrNull { ofShifted(date, it) == target } ?: return
        offsetDays = k
    }

    // ---------------- Dini günler ----------------

    /** Dini günün türü: ekranda seçili dildeki adı bununla bulunur (bkz. Lang.holyDayName). */
    enum class HolyKind { NEW_YEAR, ASHURA, THREE_MONTHS, RAMADAN_START, ADHA_EVE, MAWLID, MIRAJ, BARAAT, QADR, FITR_EVE, RAGHAIB, FITR_DAY, ADHA_DAY }

    /**
     * [name]: Türkçe adı (testler ve Türkçe ekran bunu kullanır).
     * [kind] ve [n]: öteki dillerde adı kurmak için tür ve sayı (bayramın kaçıncı günü, Hicri yıl).
     */
    data class HolyDay(
        val date: LocalDate,
        val name: String,
        val night: Boolean,
        val kind: HolyKind = HolyKind.NEW_YEAR,
        val n: Int = 0,
    )

    /**
     * Bugünden itibaren [days] gün içindeki dini günler.
     * Kandiller "gecesi" kutlandığı için Diyanet gibi bir önceki günün tarihiyle verilir.
     *
     * [hijriOf]: Miladi tarihi Hicri'ye çeviren fonksiyon. Uygulamada her zaman [of] kullanılır;
     * birim testleri Android olmadan çalışabilsin diye dışarıdan verilebilir.
     */
    fun holyDays(from: LocalDate, days: Int = 385, hijriOf: (LocalDate) -> HDate = ::of): List<HolyDay> {
        val out = ArrayList<HolyDay>()
        var h = hijriOf(from)
        for (i in 0 until days) {
            val d = from.plusDays(i.toLong())
            val next = hijriOf(d.plusDays(1))
            val md = h.month to h.day
            val nmd = next.month to next.day

            when (md) {
                1 to 1 -> out.add(HolyDay(d, "Hicri Yılbaşı (${h.year})", false, HolyKind.NEW_YEAR, h.year))
                1 to 10 -> out.add(HolyDay(d, "Aşure Günü", false, HolyKind.ASHURA))
                7 to 1 -> out.add(HolyDay(d, "Üç Ayların Başlangıcı", false, HolyKind.THREE_MONTHS))
                9 to 1 -> out.add(HolyDay(d, "Ramazan Başlangıcı", false, HolyKind.RAMADAN_START))
                12 to 9 -> out.add(HolyDay(d, "Kurban Bayramı Arefesi", false, HolyKind.ADHA_EVE))
            }
            when (nmd) {
                3 to 12 -> out.add(HolyDay(d, "Mevlid Kandili", true, HolyKind.MAWLID))
                7 to 27 -> out.add(HolyDay(d, "Miraç Kandili", true, HolyKind.MIRAJ))
                8 to 15 -> out.add(HolyDay(d, "Berat Kandili", true, HolyKind.BARAAT))
                9 to 27 -> out.add(HolyDay(d, "Kadir Gecesi", true, HolyKind.QADR))
                10 to 1 -> out.add(HolyDay(d, "Ramazan Bayramı Arefesi", false, HolyKind.FITR_EVE))
            }
            // Regaib: Recep ayının ilk cuma gecesi (perşembeyi cumaya bağlayan gece)
            if (d.dayOfWeek == DayOfWeek.THURSDAY && next.month == 7 && next.day <= 7) {
                out.add(HolyDay(d, "Regaib Kandili", true, HolyKind.RAGHAIB))
            }
            if (h.month == 10 && h.day in 1..3) out.add(HolyDay(d, "Ramazan Bayramı ${h.day}. Gün", false, HolyKind.FITR_DAY, h.day))
            if (h.month == 12 && h.day in 10..13) out.add(HolyDay(d, "Kurban Bayramı ${h.day - 9}. Gün", false, HolyKind.ADHA_DAY, h.day - 9))

            h = next
        }
        return out
    }
}

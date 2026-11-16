package com.aydinmtl.namazvakitleri

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Seçilen yerin saat dilimini Diyanet'in öğle saatinden bulur.
 *
 * Sorun: Diyanet vakitleri o yerin KENDİ saatiyle verir (Cenevre için İsviçre saati, İstanbul
 * için Türkiye saati). Listedeki "GreenwichOrtalamaZamani" alanı her yerde 3 yazdığı için işe
 * yaramaz. Telefon ise kendi saat dilimini kullanır. Cenevre'deyken İstanbul seçilirse alarm
 * ve geri sayım 2 saat kayardı.
 *
 * Çözüm: Öğle vakti, güneşin en yükseğe çıktığı andan birkaç dakika sonradır (Diyanet'te ~5 dk).
 * Güneşin en yükseğe çıktığı anı yerin boylamından UTC olarak hesaplayabiliriz. Diyanet'in öğle
 * saati ile bu an arasındaki fark, yerin UTC farkını verir (gerçek bir saat dilimi farkına
 * yuvarlanır: tam ya da yarım saat, birkaç yerde 45 dakika).
 * Yaz saati de kendiliğinden hesaba girer, çünkü Diyanet'in saatleri yaz saatine göre değişir.
 *
 * Android'e bağlı değil; bilgisayarda test edilir (bkz. PlaceZoneTest).
 */
object PlaceZone {

    /** Diyanet'in öğle vaktine eklediği yaklaşık pay (dakika). */
    private const val DHUHR_MARGIN = 5.0

    /** Hesap ile Diyanet arasında bundan fazla tutarsızlık varsa sonuca güvenilmez. */
    private const val TOLERANCE = 6.0

    /**
     * Dünyadaki saat dilimleri UTC'den tam ya da yarım saat farklıdır. Çeyrek saatli olan yalnızca
     * şunlardır (dakika): Nepal +5:45, Batı Avustralya'da Eucla +8:45, Chatham Adaları +12:45
     * (yazın +13:45).
     */
    private val QUARTER_HOUR_OFFSETS = listOf(345, 525, 765, 825)

    /**
     * Yerin o günkü UTC farkı (dakika; ör. İsviçre kışın +60, Türkiye +180).
     * [dhuhr]: Diyanet'in öğle saati, [lon]: yerin boylamı.
     * Hesap tutarsızsa (ör. yanlış koordinat) null.
     *
     * Fark yalnızca GERÇEKTEN var olan saat dilimi farklarına yuvarlanır. Eskiden her 15 dakikalık
     * değer kabul ediliyordu; yer adından bulunan koordinat yanlışsa (aynı adlı başka bir yer)
     * +1:15 gibi var olmayan bir fark "tutarlı" sayılıp alarmlar 15 dakika kayabiliyordu. Şimdi
     * böyle bir sonuç reddedilir (null) ve yer, telefonla aynı saat diliminde sayılır.
     */
    fun offsetMinutes(date: LocalDate, dhuhr: LocalTime, lon: Double): Int? {
        val noonUtc = PrayerCalc.solarNoonUtc(date, lon) * 60.0
        var diff = dhuhr.toSecondOfDay() / 60.0 - noonUtc - DHUHR_MARGIN
        // Gün sınırını aşan durumlar için farkı -12..+14 saat aralığına getir
        while (diff < -12 * 60) diff += 24 * 60
        while (diff > 14 * 60) diff -= 24 * 60
        val halfHour = (diff / 30.0).roundToInt() * 30
        val rounded = (QUARTER_HOUR_OFFSETS + halfHour).minByOrNull { abs(diff - it) } ?: halfHour
        if (abs(diff - rounded) > TOLERANCE) return null
        return rounded
    }

    /**
     * Telefonun saati ile yerin saati arasındaki fark (dakika): yerin saatine bu eklenince
     * telefonun saati elde edilir. Aynı saat dilimindeyse ya da hesap güvenilmezse 0.
     */
    fun shiftMinutes(date: LocalDate, dhuhr: LocalTime, lon: Double, phone: ZoneId): Int {
        val place = offsetMinutes(date, dhuhr, lon) ?: return 0
        val instant = date.atTime(dhuhr).toInstant(ZoneOffset.ofTotalSeconds(place * 60))
        val phoneOffset = phone.rules.getOffset(instant).totalSeconds / 60
        return phoneOffset - place
    }

    /**
     * Her vaktin gerçek anını hesaplar; telefonun UTC farkı o anda kurallardan okunur.
     * Sağlayıcı IANA saat dilimi vermediği için yerin UTC farkı günlük öğle tahminidir.
     */
    fun apply(days: List<PrayerDay>, lon: Double, phone: ZoneId): List<PrayerDay> =
        days.map { d ->
            val dhuhr = d.times.getOrNull(2) ?: return@map d
            val offset = offsetMinutes(d.date, dhuhr, lon) ?: return@map d
            val place = ZoneOffset.ofTotalSeconds(offset * 60)
            val localMoments = Prayers.dayMoments(d.copy(shiftMinutes = 0, instants = null, phoneZone = place))
            val instants = localMoments.map { it.instant }
            d.copy(shiftMinutes = shiftMinutes(d.date, dhuhr, lon, phone), instants = instants, phoneZone = phone)
        }
}

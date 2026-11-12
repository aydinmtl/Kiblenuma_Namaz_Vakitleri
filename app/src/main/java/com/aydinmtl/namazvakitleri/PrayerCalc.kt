package com.aydinmtl.namazvakitleri

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Hesaplama yöntemleri. [fajr] ve [ishaAngle]: güneşin ufkun kaç derece altında olduğu.
 * [ishaMinutes]: yatsı açı yerine akşamdan belli dakika sonra ise.
 */
enum class CalcMethod(
    val id: String,
    val title: String,
    val fajr: Double,
    val ishaAngle: Double?,
    val ishaMinutes: Int?,
    val where: String,
) {
    DIYANET("diyanet", "Diyanet İşleri Başkanlığı", 18.0, 17.0, null,
        "Türkiye ve Avrupa'daki Türk camileri. Vakitler Diyanet'in resmi listesinden indirilir; temkin (ihtiyat) süreleri dahildir."),
    MWL("mwl", "Muslim World League (Dünya İslam Birliği)", 18.0, 17.0, null,
        "Avrupa, Uzak Doğu ve Amerika'nın bir kısmında yaygın."),
    ISNA("isna", "ISNA (Kuzey Amerika İslam Birliği)", 15.0, 15.0, null,
        "ABD ve Kanada."),
    EGYPT("egypt", "Mısır Genel Araştırma Kurumu", 19.5, 17.5, null,
        "Mısır, Afrika, Suriye, Lübnan, Malezya."),
    MAKKAH("makkah", "Ümmü'l-Kurâ Üniversitesi (Mekke)", 18.5, null, 90,
        "Suudi Arabistan. Yatsı akşamdan 90 dk sonra (Ramazan'da 120 dk)."),
    KARACHI("karachi", "Karaçi İslami İlimler Üniversitesi", 18.0, 18.0, null,
        "Pakistan, Hindistan, Bangladeş, Afganistan."),
    KUWAIT("kuwait", "Kuveyt", 18.0, 17.5, null, "Kuveyt."),
    QATAR("qatar", "Katar", 18.0, null, 90, "Katar. Yatsı akşamdan 90 dk sonra."),
    SINGAPORE("singapore", "Singapur / Malezya (MUIS, JAKIM)", 20.0, 18.0, null,
        "Singapur, Malezya, Endonezya."),
    UOIF("uoif", "Fransa İslam Teşkilatları Birliği (UOIF)", 12.0, 12.0, null,
        "Fransa'daki bazı camiler.");

    /** Ayarlar'da gösterilen kısa açıklama. */
    fun details(): String {
        if (this == DIYANET) return where
        val isha = if (ishaMinutes != null) "akşamdan $ishaMinutes dk sonra" else "güneş ufkun ${fmt(ishaAngle!!)}° altında"
        return "İmsak: güneş ufkun ${fmt(fajr)}° altında. Yatsı: $isha.\n$where\n" +
            "Vakitler telefonda astronomik formüllerle hesaplanır (internet gerekmez)."
    }

    private fun fmt(d: Double) = if (d == floor(d)) d.toInt().toString() else d.toString().replace('.', ',')

    companion object {
        fun byId(id: String?): CalcMethod = values().firstOrNull { it.id == id } ?: DIYANET
    }
}

/**
 * Güneşin konumundan namaz vakitlerini hesaplar.
 * Kullanılan formüller ABD Deniz Rasathanesi'nin (USNO) yaklaşık güneş konumu formülleridir;
 * dakika düzeyinde doğruluk verir.
 */
object PrayerCalc {

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(h: Double) = h - 24.0 * floor(h / 24.0)

    /** Güneşin dik açısı (declination, derece) ve zaman denklemi (saat). */
    private fun sun(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15.0)
        val eqt = q / 15.0 - ra
        val decl = deg(asin(sin(rad(e)) * sin(rad(l))))
        return Pair(decl, eqt)
    }

    /**
     * Güneşin [lon] boylamında en yükseğe çıktığı an (gerçek öğle), UTC saatiyle.
     * Örnek: 11.32 = 11:19 UTC. Saat dilimi bulmak için kullanılır (bkz. PlaceZone).
     */
    fun solarNoonUtc(date: LocalDate, lon: Double): Double {
        val jd = date.toEpochDay() + 2440587.5 + 0.5 - lon / 360.0
        return fixHour(12.0 - sun(jd).second - lon / 15.0)
    }

    /**
     * Bir günün 6 vakti: İmsak, Güneş, Öğle, İkindi, Akşam, Yatsı.
     * Güneşin hiç doğmadığı/batmadığı kutup bölgelerinde null döner.
     * [asrFactor]: 1 = standart (Şafii, Maliki, Hanbeli ve Diyanet), 2 = Hanefi (asr-ı sani).
     */
    fun day(date: LocalDate, lat: Double, lon: Double, zone: ZoneId, method: CalcMethod, asrFactor: Int): List<LocalTime>? {
        // Saat 24'ü geçen vakit (ör. 24:20) 00:20 olarak yazılır. Hangi güne ait olduğunu
        // Prayers.dayMoments sıralamaya bakarak bulur (akşamdan önce görünen yatsı = ertesi gün).
        return rawMinutes(date, lat, lon, zone, method, asrFactor)?.map { toClock(it.roundToInt()) }
    }

    /**
     * Diyanet'in temkin (ihtiyat) süreleri, dakika: İmsak, Güneş, Öğle, İkindi, Akşam, Yatsı.
     * Güneş 7 dk erken; öğle 5, ikindi 4, akşam 7 dk geç yazılır. Diyanet'in kendi listesinde de
     * böyledir: listede "GunesDogus" ile "Gunes" arasında ve "GunesBatis" ile "Aksam" arasında
     * tam 7 dakika vardır.
     *
     * Akşam burada 7 değil 8 alınır: Diyanet'in gün batımı bizim hesabımızdan yaklaşık 1 dakika
     * geç çıkıyor (Cenevre listesiyle karşılaştırıldı). Hesaplanan akşam vakti resmi listeden
     * ERKEN olmasın diye (iftar için önemli) 1 dakika daha ihtiyat eklenir.
     */
    private val DIYANET_TEMKIN = intArrayOf(0, -7, 5, 4, 8, 0)

    /**
     * Diyanet'in ölçüleriyle (imsak 18°, yatsı 17°, temkin süreleri) HESAPLANMIŞ vakitler.
     *
     * Ne zaman kullanılır: Diyanet'in listesinde bulunulan yere yakın bir ilçe yoksa
     * (bkz. AutoLocation). Resmi liste değildir; ekranda hesap olduğu yazılır.
     * Cenevre'nin Kasım 2026 Diyanet listesiyle karşılaştırıldı (bkz. PrayerCalcTest):
     * imsak, güneş, öğle, ikindi ve akşam en çok 1 dakika farklı; yatsı 5-6 dakika daha GEÇ
     * (Diyanet yüksek enlemlerde yatsı için ayrı bir kural uyguluyor; geç olan ihtiyatlı yöndür).
     *
     * Yuvarlama ihtiyatlı yöne yapılır: imsak ve güneş aşağı, diğerleri yukarı.
     */
    fun diyanetDay(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): List<LocalTime>? {
        val raw = rawMinutes(date, lat, lon, zone, CalcMethod.DIYANET, 1) ?: return null
        return raw.mapIndexed { i, m ->
            val t = m + DIYANET_TEMKIN[i]
            toClock(if (i <= SUNRISE) floor(t).toInt() else ceil(t).toInt())
        }
    }

    /** Gün içindeki dakikayı saate çevirir; 24:00'ı geçerse başa sarar. */
    private fun toClock(minutes: Int): LocalTime {
        val m = ((minutes % 1440) + 1440) % 1440
        return LocalTime.of(m / 60, m % 60)
    }

    /** 6 vaktin yuvarlanmamış hali: yerel saatle, gece yarısından itibaren dakika. */
    private fun rawMinutes(date: LocalDate, lat: Double, lon: Double, zone: ZoneId, method: CalcMethod, asrFactor: Int): List<Double>? {
        val tz = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val jd = date.toEpochDay() + 2440587.5 - lon / (15.0 * 24.0)

        fun midDay(t: Double): Double = fixHour(12.0 - sun(jd + t).second)

        fun sunAngleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sun(jd + t).first
            val noon = midDay(t)
            val x = (-sin(rad(angle)) - sin(rad(decl)) * sin(rad(lat))) / (cos(rad(decl)) * cos(rad(lat)))
            if (x < -1.0 || x > 1.0) return Double.NaN
            val tt = deg(acos(x)) / 15.0
            return if (ccw) noon - tt else noon + tt
        }

        fun asrTime(factor: Int, t: Double): Double {
            val decl = sun(jd + t).first
            val angle = -deg(atan(1.0 / (factor + tan(rad(abs(lat - decl))))))
            return sunAngleTime(angle, t, false)
        }

        val ishaAngle = method.ishaAngle
        var times = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(2) { // ikinci tur, ilk turun sonucunu başlangıç alarak hassasiyeti artırır
            val p = times.map { it / 24.0 }
            times = doubleArrayOf(
                sunAngleTime(method.fajr, p[0], true),
                sunAngleTime(0.833, p[1], true),
                midDay(p[2]),
                asrTime(asrFactor, p[3]),
                sunAngleTime(0.833, p[4], false),
                if (ishaAngle != null) sunAngleTime(ishaAngle, p[5], false) else 0.0,
            )
        }
        val sunrise = times[1]
        val sunset = times[4]
        if (sunrise.isNaN() || sunset.isNaN()) return null

        if (ishaAngle == null) {
            // Önce yönteme bakılır: Hicri takvim yalnızca Ümmü'l-Kurâ için gerekir.
            val ramadan = method == CalcMethod.MAKKAH && Hijri.of(date).month == 9
            times[5] = sunset + ((method.ishaMinutes ?: 90) + if (ramadan) 30 else 0) / 60.0
        }

        // Yüksek enlem düzeltmesi (açı tabanlı): gece çok kısaysa imsak/yatsı gecenin bir payıyla sınırlanır.
        val night = 24.0 - (sunset - sunrise)
        val fajrPortion = method.fajr / 60.0 * night
        if (times[0].isNaN() || (sunrise - times[0]) > fajrPortion) times[0] = sunrise - fajrPortion
        if (ishaAngle != null) {
            val ishaPortion = ishaAngle / 60.0 * night
            if (times[5].isNaN() || (times[5] - sunset) > ishaPortion) times[5] = sunset + ishaPortion
        }

        return times.map { t -> fixHour(t + tz - lon / 15.0) * 60.0 }
    }
}

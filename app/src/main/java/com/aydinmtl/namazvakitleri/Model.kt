package com.aydinmtl.namazvakitleri

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val TR: Locale = Locale.forLanguageTag("tr-TR")
val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Sırası API'deki sırayla aynı: 0 İmsak … 5 Yatsı. */
val PRAYER_NAMES = listOf("İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı")

/** Güneş (1) bir namaz vakti değil; bildirim gönderilmez. */
const val SUNRISE = 1

/** Akşam vaktinin sırası (0 İmsak … 4 Akşam, 5 Yatsı). */
const val MAGHRIB = 4

/**
 * Bir günün 6 vakti.
 * [times] yerin kendi saatiyle yazılır (Diyanet'in listesindeki gibi).
 * [shiftMinutes]: telefonun saati ile yerin saati arasındaki fark (dakika). Genelde 0'dır;
 * başka saat dilimindeki bir yer seçilince (ör. Cenevre'deyken İstanbul) 0 olmaz.
 * Ekranda [times] aynen gösterilir; alarm ve geri sayım bu farkla düzeltilmiş anı kullanır.
 */
data class PrayerDay(
    val date: LocalDate,
    val times: List<LocalTime>,
    val hijri: String,
    val hijriShort: String = "",
    val shiftMinutes: Int = 0,
)

/**
 * Takvimdeki tek bir an: hangi vakit, ne zaman.
 * [at] her zaman TELEFONUN saatiyle gerçek andır (alarm ve geri sayım bunu kullanır).
 * [clock]: listede yazan saat (yerin kendi saati). Saat dilimleri aynıysa [at] ile aynıdır.
 * [day]: vaktin ait olduğu gün (imsakiyedeki satır). Genelde [at] ile aynı gündür; ama kuzey
 * ülkelerinde yatsı gece yarısını geçerse [at] ertesi güne düşer, [day] yine o günün satırıdır.
 */
data class Moment(
    val index: Int,
    val at: LocalDateTime,
    val day: LocalDate = at.toLocalDate(),
    val clock: LocalTime = at.toLocalTime(),
) {
    val name: String get() = PRAYER_NAMES[index]
}

object Prayers {

    /** Bütün günlerin bütün vakitlerini zaman sırasıyla tek listede verir. */
    fun moments(days: List<PrayerDay>): List<Moment> =
        days.flatMap { dayMoments(it) }
            .sortedBy { it.at.toEpochSecond(ZoneOffset.UTC) }

    /**
     * Bir günün 6 vaktini gerçek tarih-saatleriyle verir.
     *
     * Vakitler yalnızca saat olarak saklanır (ör. 00:20). Yaz aylarında kuzey ülkelerinde:
     *  - yatsı gece yarısını geçebilir: akşamdan "önce" görünen yatsı aslında ERTESİ gündedir;
     *  - imsak gece yarısından önce olabilir: güneşten "sonra" görünen imsak aslında ÖNCEKİ gündedir.
     * Bunu düzeltmezsek sıradaki vakit ve alarmlar karışır.
     */
    fun dayMoments(d: PrayerDay): List<Moment> {
        val t = d.times
        return t.mapIndexed { i, time ->
            val date = when {
                i == 0 && t.size > 1 && time.isAfter(t[1]) -> d.date.minusDays(1)
                i == 5 && time.isBefore(t[4]) -> d.date.plusDays(1)
                else -> d.date
            }
            // Yerin saatiyle an -> telefonun saatiyle an (fark yoksa aynı kalır)
            val at = LocalDateTime.of(date, time).plusMinutes(d.shiftMinutes.toLong())
            Moment(i, at, d.date, time)
        }
    }

    /** [after] anından SONRA gelen ilk vakit. */
    fun next(days: List<PrayerDay>, after: LocalDateTime): Moment? =
        moments(days).firstOrNull { it.at.isAfter(after) }

    /** Şu an içinde bulunulan vakit (girmiş olan son vakit). */
    fun current(days: List<PrayerDay>, now: LocalDateTime): Moment? =
        moments(days).lastOrNull { !it.at.isAfter(now) }

    fun day(days: List<PrayerDay>, date: LocalDate): PrayerDay? =
        days.firstOrNull { it.date == date }
}

/** 3725 saniye -> "01:02:05" */
fun formatDuration(totalSeconds: Long): String {
    val s = if (totalSeconds < 0) 0 else totalSeconds
    return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

/**
 * İsim karşılaştırmak için sadeleştirir: "İSVİÇRE" -> "isvicre", "Genève" -> "geneve",
 * "Kadıköy" -> "kadikoy". Harf ve rakam dışındaki her şey atılır.
 */
fun norm(s: String?): String {
    if (s.isNullOrBlank()) return ""
    val lower = s.lowercase(TR).replace('ı', 'i')
    val noAccents = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    return noAccents.replace("ß", "ss").replace(Regex("[^a-z0-9]"), "")
}

/**
 * Tamamı büyük harfle yazılmış bir yer adını okunur hâle getirir: "İSVİÇRE" -> "İsviçre".
 *
 * Buradaki incelik I harfidir. Türkçede büyük I'nın küçüğü "ı", büyük İ'nin küçüğü "i"dir;
 * diğer dillerde ise büyük I'nın küçüğü "i"dir. Diyanet listesi her adı büyük harfle verir:
 *   - Türkçe adlar iki harfi ayırır: "KADIKÖY" (ı) ve "İSTANBUL" (i).
 *   - Yabancı adlar düz I ile yazılır: "VERNIER", "WINTERTHUR". Bunlara Türkçe kural
 *     uygulanırsa "Vernıer" gibi yanlış bir ad çıkar; doğrusu "Vernier"dir.
 *
 * [turkish] true ise ad Türkçe yazılmıştır (ülke adları, Türkiye'deki yerler): Türkçe kural.
 * false ise ad yabancıdır: I -> i. Yabancı bir ülkedeki ad yine de Türkçe yazılmış olabilir
 * (ör. Bulgaristan'da "KIRCAALİ"). Bu yüzden karar kelime kelime verilir: kelimede yalnızca
 * Türkçede bulunan bir harf (İ, Ş, Ğ, ı) varsa o kelime Türkçe sayılır.
 *
 * Ayrıca fazla boşluklar atılır ve parantez, tire, noktadan sonraki harf de büyük yazılır:
 * "RUTI (B)" -> "Ruti (B)", "ST. MORITZ" -> "St. Moritz".
 */
fun titleCase(s: String, turkish: Boolean = true): String =
    s.trim().split(Regex("\\s+")).joinToString(" ") { word -> titleCaseWord(word, turkish) }

/** Yalnızca Türkçe (ve Azerbaycan) yazımında bulunan harfler: kelimenin Türkçe yazıldığını gösterir. */
private const val TURKISH_ONLY_LETTERS = "İŞĞışğ"

private fun titleCaseWord(word: String, turkish: Boolean): String {
    val tr = turkish || word.any { it in TURKISH_ONLY_LETTERS }
    val locale = if (tr) TR else Locale.ROOT
    // Yabancı kelimede noktalı İ de sıradan "i" olur (Locale.ROOT onu iki karaktere bölerdi).
    val lower = if (tr) word.lowercase(TR) else word.replace('İ', 'I').lowercase(Locale.ROOT)
    val out = StringBuilder()
    var prev = ' ' // kelimenin başı: ilk harf büyük yazılır
    for (c in lower) {
        // Kesme işaretinden sonra yalnızca yabancı adlarda büyük harf gelir ("L'Isle");
        // Türkçede kesme işareti ek ayırır, ek küçük kalır.
        val afterApostrophe = prev == '\'' || prev == '’'
        val startsPart = !prev.isLetter() && (!afterApostrophe || !tr)
        out.append(if (c.isLetter() && startsPart) c.toString().uppercase(locale) else c.toString())
        prev = c
    }
    return out.toString()
}

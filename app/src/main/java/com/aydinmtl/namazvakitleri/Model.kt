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

/** Bir günün 6 vakti. */
data class PrayerDay(
    val date: LocalDate,
    val times: List<LocalTime>,
    val hijri: String,
    val hijriShort: String = "",
)

/** Takvimdeki tek bir an: hangi vakit, ne zaman. */
data class Moment(val index: Int, val at: LocalDateTime) {
    val name: String get() = PRAYER_NAMES[index]
}

object Prayers {

    /** Bütün günlerin bütün vakitlerini zaman sırasıyla tek listede verir. */
    fun moments(days: List<PrayerDay>): List<Moment> =
        days.flatMap { d -> d.times.mapIndexed { i, t -> Moment(i, LocalDateTime.of(d.date, t)) } }
            .sortedBy { it.at.toEpochSecond(ZoneOffset.UTC) }

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

/** "İSVİÇRE" -> "İsviçre", "CENEVRE" -> "Cenevre" (Türkçe harf kurallarıyla). */
fun titleCase(s: String): String =
    s.trim().lowercase(TR).split(" ").joinToString(" ") { w ->
        if (w.isEmpty()) w else w.substring(0, 1).uppercase(TR) + w.substring(1)
    }

package com.aydinmtl.namazvakitleri

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

/**
 * Uygulamanın her yerinde vakitler buradan alınır.
 * - Diyanet seçiliyse: Diyanet'ten indirilmiş resmi liste.
 * - Başka bir yöntem seçiliyse: seçili yerin koordinatıyla telefonda hesaplanır.
 */
object Times {
    private var cacheKey: String? = null
    private var cache: List<PrayerDay> = emptyList()
    private var alignedFor: String? = null
    private var zoneKey: String? = null
    private var zoneCache: List<PrayerDay> = emptyList()

    /** Seçilen yöntem hesaplanamıyorsa (yerin koordinatı yok) Diyanet'e düşülür. */
    fun effectiveMethod(ctx: Context): CalcMethod {
        val m = Store.method(ctx)
        return if (m != CalcMethod.DIYANET && Store.placePoint(ctx) == null) CalcMethod.DIYANET else m
    }

    fun isFallback(ctx: Context): Boolean = effectiveMethod(ctx) != Store.method(ctx)

    /** Ekranda gösterilecek kaynak satırı. */
    fun sourceLabel(ctx: Context): String {
        val m = effectiveMethod(ctx)
        val asr = if (m != CalcMethod.DIYANET && Store.asrHanafi(ctx)) " · İkindi: Hanefi" else ""
        // Seçilen yer başka bir saat dilimindeyse kullanıcıya söyle (bkz. PlaceZone)
        val shift = Prayers.day(days(ctx), LocalDate.now())?.shiftMinutes ?: 0
        val zoneNote = if (shift == 0) "" else
            "\nSaatler seçilen yerin saatiyle. Alarm ve geri sayım telefonun saatine göre."
        return "Kaynak: ${m.title}$asr" +
            (if (isFallback(ctx)) "\n⚠ Seçilen yöntem için yerin koordinatı bulunamadı; Diyanet gösteriliyor." else "") +
            zoneNote
    }

    @Synchronized
    fun days(ctx: Context): List<PrayerDay> {
        val diyanet = Store.days(ctx)
        // Hicri tarihleri Diyanet takvimiyle hizala.
        val today = LocalDate.now()
        Prayers.day(diyanet, today)?.let {
            val alignKey = "$today|${it.hijriShort}"
            if (it.hijriShort.isNotEmpty() && alignKey != alignedFor) {
                Hijri.alignWithDiyanet(today, it.hijriShort)
                alignedFor = alignKey
            }
        }

        val m = effectiveMethod(ctx)
        if (m == CalcMethod.DIYANET) return withPlaceZone(ctx, diyanet)

        val point = Store.placePoint(ctx) ?: return diyanet
        val zone = ZoneId.systemDefault()
        val hanafi = Store.asrHanafi(ctx)
        val key = "${m.id}|$hanafi|${point.first}|${point.second}|$today|${zone.id}|${Hijri.offsetDays}"
        if (key == cacheKey) return cache

        val out = ArrayList<PrayerDay>()
        for (i in -1..30) {
            val date = today.plusDays(i.toLong())
            val times = PrayerCalc.day(date, point.first, point.second, zone, m, if (hanafi) 2 else 1) ?: continue
            out.add(PrayerDay(date, times, Hijri.format(date)))
        }
        cache = out
        cacheKey = key
        return out
    }

    /**
     * Diyanet saatleri yerin kendi saatidir. Seçilen yer başka bir saat dilimindeyse
     * alarm ve geri sayım kaymasın diye her güne saat farkını yazar (bkz. PlaceZone).
     * Yerin koordinatı bilinmiyorsa liste olduğu gibi kalır.
     */
    private fun withPlaceZone(ctx: Context, diyanet: List<PrayerDay>): List<PrayerDay> {
        val point = Store.placePoint(ctx) ?: return diyanet
        val zone = ZoneId.systemDefault()
        val key = "${Store.districtId(ctx)}|${point.second}|${zone.id}|${diyanet.size}|" +
            "${diyanet.firstOrNull()?.date}|${diyanet.lastOrNull()?.date}"
        if (key == zoneKey) return zoneCache
        val out = PlaceZone.apply(diyanet, point.second, zone)
        zoneCache = out
        zoneKey = key
        return out
    }
}

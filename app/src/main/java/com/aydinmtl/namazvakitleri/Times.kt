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
        return "Kaynak: ${m.title}$asr" +
            if (isFallback(ctx)) "\n⚠ Seçilen yöntem için yerin koordinatı bulunamadı; Diyanet gösteriliyor." else ""
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
        if (m == CalcMethod.DIYANET) return diyanet

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
}

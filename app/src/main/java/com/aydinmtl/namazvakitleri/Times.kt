package com.aydinmtl.namazvakitleri

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

/**
 * Uygulamanın her yerinde vakitler buradan alınır.
 * - Diyanet seçiliyse: Diyanet'ten indirilmiş resmi liste.
 * - Başka bir yöntem seçiliyse: seçili yerin koordinatıyla telefonda hesaplanır.
 * - Diyanet seçili ama listede bulunulan yere yakın ilçe yoksa ("hesaplanan yer", bkz. AutoLocation):
 *   Diyanet'in ölçüleriyle telefonda hesaplanır ve ekranda bunun hesap olduğu yazılır.
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
        // Hesaplanan yerde koordinat her zaman vardır; seçilen yöntem aynen kullanılır.
        if (Store.isComputed(ctx)) return m
        return if (m != CalcMethod.DIYANET && Store.placePoint(ctx) == null) CalcMethod.DIYANET else m
    }

    fun isFallback(ctx: Context): Boolean = effectiveMethod(ctx) != Store.method(ctx)

    /** Ekranda gösterilecek kaynak satırı. */
    fun sourceLabel(ctx: Context): String {
        val m = effectiveMethod(ctx)
        if (m == CalcMethod.DIYANET && Store.isComputed(ctx)) {
            // Diyanet'in listesinde yakın yer yok: resmi liste değil, hesap olduğu açıkça yazılır.
            return Lang.str(ctx, R.string.src_computed)
        }
        val asr = if (m != CalcMethod.DIYANET && Store.asrHanafi(ctx)) Lang.str(ctx, R.string.src_asr_hanafi) else ""
        // Seçilen yer başka bir saat dilimindeyse kullanıcıya söyle (bkz. PlaceZone)
        val shift = Prayers.day(days(ctx), LocalDate.now())?.shiftMinutes ?: 0
        val zoneNote = if (shift == 0) "" else Lang.str(ctx, R.string.src_zone_note)
        return Lang.str(ctx, R.string.src_label, Lang.methodTitle(ctx, m)) + asr +
            (if (isFallback(ctx)) Lang.str(ctx, R.string.src_fallback) else "") +
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
        // Hesaplanan yer + Diyanet yöntemi: liste yok, Diyanet ölçüleriyle telefonda hesaplanır.
        val computedDiyanet = m == CalcMethod.DIYANET && Store.isComputed(ctx)
        if (m == CalcMethod.DIYANET && !computedDiyanet) return withPlaceZone(ctx, diyanet)

        val point = Store.placePoint(ctx) ?: return diyanet
        val zone = ZoneId.systemDefault()
        val hanafi = Store.asrHanafi(ctx)
        val key = "${m.id}|$computedDiyanet|$hanafi|${point.first}|${point.second}|$today|${zone.id}|${Hijri.offsetDays}"
        if (key == cacheKey) return cache

        val out = ArrayList<PrayerDay>()
        for (i in -1..30) {
            val date = today.plusDays(i.toLong())
            val times = (
                if (computedDiyanet) PrayerCalc.diyanetDay(date, point.first, point.second, zone)
                else PrayerCalc.day(date, point.first, point.second, zone, m, if (hanafi) 2 else 1)
                ) ?: continue
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

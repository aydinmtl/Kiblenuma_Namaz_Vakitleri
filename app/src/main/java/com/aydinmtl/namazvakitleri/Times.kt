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
    private val reader = PrayerTimeReader()

    /** Seçilen yöntem hesaplanamıyorsa (yerin koordinatı yok) Diyanet'e düşülür. */
    fun effectiveMethod(ctx: Context): CalcMethod = Store.prayerSnapshot(ctx).second.effectiveMethod

    fun isFallback(ctx: Context): Boolean {
        val state = Store.prayerSnapshot(ctx).second
        return state.effectiveMethod != state.method
    }

    /** Ekranda gösterilecek kaynak satırı. */
    fun sourceLabel(ctx: Context): String {
        val state = Store.prayerSnapshot(ctx).second
        val m = state.effectiveMethod
        if (m == CalcMethod.DIYANET && state.computed) {
            // Diyanet'in listesinde yakın yer yok: resmi liste değil, hesap olduğu açıkça yazılır.
            return Lang.str(ctx, R.string.src_computed)
        }
        val asr = if (m != CalcMethod.DIYANET && state.hanafi) Lang.str(ctx, R.string.src_asr_hanafi) else ""
        // Seçilen yer başka bir saat dilimindeyse kullanıcıya söyle (bkz. PlaceZone)
        val shift = Prayers.day(days(state), LocalDate.now())?.shiftMinutes ?: 0
        val zoneNote = if (shift == 0) "" else Lang.str(ctx, R.string.src_zone_note)
        return Lang.str(ctx, R.string.src_label, Lang.methodTitle(ctx, m)) + asr +
            (if (state.effectiveMethod != state.method) Lang.str(ctx, R.string.src_fallback) else "") +
            zoneNote
    }

    fun days(ctx: Context): List<PrayerDay> = days(Store.prayerSnapshot(ctx).second)

    internal fun days(state: PrayerSnapshot): List<PrayerDay> = reader.days(state)
}

/** Kaynak JSON, koordinat çifti ve hesap ayarları aynı seçimden alınır. */
internal data class PrayerSnapshot(
    val districtId: String?,
    val timesJson: String?,
    val point: Pair<Double, Double>?,
    val method: CalcMethod,
    val hanafi: Boolean,
) {
    val computed: Boolean get() = districtId == Store.COMPUTED_ID
    val effectiveMethod: CalcMethod
        get() = if (!computed && method != CalcMethod.DIYANET && point == null) CalcMethod.DIYANET else method

    fun needsData(today: LocalDate): Boolean =
        RefreshPolicy.needsData(districtId, computed, diyanetDays().lastOrNull()?.date, today)

    fun diyanetDays(): List<PrayerDay> = immutableDays(timesJson?.let(Diyanet::parseDays) ?: emptyList())
}

internal data class AlarmSnapshot(
    val prayers: PrayerSnapshot,
    val reminderMinutes: Int,
    val notifyFor: List<Boolean>,
)

/** Kilit altında alınan değerlerden türetir; Store veya seçim kilidini geri çağırmaz. */
internal class PrayerTimeReader(private val hijriFormat: (LocalDate) -> String = Hijri::format) {
    private data class Key(val state: PrayerSnapshot, val today: LocalDate, val zone: ZoneId, val hijriOffset: Long)
    private var cacheKey: Key? = null
    private var cache: List<PrayerDay> = emptyList()
    private var alignedFor: String? = null

    @Synchronized
    fun days(state: PrayerSnapshot, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<PrayerDay> {
        val diyanet = state.diyanetDays()
        Prayers.day(diyanet, today)?.let {
            val alignKey = "$today|${it.hijriShort}"
            if (it.hijriShort.isNotEmpty() && alignKey != alignedFor) {
                Hijri.alignWithDiyanet(today, it.hijriShort)
                alignedFor = alignKey
            }
        }
        val key = Key(state, today, zone, Hijri.offsetDays)
        if (key == cacheKey) return cache
        val m = state.effectiveMethod
        val computedDiyanet = m == CalcMethod.DIYANET && state.computed
        val point = state.point
        val out = when {
            m == CalcMethod.DIYANET && !computedDiyanet ->
                if (point == null) diyanet else PlaceZone.apply(diyanet, point.second, zone)
            point == null -> diyanet
            else -> (-1..30).mapNotNull { i ->
                val date = today.plusDays(i.toLong())
                PrayerCalc.calculatedDay(date, point.first, point.second, zone, m,
                    if (state.hanafi && !computedDiyanet) 2 else 1)?.copy(hijri = hijriFormat(date))
            }
        }
        cache = immutableDays(out)
        cacheKey = key
        return cache
    }
}

private fun immutableDays(days: List<PrayerDay>): List<PrayerDay> =
    java.util.Collections.unmodifiableList(days.map { day ->
        day.copy(
            times = java.util.Collections.unmodifiableList(ArrayList(day.times)),
            instants = day.instants?.let { java.util.Collections.unmodifiableList(ArrayList(it)) },
        )
    })

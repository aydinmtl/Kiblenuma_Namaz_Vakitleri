package com.aydinmtl.namazvakitleri

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Seçilen yer, ayarlar ve 30 günlük vakit önbelleği (SharedPreferences). */
object Store {
    private const val PREFS = "namaz"
    private const val MIN_RETRY_MS = 15 * 60 * 1000L        // başarısız denemeden sonra 15 dk bekle
    private const val REFRESH_WHEN_DAYS_LEFT = 10            // 10 günden az veri kalınca yenile

    private var cachedJson: String? = null
    private var cachedDays: List<PrayerDay> = emptyList()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun districtId(ctx: Context): String? = prefs(ctx).getString("districtId", null)
    fun placeName(ctx: Context): String = prefs(ctx).getString("placeName", "") ?: ""

    fun notificationsOn(ctx: Context): Boolean = prefs(ctx).getBoolean("notify", true)
    fun setNotificationsOn(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("notify", on).apply()
    }

    /** Vakit bazında bildirim. Varsayılan: Güneş hariç hepsi açık. */
    fun notifyFor(ctx: Context, index: Int): Boolean = prefs(ctx).getBoolean("notify_$index", index != SUNRISE)
    fun setNotifyFor(ctx: Context, index: Int, on: Boolean) {
        prefs(ctx).edit().putBoolean("notify_$index", on).apply()
    }

    // ---- Otomatik konum ----

    fun autoLocation(ctx: Context): Boolean = prefs(ctx).getBoolean("autoLocation", false)
    fun setAutoLocation(ctx: Context, on: Boolean) {
        val e = prefs(ctx).edit().putBoolean("autoLocation", on)
        if (!on) e.remove("autoLat").remove("autoLon")
        e.apply()
    }

    /** Konum izni açıklaması bir kez gösterilir. */
    fun locationIntroShown(ctx: Context): Boolean = prefs(ctx).getBoolean("locIntro", false)
    fun setLocationIntroShown(ctx: Context) {
        prefs(ctx).edit().putBoolean("locIntro", true).apply()
    }

    /** Vakitlerin en son otomatik olarak belirlendiği nokta (enlem, boylam). */
    fun autoPoint(ctx: Context): Pair<Double, Double>? {
        val p = prefs(ctx)
        val lat = p.getString("autoLat", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("autoLon", null)?.toDoubleOrNull() ?: return null
        return Pair(lat, lon)
    }

    fun saveAutoPoint(ctx: Context, lat: Double, lon: Double) {
        prefs(ctx).edit().putString("autoLat", lat.toString()).putString("autoLon", lon.toString()).apply()
    }

    fun lastDay(ctx: Context): LocalDate? = days(ctx).lastOrNull()?.date

    fun askedNotificationPermission(ctx: Context): Boolean = prefs(ctx).getBoolean("askedNotif", false)
    fun setAskedNotificationPermission(ctx: Context) {
        prefs(ctx).edit().putBoolean("askedNotif", true).apply()
    }

    fun saveLocation(ctx: Context, districtId: String, placeName: String, timesJson: String) {
        prefs(ctx).edit()
            .putString("districtId", districtId)
            .putString("placeName", placeName)
            .putString("times", timesJson)
            .putLong("lastAttempt", System.currentTimeMillis())
            .apply()
    }

    @Synchronized
    fun days(ctx: Context): List<PrayerDay> {
        val json = prefs(ctx).getString("times", null) ?: return emptyList()
        if (json != cachedJson) {
            cachedDays = Diyanet.parseDays(json)
            cachedJson = json
        }
        return cachedDays
    }

    /** Veri bitmek üzereyse ve kısa süre önce denenmediyse true. */
    fun needsRefresh(ctx: Context): Boolean {
        if (districtId(ctx) == null) return false
        val sinceLast = System.currentTimeMillis() - prefs(ctx).getLong("lastAttempt", 0L)
        if (sinceLast < MIN_RETRY_MS) return false
        val last = days(ctx).lastOrNull()?.date ?: return true
        return ChronoUnit.DAYS.between(LocalDate.now(), last) < REFRESH_WHEN_DAYS_LEFT
    }

    /** Vakitleri internetten yeniler. Ağ kullanır: arka planda çağır. */
    fun refresh(ctx: Context): Boolean {
        val id = districtId(ctx) ?: return false
        prefs(ctx).edit().putLong("lastAttempt", System.currentTimeMillis()).apply()
        return try {
            val json = Diyanet.fetchTimes(id)
            if (Diyanet.parseDays(json).isEmpty()) {
                false
            } else {
                prefs(ctx).edit().putString("times", json).apply()
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

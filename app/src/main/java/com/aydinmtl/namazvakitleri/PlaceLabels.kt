package com.aydinmtl.namazvakitleri

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Seçili yerin adını seçili dilde bulur: "Cenevre, İsviçre" → Fransızcada "Genève, Suisse",
 * Almancada "Genf, Schweiz", İngilizcede "Geneva, Switzerland".
 *
 * Neden gerekli? Diyanet yer adlarını tek biçimde verir ve büyük şehirlerin çoğu Türkçe yazılır
 * ("Cenevre", "Lozan"). Uygulama Fransızcaya çevrildiğinde yer adı Türkçe kalıyordu.
 *
 * Nasıl? Telefonun yer adı hizmetine (Geocoder) yerin adı seçili dille birlikte sorulur; hizmet
 * aynı yerin o dildeki adını verir. Sonuç telefonda saklanır (Store.setPlaceLabel); aynı yer ve
 * dil için bir daha sorulmaz. Hizmet yoksa ya da internet yoksa ekranda Diyanet'in adı kalır,
 * yalnızca ülke adı seçili dilde yazılır (bkz. Store.placeLabel, PlaceNames).
 *
 * Ağ kullanır; [ensure] işi arka planda yapar.
 */
object PlaceLabels {

    /** Adından bulunan yer, yerin bilinen koordinatından bu kadar uzaksa aynı adlı başka bir yerdir. */
    private const val MAX_KM = 80.0

    /** Bulunamadıysa aynı yer ve dil için bu süre dolmadan yeniden sorulmaz. */
    private const val RETRY_AFTER_MS = 5 * 60_000L

    private const val TIMEOUT_SECONDS = 8L

    private val running = AtomicBoolean(false)

    @Volatile
    private var failedKey = ""

    @Volatile
    private var failedAt = 0L

    private fun key(ctx: Context): String = Lang.code(ctx) + "|" + Store.placeName(ctx)

    /** Seçili dilde yer adı eksik mi ve şimdi sormanın anlamı var mı? */
    fun needed(ctx: Context): Boolean {
        val lang = Lang.code(ctx)
        if (lang == "tr" || Store.placeName(ctx).isEmpty() || Store.hasPlaceLabel(ctx, lang)) return false
        if (!Geocoder.isPresent()) return false
        return !(key(ctx) == failedKey && System.currentTimeMillis() - failedAt < RETRY_AFTER_MS)
    }

    /**
     * Gerekiyorsa adı arka planda bulur. Bulununca [onDone] ana iş parçacığında çağrılır
     * (ekrandaki yer adını ve widget'ı tazelemek için). Gerekmiyorsa hiçbir şey yapmaz.
     */
    fun ensure(ctx: Context, onDone: () -> Unit = {}) {
        val app = ctx.applicationContext
        if (!needed(app) || !running.compareAndSet(false, true)) return
        Thread {
            val asked = key(app)
            val found = try {
                localize(app)
            } catch (e: Exception) {
                false
            }
            if (!found) {
                failedKey = asked
                failedAt = System.currentTimeMillis()
            }
            running.set(false)
            if (found) Handler(Looper.getMainLooper()).post(onDone)
        }.start()
    }

    @Suppress("DEPRECATION") // eşzamanlı Geocoder çağrıları; zaten arka plandayız
    private fun localize(ctx: Context): Boolean {
        val lang = Lang.code(ctx)
        val locale = Lang.locale(ctx)
        val base = Store.placeName(ctx)
        val point = Store.placePoint(ctx)
        // Hesaplanan yerde Diyanet adı yoktur; yer, koordinatın kendisidir.
        val byPoint = Store.isComputed(ctx) && point != null

        val address = withTimeout {
            val geocoder = Geocoder(ctx, locale)
            if (byPoint) {
                geocoder.getFromLocation(point!!.first, point.second, 1)?.firstOrNull()
            } else {
                geocoder.getFromLocationName(base, 1)?.firstOrNull()
            }
        } ?: return false

        if (!byPoint) {
            // Türkiye'deki yerlerin adı zaten yereldir ("Kadıköy, İstanbul"): olduğu gibi kalır.
            if (address.countryCode.equals("TR", ignoreCase = true)) {
                Store.setPlaceLabel(ctx, base, lang, base)
                return true
            }
            if (point != null && address.hasLatitude() && address.hasLongitude() &&
                GeoMath.distanceKm(point.first, point.second, address.latitude, address.longitude) > MAX_KM
            ) {
                return false
            }
        }
        val local = address.locality ?: address.subAdminArea ?: address.adminArea ?: address.featureName
        val label = PlaceNames.compose(base, local, address.countryName) ?: return false
        Store.setPlaceLabel(ctx, base, lang, label)
        return true
    }

    /** Yer adı hizmeti bazı telefonlarda uzun süre takılabiliyor; en çok [TIMEOUT_SECONDS] beklenir. */
    private fun withTimeout(block: () -> Address?): Address? {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit(Callable { block() }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        } finally {
            executor.shutdownNow()
        }
    }
}

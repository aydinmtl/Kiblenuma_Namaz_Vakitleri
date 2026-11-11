package com.aydinmtl.namazvakitleri

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Konumdan Diyanet ilçesini bulur. Diyanet listesinde koordinat olmadığı için iki yol izlenir:
 *   1) İsim: telefonun bulduğu yer adları (Thônex, Genève/Cenevre, İsviçre) listede aranır.
 *   2) Mesafe: isim tutmazsa listedeki yerlerin koordinatı bulunur ve EN YAKINI seçilir.
 */
object AutoLocation {

    /** Bu kadar km'den az yer değiştirdiyse tekrar arama yapılmaz. */
    private const val MOVE_KM = 15f

    /** En yakın yer bundan uzaksa kabul edilmez (yanlış ülke/şehir eşleşmesini önler). */
    private const val NEAREST_MAX_KM = 150f

    sealed class Result {
        data class Changed(val label: String) : Result()
        object Same : Result()
        data class Failed(val reason: String) : Result()
    }

    /** Bulunulan yerin adları (Türkçe ve İngilizce karışık). */
    private data class Names(
        val countries: List<String>,
        val places: List<String>,   // öncelik sırasıyla: şehir/kasaba, ilçe, il/kanton/eyalet, semt
        val code: String?,
        val shown: String,
    )

    /** Ülke adları bazı ülkelerde telefon ile Diyanet arasında farklı; bilinen karşılıklar. */
    private val COUNTRY_ALIASES = mapOf(
        "US" to listOf("ABD", "Amerika Birleşik Devletleri", "USA", "United States"),
        "GB" to listOf("İngiltere", "Birleşik Krallık", "United Kingdom", "England"),
        "NL" to listOf("Hollanda", "Netherlands"),
        "DE" to listOf("Almanya", "Germany"),
        "CH" to listOf("İsviçre", "Switzerland"),
        "AT" to listOf("Avusturya", "Austria"),
        "BE" to listOf("Belçika", "Belgium"),
        "FR" to listOf("Fransa", "France"),
        "TR" to listOf("Türkiye", "Turkey"),
        "BA" to listOf("Bosna Hersek", "Bosnia and Herzegovina"),
        "MK" to listOf("Kuzey Makedonya", "Makedonya", "North Macedonia"),
        "KR" to listOf("Güney Kore", "South Korea"),
    )

    fun needsCheck(ctx: Context, loc: Location): Boolean {
        if (Store.districtId(ctx) == null) return true
        val p = Store.autoPoint(ctx) ?: return true
        val out = FloatArray(1)
        Location.distanceBetween(p.first, p.second, loc.latitude, loc.longitude, out)
        return out[0] / 1000f > MOVE_KM
    }

    /**
     * Ağ kullanır: arka planda çağır.
     * [progress]: hangi adımda olunduğunu ekrana yazmak için (arka plan iş parçacığından çağrılır).
     */
    fun resolve(ctx: Context, loc: Location, progress: (String) -> Unit = {}): Result {
        progress("Şehir adı bulunuyor…")
        val names = namesFromGeocoder(ctx, loc) ?: namesFromOpenStreetMap(loc)
            ?: return Result.Failed("Bulunduğun yerin adı alınamadı (internet bağlantısını kontrol et).")
        val found = (names.places.take(3) + names.countries.take(1)).distinct().joinToString(", ")

        progress("Diyanet listesinde aranıyor: ${names.shown}")
        val country = bestMatch(Diyanet.countries(), names.countries)
            ?: return Result.Failed("Ülke Diyanet listesinde bulunamadı. (Bulunan: $found)")

        val cities = Diyanet.cities(country.id)
        val city = bestMatch(cities, names.places)
            ?: cities.singleOrNull()
            ?: bestMatch(cities, names.countries)
            ?: run {
                progress("En yakın şehir hesaplanıyor…")
                nearest(ctx, cities, country.name, loc)
            }
            ?: return Result.Failed("${country.name} içinde yakın bir Diyanet şehri bulunamadı. (Bulunan: $found)")

        val districts = Diyanet.districts(city.id)
        val district = bestMatch(districts, names.places)
            ?: districts.singleOrNull()
            ?: run {
                progress("En yakın ilçe hesaplanıyor…")
                nearest(ctx, districts, "${city.name}, ${country.name}", loc)
            }
            ?: bestMatch(districts, listOf(city.name, city.nameEn))
            ?: return Result.Failed("${city.name} içinde yakın bir Diyanet ilçesi bulunamadı. (Bulunan: $found)")

        if (district.id == Store.districtId(ctx)) {
            Store.saveAutoPoint(ctx, loc.latitude, loc.longitude)
            Store.savePlacePoint(ctx, Pair(loc.latitude, loc.longitude))
            return Result.Same
        }

        progress("${district.name} vakitleri indiriliyor…")
        val json = Diyanet.fetchTimes(district.id)
        if (Diyanet.parseDays(json).isEmpty()) return Result.Failed("${district.name} için vakit alınamadı.")
        val label = Diyanet.label(district, city, country)
        Store.saveLocation(ctx, district.id, label, json)
        Store.saveAutoPoint(ctx, loc.latitude, loc.longitude)
        Store.savePlacePoint(ctx, Pair(loc.latitude, loc.longitude))
        return Result.Changed(label)
    }

    // ---------------- Yer adları ----------------

    private fun namesFromGeocoder(ctx: Context, loc: Location): Names? {
        if (!Geocoder.isPresent()) return null
        val tr = geocode(ctx, loc, TR)
        val en = geocode(ctx, loc, Locale.ENGLISH)
        if (tr == null && en == null) return null
        val code = (tr?.countryCode ?: en?.countryCode)?.uppercase(Locale.ROOT)
        return Names(
            countries = listOfNotNull(tr?.countryName, en?.countryName) + aliases(code),
            places = listOfNotNull(
                tr?.locality, en?.locality,
                tr?.subAdminArea, en?.subAdminArea,
                tr?.adminArea, en?.adminArea,
                tr?.subLocality, en?.subLocality,
            ),
            code = code,
            shown = tr?.locality ?: tr?.subAdminArea ?: tr?.adminArea ?: en?.locality ?: "Bulunduğun yer",
        )
    }

    /** Telefonun hizmeti çalışmazsa OpenStreetMap (Nominatim) ile yer adı. */
    private fun namesFromOpenStreetMap(loc: Location): Names? {
        val tr = nominatim(loc, "tr") ?: return null
        Thread.sleep(1100) // OpenStreetMap kuralı: saniyede en çok 1 istek
        val en = nominatim(loc, "en")
        val code = tr.optString("country_code").uppercase(Locale.ROOT).ifEmpty { null }
        val keys = listOf("city", "town", "village", "municipality", "county", "state_district", "state", "province", "suburb")
        val places = ArrayList<String>()
        for (k in keys) {
            tr.optString(k).takeIf { it.isNotBlank() }?.let { places.add(it) }
            en?.optString(k)?.takeIf { it.isNotBlank() }?.let { places.add(it) }
        }
        return Names(
            countries = listOfNotNull(tr.optString("country").ifBlank { null }, en?.optString("country")?.ifBlank { null }) + aliases(code),
            places = places,
            code = code,
            shown = places.firstOrNull() ?: "Bulunduğun yer",
        )
    }

    private fun nominatim(loc: Location, lang: String): JSONObject? = try {
        val url = URL(
            String.format(
                Locale.ROOT,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.5f&lon=%.5f&zoom=12&addressdetails=1&accept-language=%s",
                loc.latitude, loc.longitude, lang,
            ),
        )
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "Kiblenuma-Android/1.0 (github.com/aydinmtl/Kiblenuma_Namaz_Vakitleri)")
        try {
            if (c.responseCode != 200) null
            else JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }).optJSONObject("address")
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun aliases(code: String?): List<String> = code?.let { COUNTRY_ALIASES[it] } ?: emptyList()

    // ---------------- Mesafeye göre en yakın ----------------

    /**
     * Listedeki her yerin koordinatını (adından) bulur ve [loc]'a en yakın olanı döndürür.
     * Bulunan koordinatlar saklanır; sonraki aramalar hızlı olur.
     */
    private fun nearest(ctx: Context, list: List<Diyanet.Place>, context: String, loc: Location): Diyanet.Place? {
        if (list.isEmpty() || list.size > 150 || !Geocoder.isPresent()) return null
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = list.map { p ->
                pool.submit(Callable<Pair<Diyanet.Place, Pair<Double, Double>?>> {
                    val cached = Store.geoCache(ctx, p.id)
                    if (cached != null) return@Callable Pair(p, cached)
                    val point = forward(ctx, "${p.name}, $context") ?: forward(ctx, p.nameEn)
                    if (point != null) Store.setGeoCache(ctx, p.id, point)
                    Pair(p, point)
                })
            }
            var best: Diyanet.Place? = null
            var bestKm = Float.MAX_VALUE
            val out = FloatArray(1)
            val deadline = System.currentTimeMillis() + 30_000L
            for (f in futures) {
                val wait = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
                val (p, point) = try { f.get(wait, TimeUnit.MILLISECONDS) } catch (e: Exception) { continue }
                if (point == null) continue
                Location.distanceBetween(loc.latitude, loc.longitude, point.first, point.second, out)
                val km = out[0] / 1000f
                if (km < bestKm) {
                    bestKm = km
                    best = p
                }
            }
            return if (bestKm <= NEAREST_MAX_KM) best else null
        } finally {
            pool.shutdownNow()
        }
    }

    @Suppress("DEPRECATION")
    private fun forward(ctx: Context, query: String): Pair<Double, Double>? {
        if (query.isBlank()) return null
        return try {
            val a = Geocoder(ctx, TR).getFromLocationName(query, 1)?.firstOrNull() ?: return null
            if (a.hasLatitude() && a.hasLongitude()) Pair(a.latitude, a.longitude) else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Yer adından koordinat bulur ("Cenevre, İsviçre" -> 46.20, 6.14).
     * Elle seçilen yerlerde hesaplama yöntemleri, kıble ve camiler için gerekir. Ağ kullanır.
     */
    fun geocodeName(ctx: Context, queries: List<String>): Pair<Double, Double>? {
        if (!Geocoder.isPresent()) return null
        val executor = Executors.newSingleThreadExecutor()
        try {
            for (q in queries.filter { it.isNotBlank() }.distinct()) {
                val p = try {
                    executor.submit(Callable { forward(ctx, q) }).get(8, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    null
                }
                if (p != null) return p
            }
            return null
        } finally {
            executor.shutdownNow()
        }
    }

    /** Şehir adı sorgusu bazı telefonlarda uzun süre takılabiliyor; en çok 8 sn bekle. */
    @Suppress("DEPRECATION")
    private fun geocode(ctx: Context, loc: Location, locale: Locale): Address? {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit(Callable<Address?> {
                Geocoder(ctx, locale).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            }).get(8, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        } finally {
            executor.shutdownNow()
        }
    }

    /** Önce birebir eşleşme, sonra baş kısmı tutan eşleşme ("Genève" ~ "Geneve"). */
    private fun bestMatch(list: List<Diyanet.Place>, names: List<String?>): Diyanet.Place? {
        val keys = names.map { norm(it) }.filter { it.length >= 3 }.distinct()
        if (keys.isEmpty()) return null
        for (k in keys) {
            list.firstOrNull { norm(it.name) == k || norm(it.nameEn) == k }?.let { return it }
        }
        for (k in keys) {
            list.firstOrNull { p ->
                listOf(norm(p.name), norm(p.nameEn)).any { a ->
                    a.length >= 4 && k.length >= 4 && (a.startsWith(k) || k.startsWith(a))
                }
            }?.let { return it }
        }
        return null
    }
}

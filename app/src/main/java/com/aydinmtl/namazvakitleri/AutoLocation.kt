package com.aydinmtl.namazvakitleri

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Otomatik konum: telefonun bulunduğu yere geçer ve o yerin vakitlerini hazırlar.
 *
 * Diyanet'in listesinde koordinat yoktur, yalnızca yer adları vardır. Bu yüzden sırayla şunlar denenir:
 *   1) İsim: telefonun bulduğu yer adları (Thônex, Genève/Cenevre, İsviçre) listede aranır.
 *   2) Çevre: isim tutmazsa bulunulan yerin ÇEVRESİNDEKİ yerleşimlerin adları öğrenilir
 *      (2 km'den 24 km'ye kadar halkalar) ve onlar listede aranır. Ülke sınırına bakılmaz:
 *      Fransa sınırındaki biri için hem Annemasse (Fransa) hem Cenevre (İsviçre) adaydır.
 *   3) Mesafe: liste kısaysa listedeki yerlerin koordinatı bulunur ve en yakını seçilir.
 *   4) Hesap: Diyanet'te yakın bir yer hiç yoksa (ya da sunucuya ulaşılamıyorsa) vakitler
 *      konumun kendisinden, Diyanet'in ölçüleriyle hesaplanır (bkz. PrayerCalc.diyanetDay).
 *      Ekranda bunun hesap olduğu yazılır (bkz. Times.sourceLabel).
 *
 * Böylece otomatik konum "bulunamadı, eski yer kullanılıyor" diyerek eski şehirde KALMAZ.
 */
object AutoLocation {

    /** Bu kadar km'den az yer değiştirdiyse tekrar arama yapılmaz. */
    private const val MOVE_KM = 15f

    /** Bir ilçe bundan uzaksa kabul edilmez: o kadar uzağın vakti yerine hesap daha doğrudur. */
    private const val DISTRICT_MAX_KM = 50f

    /** Şehir (il, eyalet, kanton) merkezi için sınır; bunlar geniş bölgelerdir. */
    private const val CITY_MAX_KM = 300f

    /** Çevredeki yerleşimlerin arandığı halkalar (km). Yakından uzağa; ilk bulunan seçilir. */
    private val RINGS_KM = doubleArrayOf(2.0, 4.0, 7.0, 11.0, 16.0, 24.0)

    /** Hesaplanan yerdeyken Diyanet listesi bu aralıkla yeniden aranır. */
    private const val COMPUTED_RECHECK_MS = 12 * 60 * 60_000L

    /** Hesaba internet/sunucu hatası yüzünden düşüldüyse daha çabuk yeniden denenir. */
    private const val COMPUTED_RECHECK_AFTER_ERROR_MS = 2 * 60_000L

    /** Hesaplanan yerde konum bundan az değiştiyse nokta aynı kalır (vakitler oynamasın). */
    private const val COMPUTED_SAME_KM = 5.0

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

    /** Diyanet listesinde bulunan yer: ilçe, bağlı olduğu şehir ve ülke. */
    private data class Found(val district: Diyanet.Place, val city: Diyanet.Place, val country: Diyanet.Place)

    /** Bir arama boyunca indirilen şehir/ilçe listelerini saklar; aynı liste iki kez indirilmez. */
    private class Lists {
        private val cities = HashMap<String, List<Diyanet.Place>>()
        private val districts = HashMap<String, List<Diyanet.Place>>()
        fun cities(countryId: String): List<Diyanet.Place> = cities.getOrPut(countryId) { Diyanet.cities(countryId) }
        fun districts(cityId: String): List<Diyanet.Place> = districts.getOrPut(cityId) { Diyanet.districts(cityId) }
    }

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

    /** Son başarısız denemenin zamanı. Başarısızlıkta hemen tekrar tekrar denememek için. */
    @Volatile
    var lastFailAt = 0L

    /** Başarısız denemeden sonra bu kadar süre beklenir (uygulama açıldıkça yeniden dener). */
    private const val RETRY_AFTER_FAIL_MS = 60_000L

    fun needsCheck(ctx: Context, loc: Location): Boolean {
        if (Store.districtId(ctx) == null) return true
        // Az önce başarısız olduysa bir dakika bekle; sonra uygulama her açıldığında yine dener.
        if (System.currentTimeMillis() - lastFailAt < RETRY_AFTER_FAIL_MS) return false
        // Hesaplanan yerdeyken arada bir Diyanet listesine yeniden bakılır (belki artık bulunur).
        if (Store.isComputed(ctx) && System.currentTimeMillis() >= Store.computedRecheckAt(ctx)) return true
        val p = Store.autoPoint(ctx) ?: return true
        val out = FloatArray(1)
        Location.distanceBetween(p.first, p.second, loc.latitude, loc.longitude, out)
        return out[0] / 1000f > MOVE_KM
    }

    /**
     * Ağ kullanır: arka planda çağır.
     * [progress]: hangi adımda olunduğunu ekrana yazmak için (arka plan iş parçacığından çağrılır).
     *
     * Sonuç neredeyse her zaman Changed ya da Same olur. Failed yalnızca vakitlerin
     * hesaplanamadığı kutup bölgelerinde döner.
     */
    fun resolve(ctx: Context, loc: Location, progress: (String) -> Unit = {}): Result {
        progress("Şehir adı bulunuyor…")
        val names = namesFromGeocoder(ctx, loc.latitude, loc.longitude) ?: namesFromOpenStreetMap(loc)

        // ---- 1-3) Diyanet listesinde ara ----
        var found: Found? = null
        var timesJson: String? = null
        var hadError = names == null // yer adı alınamadıysa internet yok demektir
        if (names != null) {
            try {
                progress("Diyanet listesinde aranıyor: ${names.shown}")
                val lists = Lists()
                found = byName(ctx, lists, names, loc, progress)
                    ?: nearbyByName(ctx, lists, loc, progress)
                if (found != null && found.district.id != Store.districtId(ctx)) {
                    progress("${found.district.name} vakitleri indiriliyor…")
                    timesJson = Diyanet.fetchTimes(found.district.id)
                    if (Diyanet.parseDays(timesJson).isEmpty()) {
                        found = null
                        hadError = true
                    }
                }
            } catch (e: Exception) {
                // Diyanet sunucusuna ulaşılamadı: aşağıda konumdan hesaplanır, sonra yeniden denenir.
                found = null
                hadError = true
            }
        }

        if (found != null) {
            if (found.district.id == Store.districtId(ctx)) {
                Store.saveAutoPoint(ctx, loc.latitude, loc.longitude)
                Store.savePlacePoint(ctx, Pair(loc.latitude, loc.longitude))
                return Result.Same
            }
            val label = Diyanet.label(found.district, found.city, found.country)
            Store.saveLocation(ctx, found.district.id, label, timesJson ?: "")
            Store.saveAutoPoint(ctx, loc.latitude, loc.longitude)
            Store.savePlacePoint(ctx, Pair(loc.latitude, loc.longitude))
            return Result.Changed(label)
        }

        // ---- 4) Diyanet'te yakın yer yok: konumdan hesapla ----
        progress("Vakitler konumundan hesaplanıyor…")
        val today = LocalDate.now()
        if (PrayerCalc.diyanetDay(today, loc.latitude, loc.longitude, ZoneId.systemDefault()) == null) {
            return Result.Failed("Bu konum için vakit hesaplanamadı (güneşin batmadığı ya da doğmadığı bölge).")
        }
        Store.setComputedRecheckAt(
            ctx,
            System.currentTimeMillis() + if (hadError) COMPUTED_RECHECK_AFTER_ERROR_MS else COMPUTED_RECHECK_MS,
        )
        // Zaten hesaplanan yerdeysek ve konum pek değişmediyse nokta aynı kalır.
        val old = Store.placePoint(ctx)
        if (Store.isComputed(ctx) && old != null &&
            GeoMath.distanceKm(old.first, old.second, loc.latitude, loc.longitude) <= COMPUTED_SAME_KM
        ) {
            Store.saveAutoPoint(ctx, loc.latitude, loc.longitude)
            return Result.Same
        }
        val label = computedLabel(names, loc)
        Store.saveComputedLocation(ctx, label, Pair(loc.latitude, loc.longitude))
        Store.saveAutoPoint(ctx, loc.latitude, loc.longitude)
        return Result.Changed(label)
    }

    /** Hesaplanan yerin ekranda görünen adı: "Ambilly, Fransa"; ad yoksa koordinat. */
    private fun computedLabel(names: Names?, loc: Location): String {
        val place = names?.places?.firstOrNull { it.isNotBlank() }
        val country = names?.countries?.firstOrNull { it.isNotBlank() }
        val parts = listOfNotNull(place, country).distinctBy { norm(it) }
        if (parts.isNotEmpty()) return parts.joinToString(", ")
        return String.format(TR, "Konumun (%.2f°, %.2f°)", loc.latitude, loc.longitude)
    }

    // ---------------- Diyanet listesinde arama ----------------

    /**
     * 1. adım: bulunulan yerin kendi adlarıyla arama (ülke → şehir → ilçe).
     * Adı tutmayan bir yer ancak yakın olduğu doğrulanırsa kabul edilir.
     */
    private fun byName(ctx: Context, lists: Lists, names: Names, loc: Location, progress: (String) -> Unit): Found? {
        val country = PlaceMatch.best(Diyanet.countries(), names.countries) ?: return null
        val cities = lists.cities(country.id)
        val city = PlaceMatch.best(cities, names.places)
            ?: cities.singleOrNull()
            ?: PlaceMatch.best(cities, names.countries)
            ?: run {
                progress("En yakın şehir hesaplanıyor…")
                nearest(ctx, cities, country.name, loc, CITY_MAX_KM)
            }
            ?: return null

        val districts = lists.districts(city.id)
        PlaceMatch.best(districts, names.places)?.let { return Found(it, city, country) }

        // Tek ilçesi olan şehir: adı tutmasa da yakınsa odur (ör. küçük ülkeler).
        val context = "${city.name}, ${country.name}"
        districts.singleOrNull()?.let { only ->
            val p = Store.geoCache(ctx, only.id) ?: forward(ctx, "${only.name}, $context")
            if (p != null && GeoMath.distanceKm(p.first, p.second, loc.latitude, loc.longitude) <= DISTRICT_MAX_KM) {
                return Found(only, city, country)
            }
        }
        // Kısa listelerde (ör. İsviçre: 147 yer) her yerin koordinatı bulunup en yakını seçilir.
        // Uzun listelerde (ör. Fransa: 582 yer) bu çok uzun sürer; onlar için çevre araması var.
        progress("En yakın ilçe hesaplanıyor…")
        return nearest(ctx, districts, context, loc, DISTRICT_MAX_KM)?.let { Found(it, city, country) }
    }

    /** Çevre aramasında kullanılan sade eşleştirme: ilçe adı mutlaka tutmalı. */
    private fun strictByName(lists: Lists, names: Names): Found? {
        val country = PlaceMatch.best(Diyanet.countries(), names.countries) ?: return null
        val cities = lists.cities(country.id)
        val city = PlaceMatch.best(cities, names.places)
            ?: cities.singleOrNull()
            ?: PlaceMatch.best(cities, names.countries)
            ?: return null
        val district = PlaceMatch.best(lists.districts(city.id), names.places) ?: return null
        return Found(district, city, country)
    }

    /**
     * 2. adım: bulunulan yerin çevresindeki yerleşimleri Diyanet listesinde arar.
     *
     * Her halkada 8 yöne bakılır (kuzey, kuzeydoğu, doğu…): o noktanın yer adı telefondan
     * sorulur ve listede aranır. Yakın halkadan başlanır; ilk bulunan yer seçilir. Nokta
     * başka ülkeye düşerse o ülkenin listesine bakılır, yani ülke sınırı engel olmaz.
     * Telefonda yer adı hizmeti (Geocoder) yoksa bu adım atlanır.
     */
    private fun nearbyByName(ctx: Context, lists: Lists, loc: Location, progress: (String) -> Unit): Found? {
        if (!Geocoder.isPresent()) return null
        progress("Çevredeki yerler Diyanet listesinde aranıyor…")
        val deadline = System.currentTimeMillis() + 25_000L
        val tried = HashSet<String>() // aynı yer adı iki kez aranmasın
        val pool = Executors.newFixedThreadPool(4)
        try {
            for ((i, km) in RINGS_KM.withIndex()) {
                // Halkalar birbirine göre yarım adım döndürülür; böylece arada boşluk kalmaz.
                val turn = if (i % 2 == 1) 22.5 else 0.0
                val futures = (0 until 8).map { k ->
                    val p = GeoMath.offset(loc.latitude, loc.longitude, km, k * 45.0 + turn)
                    pool.submit(Callable<Names?> { namesFromGeocoder(ctx, p.first, p.second) })
                }
                for (f in futures) {
                    val wait = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
                    val n = (try { f.get(wait, TimeUnit.MILLISECONDS) } catch (e: Exception) { null }) ?: continue
                    val key = "${n.code}|${n.places.take(2).joinToString("|") { norm(it) }}"
                    if (!tried.add(key)) continue
                    strictByName(lists, n)?.let { return it }
                }
                if (System.currentTimeMillis() >= deadline) break
            }
            return null
        } finally {
            pool.shutdownNow()
        }
    }

    // ---------------- Yer adları ----------------

    private fun namesFromGeocoder(ctx: Context, lat: Double, lon: Double): Names? {
        if (!Geocoder.isPresent()) return null
        val tr = geocode(ctx, lat, lon, TR)
        val en = geocode(ctx, lat, lon, Locale.ENGLISH)
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
     * En yakın yer [maxKm]'den uzaksa null döner. 150'den uzun listelerde denenmez (çok uzun sürer).
     */
    private fun nearest(ctx: Context, list: List<Diyanet.Place>, context: String, loc: Location, maxKm: Float): Diyanet.Place? {
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
            return if (bestKm <= maxKm) best else null
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
    private fun geocode(ctx: Context, lat: Double, lon: Double, locale: Locale): Address? {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit(Callable<Address?> {
                Geocoder(ctx, locale).getFromLocation(lat, lon, 1)?.firstOrNull()
            }).get(8, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        } finally {
            executor.shutdownNow()
        }
    }
}

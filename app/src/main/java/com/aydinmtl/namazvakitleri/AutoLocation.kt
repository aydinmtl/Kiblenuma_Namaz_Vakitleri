package com.aydinmtl.namazvakitleri

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Konumdan Diyanet ilçesini bulur:
 *   1) Telefonun şehir bulma hizmeti (Geocoder) koordinatı ülke/il/şehir adına çevirir.
 *   2) Bu adlar Diyanet'in ülke → şehir → ilçe listelerinde aranır.
 * Diyanet listesinde koordinat olmadığı için eşleştirme isimle yapılır.
 */
object AutoLocation {

    /** Bu kadar km'den az yer değiştirdiyse tekrar arama yapılmaz. */
    private const val MOVE_KM = 15f

    sealed class Result {
        data class Changed(val label: String) : Result()
        object Same : Result()
        data class Failed(val reason: String) : Result()
    }

    /** Ülke adları bazı ülkelerde Geocoder ile Diyanet arasında farklı; bilinen karşılıklar. */
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
        if (!Geocoder.isPresent()) return Result.Failed("Bu telefonda şehir bulma hizmeti yok.")
        progress("Şehir adı bulunuyor…")
        val tr = geocode(ctx, loc, TR)
        val en = geocode(ctx, loc, Locale.ENGLISH)
        if (tr == null && en == null) return Result.Failed("Şehir adı bulunamadı (internet bağlantısını kontrol et).")

        val code = (tr?.countryCode ?: en?.countryCode)?.uppercase(Locale.ROOT)
        val countryNames = listOfNotNull(tr?.countryName, en?.countryName) +
            (code?.let { COUNTRY_ALIASES[it] } ?: emptyList())
        // Öncelik sırasıyla: şehir, ilçe/bölge, il/eyalet, semt
        val placeNames = listOfNotNull(
            tr?.locality, en?.locality,
            tr?.subAdminArea, en?.subAdminArea,
            tr?.adminArea, en?.adminArea,
            tr?.subLocality, en?.subLocality,
        )
        val shownName = tr?.locality ?: tr?.subAdminArea ?: tr?.adminArea ?: "Bulunduğun yer"

        progress("Diyanet listesinde aranıyor: $shownName")
        val country = bestMatch(Diyanet.countries(), countryNames)
            ?: return Result.Failed("${tr?.countryName ?: "Bu ülke"} Diyanet listesinde bulunamadı.")

        val cities = Diyanet.cities(country.id)
        val city = bestMatch(cities, placeNames)
            ?: cities.singleOrNull()
            ?: bestMatch(cities, countryNames)
            ?: return Result.Failed("$shownName için Diyanet şehri bulunamadı.")

        val districts = Diyanet.districts(city.id)
        val district = bestMatch(districts, placeNames)
            ?: districts.singleOrNull()
            ?: bestMatch(districts, listOf(city.name, city.nameEn))
            ?: return Result.Failed("$shownName için Diyanet ilçesi bulunamadı.")

        if (district.id == Store.districtId(ctx)) {
            Store.saveAutoPoint(ctx, loc.latitude, loc.longitude)
            return Result.Same
        }

        progress("${district.name} vakitleri indiriliyor…")
        val json = Diyanet.fetchTimes(district.id)
        if (Diyanet.parseDays(json).isEmpty()) return Result.Failed("${district.name} için vakit alınamadı.")
        val label = Diyanet.label(district, city, country)
        Store.saveLocation(ctx, district.id, label, json)
        Store.saveAutoPoint(ctx, loc.latitude, loc.longitude)
        return Result.Changed(label)
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

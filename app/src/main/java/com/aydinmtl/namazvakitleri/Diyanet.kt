package com.aydinmtl.namazvakitleri

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray

/**
 * Diyanet İşleri Başkanlığı'nın vakitlerini, aynı verileri anahtarsız sunan
 * ezanvakti.emushaf.net servisinden çeker.
 *
 *   /ulkeler            -> ülkeler   (UlkeID, UlkeAdi)
 *   /sehirler/{ulkeId}  -> şehirler  (SehirID, SehirAdi)
 *   /ilceler/{sehirId}  -> ilçeler   (IlceID, IlceAdi)
 *   /vakitler/{ilceId}  -> bugünden itibaren ~30 günlük vakitler
 *
 * Bu fonksiyonlar ağa bağlanır: ana (arayüz) iş parçacığında çağrılmamalı.
 */
object Diyanet {
    private const val BASE = "https://ezanvakti.emushaf.net"
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val KEYS = listOf("Imsak", "Gunes", "Ogle", "Ikindi", "Aksam", "Yatsi")

    /** [nameEn]: servisin verdiği İngilizce ad (yoksa boş). Otomatik konum eşleştirmesinde kullanılır. */
    data class Place(val id: String, val name: String, val nameEn: String = "")

    @Volatile
    private var countriesCache: List<Place>? = null

    /** Hangi şehir hangi ülkede: ilçe adlarının hangi harf kuralıyla yazılacağını bilmek için. */
    private val cityCountry = ConcurrentHashMap<String, String>()

    /** Yer adları Türkçe harf kurallarıyla yazılan ülkeler (norm() ile sadeleştirilmiş adları). */
    private val TURKISH_SPELLING_COUNTRIES = setOf("turkiye", "kuzeykibris", "azerbaycan")

    /** Ülke adları listede Türkçedir ("İSVİÇRE", "IRAK"): Türkçe harf kuralı. */
    fun countries(): List<Place> =
        countriesCache ?: places(get("$BASE/ulkeler"), "UlkeID", "UlkeAdi", "UlkeAdiEn", turkish = true)
            .also { countriesCache = it }

    fun cities(countryId: String): List<Place> =
        places(get("$BASE/sehirler/$countryId"), "SehirID", "SehirAdi", "SehirAdiEn", turkishSpelling(countryId))
            .also { list -> list.forEach { cityCountry[it.id] = countryId } }

    fun districts(cityId: String): List<Place> {
        // Şehrin ülkesi bilinmiyorsa (olmaması gerekir: önce hep şehir listesi indirilir) Türkçe kural.
        val turkish = cityCountry[cityId]?.let { turkishSpelling(it) } ?: true
        return places(get("$BASE/ilceler/$cityId"), "IlceID", "IlceAdi", "IlceAdiEn", turkish)
    }

    /**
     * Bu ülkedeki yer adları Türkçe harflerle mi yazılıyor?
     * Türkiye, Kuzey Kıbrıs ve Azerbaycan'da evet ("KADIKÖY" -> "Kadıköy").
     * Diğer ülkelerde hayır ("VERNIER" -> "Vernier"); bkz. titleCase.
     */
    private fun turkishSpelling(countryId: String): Boolean {
        val name = countries().firstOrNull { it.id == countryId }?.name ?: return false
        return norm(name) in TURKISH_SPELLING_COUNTRIES
    }

    /**
     * Ekranda görünen yer adı. Türkiye'de "İlçe, İl" (Kadıköy, İstanbul),
     * yurt dışında "Şehir, Ülke" (Cenevre, İsviçre).
     */
    fun label(district: Place, city: Place?, country: Place?): String {
        val second = if (country == null || norm(country.name) == "turkiye") city?.name else country.name
        return if (second == null || norm(second) == norm(district.name)) district.name else "${district.name}, $second"
    }

    /** Ham JSON döndürür; önbelleğe olduğu gibi yazılır. */
    fun fetchTimes(districtId: String): String = get("$BASE/vakitler/$districtId")

    fun parseDays(json: String): List<PrayerDay> {
        val out = ArrayList<PrayerDay>()
        val arr = try { JSONArray(json) } catch (e: Exception) { return out }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            try {
                val date = LocalDate.parse(o.getString("MiladiTarihKisa"), DATE)
                val times = KEYS.map { LocalTime.parse(o.getString(it).trim()) }
                out.add(PrayerDay(date, times, o.optString("HicriTarihUzun", ""), o.optString("HicriTarihKisa", "")))
            } catch (e: Exception) {
                // Bozuk bir gün varsa onu atla, diğerlerini kullan.
            }
        }
        return out.sortedBy { it.date.toEpochDay() }
    }

    private fun places(json: String, idKey: String, nameKey: String, enKey: String, turkish: Boolean): List<Place> {
        val arr = JSONArray(json)
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString(idKey, "")
            val name = o.optString(nameKey, "")
            val en = o.optString(enKey, "")
            if (id.isNotEmpty() && name.isNotEmpty()) out.add(Place(id, titleCase(name, turkish), en.trim()))
        }
        val collator = Collator.getInstance(TR)
        return out.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "Kiblenuma-Android/1.0")
        try {
            val code = c.responseCode
            if (code != 200) throw IOException("Sunucu yanıtı: HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

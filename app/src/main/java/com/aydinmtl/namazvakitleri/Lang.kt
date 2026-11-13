package com.aydinmtl.namazvakitleri

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Uygulamanın dili: Türkçe, Fransızca, İngilizce, Almanca.
 *
 * Nasıl çalışır? Android, ekrandaki yazıları `res/values/strings.xml` dosyasından alır.
 * Aynı yazıların çevirileri `values-fr`, `values-en`, `values-de` klasörlerindedir.
 * Android hangi klasörü kullanacağına "Configuration" içindeki dile bakarak karar verir.
 * Biz telefonun dilini değil, kullanıcının uygulamada seçtiği dili kullanmak istiyoruz;
 * bu yüzden her ekranı açarken Configuration'a seçilen dili koyuyoruz ([wrap]).
 *
 * Seçim yapılmamışsa uygulama eskisi gibi Türkçe açılır.
 */
object Lang {
    /** Seçicide görünen sıra. */
    val CODES = listOf("tr", "fr", "en", "de")

    /** Seçicideki uzun adlar (her dil kendi adıyla yazılır, çevrilmez). */
    val NATIVE_NAMES = mapOf(
        "tr" to "Türkçe",
        "fr" to "Français",
        "en" to "English",
        "de" to "Deutsch",
    )

    /** Seçili dilin kodu: "tr", "fr", "en" ya da "de". */
    fun code(ctx: Context): String = Store.language(ctx).takeIf { it in CODES } ?: "tr"

    /**
     * Seçili dilin Locale'i. Tarih ("13 Kasım" / "13 novembre") ve büyük/küçük harf
     * dönüşümleri bununla yapılır. Türkçe için tam "tr-TR" verilir (I/ı kuralı için).
     */
    fun locale(ctx: Context): Locale = when (code(ctx)) {
        "tr" -> TR
        else -> Locale.forLanguageTag(code(ctx))
    }

    fun set(ctx: Context, code: String) {
        if (code in CODES) Store.setLanguage(ctx, code)
    }

    /**
     * Verilen bağlamın (Context) seçili dilde yazı veren bir kopyasını döndürür.
     * Ekranlar (Activity) bunu açılırken kullanır; bildirim ve widget gibi ekransız
     * yerler de yazı almadan önce bundan geçer.
     */
    fun wrap(base: Context): Context {
        val loc = locale(base)
        val current = base.resources.configuration
        if (current.locales.size() > 0 && current.locales[0] == loc) return base
        val cfg = Configuration(current)
        cfg.setLocale(loc)
        return base.createConfigurationContext(cfg)
    }

    /** Seçili dilde tek bir yazı. Ekransız yerler (bildirim, widget, konum) için kısayol. */
    fun str(ctx: Context, id: Int, vararg args: Any): String = wrap(ctx).getString(id, *args)

    /** Hicri tarih seçili dilde: "5 Cemaziyelevvel 1448" / "5 Jumada al-Ula 1448 AH". */
    fun hijriDate(ctx: Context, h: Hijri.HDate): String {
        val c = wrap(ctx)
        val month = c.resources.getStringArray(R.array.hijri_months)[h.month - 1]
        return c.getString(R.string.hijri_date, h.day, month, h.year)
    }

    /** Dini günün seçili dildeki adı. */
    fun holyDayName(ctx: Context, h: Hijri.HolyDay): String {
        val c = wrap(ctx)
        return when (h.kind) {
            Hijri.HolyKind.NEW_YEAR -> c.getString(R.string.holy_new_year, h.n)
            Hijri.HolyKind.ASHURA -> c.getString(R.string.holy_ashura)
            Hijri.HolyKind.THREE_MONTHS -> c.getString(R.string.holy_three_months)
            Hijri.HolyKind.RAMADAN_START -> c.getString(R.string.holy_ramadan_start)
            Hijri.HolyKind.ADHA_EVE -> c.getString(R.string.holy_adha_eve)
            Hijri.HolyKind.MAWLID -> c.getString(R.string.holy_mawlid)
            Hijri.HolyKind.MIRAJ -> c.getString(R.string.holy_miraj)
            Hijri.HolyKind.BARAAT -> c.getString(R.string.holy_baraat)
            Hijri.HolyKind.QADR -> c.getString(R.string.holy_qadr)
            Hijri.HolyKind.FITR_EVE -> c.getString(R.string.holy_fitr_eve)
            Hijri.HolyKind.RAGHAIB -> c.getString(R.string.holy_raghaib)
            Hijri.HolyKind.FITR_DAY -> c.getString(R.string.holy_fitr_day, h.n)
            Hijri.HolyKind.ADHA_DAY -> c.getString(R.string.holy_adha_day, h.n)
        }
    }

    /** Hesaplama yönteminin seçili dildeki adı (sıra CalcMethod'daki sırayla aynı). */
    fun methodTitle(ctx: Context, m: CalcMethod): String =
        wrap(ctx).resources.getStringArray(R.array.method_titles).getOrElse(m.ordinal) { m.title }

    /** Ayarlar'da yöntemin altında görünen açıklama, seçili dilde. */
    fun methodDetails(ctx: Context, m: CalcMethod): String {
        val c = wrap(ctx)
        val where = c.resources.getStringArray(R.array.method_where).getOrElse(m.ordinal) { m.where }
        if (m == CalcMethod.DIYANET) return where
        val isha = if (m.ishaMinutes != null) {
            c.getString(R.string.method_isha_minutes, m.ishaMinutes)
        } else {
            c.getString(R.string.method_isha_angle, degrees(ctx, m.ishaAngle!!))
        }
        return c.getString(R.string.method_details, degrees(ctx, m.fajr), isha, where)
    }

    /** 18.0 -> "18", 19.5 -> "19,5" (İngilizcede "19.5"). */
    private fun degrees(ctx: Context, d: Double): String {
        if (d == kotlin.math.floor(d)) return d.toInt().toString()
        return if (code(ctx) == "en") d.toString() else d.toString().replace('.', ',')
    }

    /** Vaktin seçili dildeki adı: 0 İmsak … 5 Yatsı. */
    fun prayerName(ctx: Context, index: Int): String =
        wrap(ctx).resources.getStringArray(R.array.prayer_names).getOrElse(index) { PRAYER_NAMES[index] }
}

/**
 * Bütün ekranların ortak atası: açılırken seçili dili uygular.
 * Yeni bir ekran yazarken `Activity()` yerine `LangActivity()` kullanmak yeterlidir.
 */
open class LangActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }
}

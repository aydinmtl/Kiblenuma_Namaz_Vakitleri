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

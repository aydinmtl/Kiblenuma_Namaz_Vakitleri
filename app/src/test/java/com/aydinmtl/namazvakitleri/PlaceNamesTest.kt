package com.aydinmtl.namazvakitleri

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Yer ve ülke adlarının seçili dilde gösterilmesi (CountryNames, PlaceNames, Lang.pick). */
class PlaceNamesTest {
    private val fr = Locale.FRENCH
    private val de = Locale.GERMAN
    private val en = Locale.ENGLISH

    @Test
    fun diyanetCountryNumbersMapToIsoCodes() {
        assertEquals("CH", CountryNames.code("49")) // İsviçre
        assertEquals("FR", CountryNames.code("21")) // Fransa
        assertEquals("DE", CountryNames.code("13")) // Almanya
        assertEquals("TR", CountryNames.code("2"))
        assertNull(CountryNames.code("1")) // Kuzey Kıbrıs: uluslararası kodu yok
        assertNull(CountryNames.code("9999"))
        assertNull(CountryNames.code(null))
    }

    @Test
    fun countryNameIsWrittenInTheSelectedLanguage() {
        assertEquals("Suisse", CountryNames.display("CH", fr))
        assertEquals("Schweiz", CountryNames.display("CH", de))
        assertEquals("Switzerland", CountryNames.display("CH", en))
        assertEquals("France", CountryNames.display("FR", fr))
        assertEquals("Frankreich", CountryNames.display("FR", de))
        assertEquals("Allemagne", CountryNames.display("DE", fr))
        assertNull(CountryNames.display(null, fr))
        assertNull(CountryNames.display("", fr))
    }

    @Test
    fun everyTableEntryHasANameInAllLanguages() {
        // Tablodaki her kodun telefon sözlüğünde bir adı olmalı; yoksa ekranda "XK" gibi bir kod görünürdü.
        for (id in 1..210) {
            val code = CountryNames.code(id.toString()) ?: continue
            for (locale in listOf(fr, de, en)) {
                val name = CountryNames.display(code, locale)
                assertTrue("$id/$code/$locale", name != null && name.length > 2)
            }
        }
    }

    @Test
    fun labelGetsCountryInSelectedLanguageWhenCityNameIsNotKnownYet() {
        assertEquals("Cenevre, Suisse", PlaceNames.withLocalCountry("Cenevre, İsviçre", "CH", true, fr))
        assertEquals("Annemasse, France", PlaceNames.withLocalCountry("Annemasse, Fransa", "FR", true, fr))
        assertEquals("Annemasse, Frankreich", PlaceNames.withLocalCountry("Annemasse, Fransa", "FR", true, de))
        // Türkiye'de son parça ildir: dokunulmaz.
        assertEquals("Kadıköy, İstanbul", PlaceNames.withLocalCountry("Kadıköy, İstanbul", "TR", false, fr))
        // Ülke kodu bilinmiyorsa (eski kayıt) etiket olduğu gibi kalır.
        assertEquals("Cenevre, İsviçre", PlaceNames.withLocalCountry("Cenevre, İsviçre", null, true, fr))
        assertEquals("Cenevre", PlaceNames.withLocalCountry("Cenevre", "CH", true, fr))
    }

    @Test
    fun labelIsBuiltFromNamesGivenByThePhone() {
        assertEquals("Genève, Suisse", PlaceNames.compose("Cenevre, İsviçre", "Genève", "Suisse"))
        assertEquals("Genf, Schweiz", PlaceNames.compose("Cenevre, İsviçre", " Genf ", "Schweiz"))
        // Asıl etiket tek parçalıysa ülke eklenmez.
        assertEquals("Genève", PlaceNames.compose("Cenevre", "Genève", "Suisse"))
        // Yer adı ülke adıyla aynıysa bir kez yazılır (ör. Lüksemburg).
        assertEquals("Luxembourg", PlaceNames.compose("Lüksemburg, Lüksemburg", "Luxembourg", "Luxembourg"))
        assertNull(PlaceNames.compose("Cenevre, İsviçre", null, "Suisse"))
        assertNull(PlaceNames.compose("Cenevre, İsviçre", "  ", "Suisse"))
        assertEquals("Genève", PlaceNames.compose("Cenevre, İsviçre", "Genève", null))
    }

    @Test
    fun diyanetLabelKnowsWhetherItEndsWithCountry() {
        val geneva = Diyanet.Place("14550", "Cenevre")
        val switzerland = Diyanet.Place("49", "İsviçre", "SWITZERLAND")
        val kadikoy = Diyanet.Place("9541", "Kadıköy")
        val turkey = Diyanet.Place("2", "Türkiye", "TÜRKİYE")
        val luxembourg = Diyanet.Place("31", "Lüksemburg", "LUXEMBOURG")
        assertTrue(Diyanet.labelEndsWithCountry(geneva, switzerland))
        assertFalse(Diyanet.labelEndsWithCountry(kadikoy, turkey))
        assertFalse(Diyanet.labelEndsWithCountry(Diyanet.Place("x", "Lüksemburg"), luxembourg)) // etiket tek parça
        assertFalse(Diyanet.labelEndsWithCountry(geneva, null))
    }

    @Test
    fun defaultLanguageFollowsThePhoneOnNewInstallsOnly() {
        assertEquals("de", Lang.pick("de", existingInstall = false))
        assertEquals("fr", Lang.pick("fr", existingInstall = false))
        assertEquals("tr", Lang.pick("tr", existingInstall = false))
        assertEquals("en", Lang.pick("it", existingInstall = false)) // desteklenmeyen dil: İngilizce
        assertEquals("tr", Lang.pick("de", existingInstall = true)) // eski kurulum: Türkçe kalır
    }
}

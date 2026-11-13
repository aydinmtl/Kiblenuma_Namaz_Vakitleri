package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Yer adı sadeleştirme (norm), Türkçe büyük harf (titleCase) ve Diyanet listesiyle eşleştirme. */
class PlaceMatchTest {

    @Test
    fun norm_turkce_ve_aksanli_harfleri_sadelestirir() {
        assertEquals("isvicre", norm("İSVİÇRE"))
        assertEquals("geneve", norm("Genève"))
        assertEquals("kadikoy", norm("Kadıköy"))
        assertEquals("thonex", norm("Thônex"))
        assertEquals("strasse", norm("Straße"))
        assertEquals("sanktgallen", norm("Sankt-Gallen"))
        assertEquals("", norm(null))
        assertEquals("", norm("   "))
    }

    @Test
    fun titleCase_turkce_kurallarla() {
        assertEquals("İsviçre", titleCase("İSVİÇRE"))
        assertEquals("Kadıköy", titleCase("KADIKÖY"))
        assertEquals("Bosna Hersek", titleCase("BOSNA HERSEK"))
        assertEquals("Irak", titleCase("IRAK"))
        assertEquals("Isparta", titleCase("ISPARTA"))
        assertEquals("İstanbul", titleCase("İSTANBUL"))
        assertEquals("Kuzey Kıbrıs", titleCase("KUZEY KIBRIS"))
    }

    @Test
    fun titleCase_yabanci_adlarda_I_harfi_i_olur() {
        // Diyanet listesi İsviçre'deki yerleri düz I ile verir; Türkçe kural "Vernıer" yapardı.
        assertEquals("Vernier", titleCase("VERNIER", turkish = false))
        assertEquals("Winterthur", titleCase("WINTERTHUR", turkish = false))
        assertEquals("Zurih", titleCase("ZURIH", turkish = false))
        assertEquals("Interlaken", titleCase("INTERLAKEN", turkish = false))
        assertEquals("Biel", titleCase("BIEL", turkish = false))
        assertEquals("Yverdon Les Bains", titleCase("YVERDON LES BAINS", turkish = false))
        // Noktalı İ yabancı adda da sıradan i olur.
        assertEquals("Berlin", titleCase("BERLİN", turkish = false))
    }

    @Test
    fun titleCase_yabanci_ulkede_turkce_yazilmis_ad() {
        // Kelimede yalnızca Türkçede olan harf (İ, Ş, Ğ) varsa o kelime Türkçe kuralla yazılır.
        assertEquals("Kırcaali", titleCase("KIRCAALİ", turkish = false))
        assertEquals("Şamahı", titleCase("ŞAMAHI", turkish = false))
    }

    @Test
    fun titleCase_bosluk_parantez_ve_nokta() {
        assertEquals("Biberist", titleCase("BIBERIST ", turkish = false))
        assertEquals("Ruti (B)", titleCase("RUTI (B)", turkish = false))
        assertEquals("Au (Zurih)", titleCase("AU (Zurih)", turkish = false))
        assertEquals("St. Moritz", titleCase("ST. MORITZ", turkish = false))
        assertEquals("Wil (Sg)", titleCase("WIL (SG)", turkish = false))
        assertEquals("L'Isle", titleCase("L'ISLE", turkish = false))
        assertEquals("Ağrı'nın", titleCase("AĞRI'NIN"))
    }

    private val cities = listOf(
        Diyanet.Place("1", "Basel", "Basel"),
        Diyanet.Place("2", "Bern", "Bern"),
        Diyanet.Place("3", "Cenevre", "Geneva"),
        Diyanet.Place("4", "Frankfurt", "Frankfurt"),
        Diyanet.Place("5", "Zürih", "Zurich"),
    )

    @Test
    fun turkce_adla_birebir() {
        // Telefon Türkçe "Cenevre" verirse
        assertEquals("3", PlaceMatch.best(cities, listOf("Thônex", "Cenevre"))!!.id)
    }

    @Test
    fun ingilizce_adla_birebir() {
        assertEquals("3", PlaceMatch.best(cities, listOf("Geneva"))!!.id)
        assertEquals("5", PlaceMatch.best(cities, listOf("Zürich"))!!.id)
    }

    @Test
    fun fransizca_Geneve_eslesmez() {
        // "Genève" ile "Geneva" ne birebir ne de baş kısmı tutuyor. Bu durumda otomatik konum
        // diğer adlara (Türkçe "Cenevre") ya da en yakın yer hesabına güvenir.
        assertNull(PlaceMatch.best(cities, listOf("Genève")))
    }

    @Test
    fun bas_kismi_tutan_eslesme() {
        assertEquals("4", PlaceMatch.best(cities, listOf("Frankfurt am Main"))!!.id)
    }

    @Test
    fun birebir_eslesme_bas_kismindan_once_gelir() {
        // İlk ad yalnızca baş kısmıyla tutuyor, ikinci ad birebir: birebir kazanır.
        assertEquals("2", PlaceMatch.best(cities, listOf("Baselland", "Bern"))!!.id)
    }

    @Test
    fun kisa_adlar_eslesmeye_katilmaz() {
        assertNull(PlaceMatch.best(cities, listOf("GE", "", null)))
        // 3 harfli ad yalnızca birebir eşleşebilir, baş kısmı eşleşmesine giremez.
        assertNull(PlaceMatch.best(cities, listOf("Bas")))
    }

    @Test
    fun hic_ad_yoksa_null() {
        assertNull(PlaceMatch.best(cities, emptyList()))
        assertNull(PlaceMatch.best(emptyList(), listOf("Bern")))
    }

    @Test
    fun ekranda_gorunen_yer_adi() {
        val tr = Diyanet.Place("t", "Türkiye")
        val ist = Diyanet.Place("c", "İstanbul")
        val ch = Diyanet.Place("ch", "İsviçre")
        val gen = Diyanet.Place("g", "Cenevre")
        assertEquals("Kadıköy, İstanbul", Diyanet.label(Diyanet.Place("d", "Kadıköy"), ist, tr))
        assertEquals("Cenevre, İsviçre", Diyanet.label(gen, gen, ch))
        // İlçe adı ile il adı aynıysa tekrar yazılmaz
        assertEquals("İstanbul", Diyanet.label(Diyanet.Place("d", "İstanbul"), ist, tr))
    }
}

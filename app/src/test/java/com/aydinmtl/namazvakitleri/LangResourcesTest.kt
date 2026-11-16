package com.aydinmtl.namazvakitleri

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Çeviri dosyalarını denetler (values, values-fr, values-en, values-de):
 *  - her dilde aynı yazılar var mı (biri eksik kalırsa o yazı Türkçe görünürdü),
 *  - "%1$s", "%2$d" gibi yer tutucular her dilde aynı mı (yanlışsa uygulama çöker),
 *  - listelerde (vakit adları, Hicri aylar) öğe sayısı aynı mı.
 */
class LangResourcesTest {

    private val res = File("src/main/res")

    /** Her dilde aynı olduğu için yalnızca Türkçe dosyada duran yazılar. */
    private val sameInAllLanguages = setOf("app_name", "widget_big_label", "widget_small_label")

    private val placeholder = Regex("%\\d+\\$[.0-9]*[sdf]")

    private class Entry(val texts: List<String>)

    private fun read(folder: String): Map<String, Entry> {
        val file = File(res, "$folder/strings.xml")
        assertTrue("$file bulunamadı", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = LinkedHashMap<String, Entry>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val items = e.getElementsByTagName("item")
            val texts = if (items.length == 0) {
                listOf(e.textContent)
            } else {
                (0 until items.length).map { items.item(it).textContent }
            }
            out[e.tagName + ":" + e.getAttribute("name")] = Entry(texts)
        }
        return out
    }

    private fun placeholders(s: String): Set<String> = placeholder.findAll(s).map { it.value }.toSet()

    /** Tarih kalıpları (date_full, date_long, date_short) her dilde geçerli olmalı; bozuk kalıp ekranı çökertir. */
    @Test
    fun datePatternsFormatInEveryLanguage() {
        val date = LocalDate.of(2026, 11, 15)
        val languages = listOf("values" to "tr-TR", "values-fr" to "fr", "values-en" to "en", "values-de" to "de")
        for ((folder, tag) in languages) {
            val strings = read(folder)
            for (name in listOf("date_full", "date_long", "date_short")) {
                val pattern = strings.getValue("string:$name").texts[0]
                val text = date.format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag(tag)))
                assertTrue("$folder $name: \"$text\"", text.contains("15"))
            }
        }
        // Almancada gün sayısından sonra nokta konur.
        val german = read("values-de").getValue("string:date_full").texts[0]
        assertEquals("Sonntag, 15. November 2026", date.format(DateTimeFormatter.ofPattern(german, Locale.GERMAN)))
    }

    @Test
    fun translationsMatchTurkish() {
        val tr = read("values")
        for (lang in listOf("fr", "en", "de")) {
            val other = read("values-$lang")
            val expectedKeys = tr.keys.filter { it.substringAfter(':') !in sameInAllLanguages }.toSet()
            assertEquals("values-$lang: yazı adları Türkçeyle aynı olmalı", expectedKeys, other.keys)
            for ((key, entry) in other) {
                val base = tr.getValue(key)
                val isArray = key.startsWith("string-array:")
                if (isArray) {
                    assertEquals("$lang $key: liste uzunluğu", base.texts.size, entry.texts.size)
                } else {
                    // Çoğul yazılarda ("one", "other") hepsinin yer tutucuları birleşik karşılaştırılır.
                    val want = base.texts.flatMap { placeholders(it) }.toSet()
                    val got = entry.texts.flatMap { placeholders(it) }.toSet()
                    assertEquals("$lang $key: yer tutucular", want, got)
                }
            }
        }
    }
}

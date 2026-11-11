package com.aydinmtl.namazvakitleri

/**
 * Telefonun bulduğu yer adlarını (Thônex, Genève, İsviçre…) Diyanet listesindeki yerlerle eşleştirir.
 *
 * Android'e bağlı hiçbir şey kullanmaz; bu yüzden bilgisayarda birim testiyle denenebilir
 * (bkz. app/src/test/.../PlaceMatchTest.kt). Otomatik konum (AutoLocation) bunu kullanır.
 */
object PlaceMatch {

    /**
     * [names] listesindeki adlardan biriyle eşleşen ilk yeri döndürür (yoksa null).
     *
     * 1) Önce birebir eşleşme aranır: "Genève" == "Geneve" (norm() harfleri sadeleştirir).
     * 2) Bulunamazsa baş kısmı tutan eşleşme: "Frankfurt am Main" ~ "Frankfurt" gibi.
     *    Bu adımda iki ad da en az 4 harf olmalı; kısa adlar yanlış eşleşme yapmasın.
     *
     * [names] öncelik sırasıyla verilir: listedeki ilk ad önce denenir.
     */
    fun best(list: List<Diyanet.Place>, names: List<String?>): Diyanet.Place? {
        // 3 harften kısa adlar ("", "GE" gibi) eşleştirmeye katılmaz.
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

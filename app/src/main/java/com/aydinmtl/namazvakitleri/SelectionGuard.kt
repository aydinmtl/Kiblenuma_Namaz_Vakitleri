package com.aydinmtl.namazvakitleri

/**
 * Seçim, veri yenileme ve sonuç kabulünü aynı kilitle sıralar.
 *
 * İki ayrı sayaç vardır; ikisini ayırmak önemlidir:
 *
 *  - YER sayacı yalnızca SEÇİLİ YER değişince artar (yeni şehir, elle seçim, otomatik konumun
 *    açılıp kapanması). Arka planda süren bir konum işi, başladığı andaki yer sayacı hâlâ aynıysa
 *    sonucunu yazabilir. Bildirim ayarı, hesap yöntemi ya da vakit yenilemesi bu sayacı ARTIRMAZ;
 *    böylece ilgisiz bir ayar değişikliği süren işin sonucunu çöpe atmaz.
 *
 *  - VERİ sayacı alarmı etkileyen HER değişiklikte artar (yer, vakit listesi, koordinat, ayarlar).
 *    Hesaplanmış bir alarm, veri sayacı hâlâ aynıysa kurulabilir; değilse hesap eskimiştir.
 */
class SelectionGuard {
    private var place = 0L
    private var data = 0L
    private var pendingManual: Long? = null

    /** Yer isteği başlatır: değeri o anki YER sayacıyla birlikte okur. */
    @Synchronized
    fun <T> capture(read: () -> T): Pair<Long, T> = place to read()

    /** Alarm ve vakit hesabı için: değeri o anki VERİ sayacıyla birlikte okur. */
    @Synchronized
    fun <T> captureData(read: () -> T): Pair<Long, T> = data to read()

    /** Otomatik konum isteği; elle seçim sürüyorsa başlatılamaz (null). */
    @Synchronized
    fun <T> automatic(read: () -> T): Pair<Long, T>? =
        if (pendingManual != null) null else place to read()

    @Synchronized
    fun beginManual(): Long {
        place++
        data++
        pendingManual = place
        return place
    }

    @Synchronized
    fun cancelManual(token: Long) {
        if (pendingManual == token) {
            pendingManual = null
            place++
            data++
        }
    }

    @Synchronized
    fun finishManual(token: Long, write: () -> Unit): Boolean {
        if (pendingManual != token) return false
        try {
            write()
        } finally {
            pendingManual = null
            place++
            data++
        }
        return true
    }

    /** Ayar ya da veri değişimi (yer aynı kalır): yalnızca VERİ sayacı artar. */
    @Synchronized
    fun change(write: () -> Unit) {
        data++
        write()
    }

    /** Seçili yerin değişimi: iki sayaç da artar; süren yer istekleri geçersiz kalır. */
    @Synchronized
    fun changePlace(write: () -> Unit) {
        place++
        data++
        write()
    }

    /** [condition] kilit altında doğruysa yazar (ör. "seçili ilçe hâlâ indirdiğim ilçe mi?"). */
    @Synchronized
    fun changeIf(condition: () -> Boolean, write: () -> Unit): Boolean {
        if (!condition()) return false
        data++
        write()
        return true
    }

    @Synchronized
    fun isCurrentRequest(expected: Long): Boolean = expected == place && pendingManual == null

    /** Yer isteğinin sonucunu yazar; istek başladığından beri yer değiştiyse yazmaz. */
    @Synchronized
    fun apply(expected: Long, write: () -> Unit): Boolean {
        if (!isCurrentRequest(expected)) return false
        data++
        write()
        return true
    }

    /** [expected] VERİ sayacı hâlâ geçerliyse yayımlar (alarm kurma/iptal). */
    @Synchronized
    fun ifCurrent(expected: Long, publish: () -> Unit): Boolean {
        if (expected != data) return false
        publish()
        return true
    }
}

/** Hesap kilitsiz yapılır; yalnızca en yeni çağrı güncel veriyi yayımlayabilir. */
internal class GuardedPublication(private val selection: SelectionGuard) {
    private var latest = 0L

    fun <S, T> run(capture: () -> Pair<Long, S>, compute: (S) -> T, publish: (T) -> Unit): Boolean {
        val ticket = synchronized(this) { ++latest }
        val (generation, state) = capture()
        val result = compute(state)
        var accepted = false
        selection.ifCurrent(generation) {
            synchronized(this) {
                if (ticket == latest) {
                    publish(result)
                    accepted = true
                }
            }
        }
        return accepted
    }

    /**
     * [run] reddedilirse taze veriyle yeniden dener (en çok [attempts] kez).
     * Reddin iki nedeni olabilir: hesap sürerken veri değişti ya da daha yeni bir çağrı geldi.
     * İkisinde de yeniden okunan veri günceldir; son yayımlanan her zaman güncel hesaptır.
     */
    fun <S, T> runUntilAccepted(
        attempts: Int,
        capture: () -> Pair<Long, S>,
        compute: (S) -> T,
        publish: (T) -> Unit,
    ): Boolean {
        repeat(attempts) {
            if (run(capture, compute, publish)) return true
        }
        return false
    }
}

package com.aydinmtl.namazvakitleri

import java.time.LocalDateTime

/**
 * Hangi alarmın ne zaman çalacağını ve geciken bir alarmda ne yapılacağını belirleyen kurallar.
 * Android'e bağlı değil; bilgisayarda test edilebilir (bkz. AlarmPlanTest).
 */
object AlarmPlan {

    /** Bir alarm olayı: vakit girişi ya da vakitten önce hatırlatma. */
    data class Event(val moment: Moment, val reminder: Boolean, val fireAt: LocalDateTime)

    /**
     * [after] anından sonraki ilk olay.
     * [remindMinutes]: vakitten kaç dakika önce hatırlatılsın (0: hatırlatma yok).
     * [remindFor]: o vakit için hatırlatma isteniyor mu (vakit sırası 0..5).
     *
     * Bir hatırlatma başka bir vaktin girişiyle AYNI dakikaya denk gelirse (ör. 30 dk önce
     * hatırlatma ile güneşin doğuşu aynı an) hatırlatma 1 dakika öne alınır. Eskiden ikisinden
     * biri sessizce atlanıyordu, çünkü her seferinde yalnızca bir alarm kurulur.
     */
    fun next(
        moments: List<Moment>,
        remindMinutes: Int,
        remindFor: (Int) -> Boolean,
        after: LocalDateTime,
    ): Event? {
        val entries = moments.map { Event(it, false, it.at) }
        val entryTimes = entries.map { it.fireAt }.toHashSet()
        val reminders = if (remindMinutes <= 0) emptyList() else moments
            .filter { remindFor(it.index) }
            .map { m ->
                var t = m.at.minusMinutes(remindMinutes.toLong())
                if (t in entryTimes) t = t.minusMinutes(1)
                Event(m, true, t)
            }
        return (entries + reminders)
            .filter { it.fireAt.isAfter(after) }
            .minByOrNull { it.fireAt }
    }

    /** Ezan, vakit girdikten en fazla bu kadar sonra çalınır (daha geç çalması yanıltıcı olur). */
    const val ADHAN_MAX_LATE_MS = 15 * 60_000L

    /** Vakit bildirimi, alarm en fazla bu kadar gecikse de gösterilir (sessiz değil, normal). */
    const val PRAYER_MAX_LATE_MS = 60 * 60_000L

    /**
     * Geciken alarm için bildirim gösterilsin mi?
     * - Hatırlatma: vakit henüz girmediyse göster; girdiyse artık anlamı yok.
     * - Vakit girişi: alarm en fazla 1 saat geciktiyse göster.
     *
     * Eskiden ikisi için de 15 dakika sınırı vardı. "Alarmlar ve hatırlatıcılar" izni yoksa
     * Android pil tasarrufu (Doze) alarmı daha fazla geciktirebiliyor ve bildirim hiç gelmiyordu.
     * Telefon kapalıyken kaçan alarmlar zaten çalmaz; açılınca yalnızca sıradaki kurulur.
     */
    fun shouldNotify(reminder: Boolean, nowMs: Long, fireMs: Long, atMs: Long): Boolean =
        if (reminder) nowMs < atMs else nowMs - fireMs < PRAYER_MAX_LATE_MS

    /** Ezan hâlâ çalınabilir mi (vakit girişinden bu yana 15 dakikadan az mı geçti)? */
    fun adhanInTime(nowMs: Long, atMs: Long): Boolean = nowMs - atMs < ADHAN_MAX_LATE_MS
}

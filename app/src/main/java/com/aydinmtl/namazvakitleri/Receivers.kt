package com.aydinmtl.namazvakitleri

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Vakit girdiğinde AlarmManager tarafından çağrılır. */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val index = intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, -1)
        val atMs = intent.getLongExtra(AlarmScheduler.EXTRA_AT, 0L)
        val at = Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()).toLocalDateTime()

        // Telefon uzun süre kapalı kaldıysa eski vakit için bildirim gösterme (15 dk tolerans).
        val late = System.currentTimeMillis() - atMs
        if (index in PRAYER_NAMES.indices && late < 15 * 60_000L &&
            Store.notificationsOn(app) && Store.notifyFor(app, index)
        ) {
            Notifications.showPrayer(app, Moment(index, at))
        }

        val now = LocalDateTime.now()
        val after = if (at.isAfter(now)) at else now
        PrayerWidget.updateAll(app)
        AlarmScheduler.schedule(app, after)

        if (Store.needsRefresh(app)) {
            val pending = goAsync()
            Thread {
                try {
                    if (Store.refresh(app)) {
                        AlarmScheduler.schedule(app, after)
                        PrayerWidget.updateAll(app)
                    }
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }
}

/** Telefon açılınca, saat/saat dilimi değişince ya da uygulama güncellenince alarmı yeniden kurar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.schedule(context)
        PrayerWidget.updateAll(context)
    }
}

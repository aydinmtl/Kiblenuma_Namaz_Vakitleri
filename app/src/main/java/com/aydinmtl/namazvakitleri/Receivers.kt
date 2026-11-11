package com.aydinmtl.namazvakitleri

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Vakit girdiğinde ya da hatırlatma zamanı geldiğinde AlarmManager tarafından çağrılır. */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val zone = ZoneId.systemDefault()
        val index = intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, -1)
        val atMs = intent.getLongExtra(AlarmScheduler.EXTRA_AT, 0L)
        val fireMs = intent.getLongExtra(AlarmScheduler.EXTRA_FIRE, atMs)
        val reminder = intent.getBooleanExtra(AlarmScheduler.EXTRA_REMINDER, false)
        val at = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDateTime()
        val fire = Instant.ofEpochMilli(fireMs).atZone(zone).toLocalDateTime()

        // Telefon uzun süre kapalı kaldıysa eski olay için uyarı verme (15 dk tolerans).
        val late = System.currentTimeMillis() - fireMs
        val notify = index in PRAYER_NAMES.indices && late < 15 * 60_000L &&
            Store.notificationsOn(app) && Store.notifyFor(app, index)

        if (notify) {
            val m = Moment(index, at)
            if (reminder) {
                Notifications.showReminder(app, m, Store.reminderMinutes(app))
            } else {
                val playAdhan = index != SUNRISE && Store.adhanOn(app) && AdhanService.allowedNow(app)
                // Önce ezanı başlatmayı dene. Android izin vermezse (ör. "Alarmlar ve hatırlatıcılar"
                // izni yoksa) bildirim SESLİ gönderilir; yoksa kullanıcı vakti hiç duymaz.
                val adhanStarted = playAdhan && AdhanService.start(app, m)
                Notifications.showPrayer(app, m, silent = adhanStarted)
            }
        }

        val now = LocalDateTime.now()
        val after = if (fire.isAfter(now)) fire else now
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

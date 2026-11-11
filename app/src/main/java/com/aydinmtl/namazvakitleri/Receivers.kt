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

        // Alarm çok geciktiyse eski olay için uyarı verme (kurallar AlarmPlan.shouldNotify'da).
        val nowMs = System.currentTimeMillis()
        val notify = index in PRAYER_NAMES.indices &&
            AlarmPlan.shouldNotify(reminder, nowMs, fireMs, atMs) &&
            Store.notificationsOn(app) && Store.notifyFor(app, index)

        if (notify) {
            val m = Moment(index, at)
            if (reminder) {
                Notifications.showReminder(app, m, Store.reminderMinutes(app))
            } else {
                val playAdhan = index != SUNRISE && Store.adhanOn(app) && AdhanService.allowedNow(app) &&
                    AlarmPlan.adhanInTime(nowMs, atMs) // 15 dakikadan geç ezan çalınmaz
                // Önce ezanı başlatmayı dene. Android izin vermezse (ör. "Alarmlar ve hatırlatıcılar"
                // izni yoksa) bildirim SESLİ gönderilir; yoksa kullanıcı vakti hiç duymaz.
                val adhanStarted = playAdhan && AdhanService.start(app, m)
                Notifications.showPrayer(app, m, silent = adhanStarted)
            }
        }

        // Aile Halkası: akşam vakti girince günün sorusu (açıksa ve bugün henüz cevaplanmadıysa)
        if (index == MAGHRIB && !reminder && Store.dailyNotify(app) &&
            AlarmPlan.shouldNotify(false, nowMs, fireMs, atMs) &&
            Store.dailyDoneDate(app) != at.toLocalDate().toString()
        ) {
            val id = QuizDeck.dailyId(QuizBank.all(app).map { it.id }, at.toLocalDate())
            QuizBank.byId(app, id ?: "")?.let { Notifications.showDailyQuestion(app, it.text) }
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

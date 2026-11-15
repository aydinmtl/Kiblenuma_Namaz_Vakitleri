package com.aydinmtl.namazvakitleri

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant

/**
 * Her seferinde yalnızca SIRADAKİ olay için bir alarm kurar. Olaylar:
 *   - vakit girişi (bildirim + ezan + widget yenileme)
 *   - vakitten X dakika önce hatırlatma (açıksa)
 * Alarm çalınca PrayerAlarmReceiver işini yapar ve bir sonrakini kurar.
 */
object AlarmScheduler {
    const val EXTRA_INDEX = "index"
    const val EXTRA_AT = "at"             // vaktin kendisi (ms)
    const val EXTRA_FIRE = "fire"         // alarmın çaldığı an (ms)
    const val EXTRA_REMINDER = "reminder" // true: önceden hatırlatma
    const val EXTRA_CLOCK = "clock"       // seçili yerin saati (gece yarısından itibaren saniye)
    const val EXTRA_DAY = "day"           // vaktin ait olduğu takvim satırı (epoch gün)

    private val publisher = Store.alarmPublisher()

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    /** [after] anından sonraki ilk olay. Kurallar AlarmPlan.next içinde. */
    fun nextEvent(ctx: Context, after: Instant): AlarmPlan.Event? {
        return nextEvent(Store.alarmSnapshot(ctx).second, after)
    }

    private fun nextEvent(state: AlarmSnapshot, after: Instant): AlarmPlan.Event? = AlarmPlan.next(
        Prayers.moments(Times.days(state.prayers)), state.reminderMinutes,
        remindFor = { state.notifyFor[it] }, after = after,
    )

    fun schedule(ctx: Context, after: Instant = Instant.now()) {
        val app = ctx.applicationContext
        DataRefresh.ensureScheduled(app)
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        publisher.run(
            capture = { Store.alarmSnapshot(app) },
            compute = { nextEvent(it, after) },
            publish = { publish(app, am, it) },
        )
    }

    private fun publish(app: Context, am: AlarmManager, next: AlarmPlan.Event?) {
        val intent = Intent(app, PrayerAlarmReceiver::class.java)
        if (next == null) {
            val old = PendingIntent.getBroadcast(app, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            if (old != null) am.cancel(old)
            return
        }

        val fireMs = next.fireInstant.toEpochMilli()
        intent.putExtra(EXTRA_INDEX, next.moment.index)
        intent.putExtra(EXTRA_AT, next.moment.instant.toEpochMilli())
        intent.putExtra(EXTRA_CLOCK, next.moment.clock.toSecondOfDay())
        intent.putExtra(EXTRA_DAY, next.moment.day.toEpochDay())
        intent.putExtra(EXTRA_FIRE, fireMs)
        intent.putExtra(EXTRA_REMINDER, next.reminder)
        val pi = PendingIntent.getBroadcast(app, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        try {
            if (canExact(app)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMs, pi)
            } else {
                // İzin yoksa birkaç dakika gecikebilen alarm.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMs, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMs, pi)
        }
    }
}

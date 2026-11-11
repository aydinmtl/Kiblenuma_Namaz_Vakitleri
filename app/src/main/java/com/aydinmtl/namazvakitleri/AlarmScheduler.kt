package com.aydinmtl.namazvakitleri

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

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

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    /** [after] anından sonraki ilk olay. Kurallar AlarmPlan.next içinde. */
    fun nextEvent(ctx: Context, after: LocalDateTime): AlarmPlan.Event? {
        val remind = if (Store.notificationsOn(ctx)) Store.reminderMinutes(ctx) else 0
        return AlarmPlan.next(
            Prayers.moments(Times.days(ctx)), remind,
            remindFor = { Store.notifyFor(ctx, it) }, after = after,
        )
    }

    fun schedule(ctx: Context, after: LocalDateTime = LocalDateTime.now()) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val next = nextEvent(app, after)

        val intent = Intent(app, PrayerAlarmReceiver::class.java)
        if (next == null) {
            val old = PendingIntent.getBroadcast(app, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            if (old != null) am.cancel(old)
            return
        }

        val zone = ZoneId.systemDefault()
        val fireMs = next.fireAt.atZone(zone).toInstant().toEpochMilli()
        intent.putExtra(EXTRA_INDEX, next.moment.index)
        intent.putExtra(EXTRA_AT, next.moment.at.atZone(zone).toInstant().toEpochMilli())
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

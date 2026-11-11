package com.aydinmtl.namazvakitleri

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

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

    data class Event(val moment: Moment, val reminder: Boolean, val fireAt: LocalDateTime)

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    /** [after] anından sonraki ilk olay. */
    fun nextEvent(ctx: Context, after: LocalDateTime): Event? {
        val moments = Prayers.moments(Times.days(ctx))
        val remind = Store.reminderMinutes(ctx)
        val events = ArrayList<Event>()
        for (m in moments) {
            events.add(Event(m, false, m.at))
            if (remind > 0 && Store.notificationsOn(ctx) && Store.notifyFor(ctx, m.index)) {
                events.add(Event(m, true, m.at.minusMinutes(remind.toLong())))
            }
        }
        return events
            .filter { it.fireAt.isAfter(after) }
            .minByOrNull { it.fireAt.toEpochSecond(ZoneOffset.UTC) }
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

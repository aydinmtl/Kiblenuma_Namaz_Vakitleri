package com.aydinmtl.namazvakitleri

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Her seferinde yalnızca SIRADAKİ vakit için bir alarm kurar.
 * Alarm çalınca PrayerAlarmReceiver bildirimi gösterir ve bir sonrakini kurar.
 */
object AlarmScheduler {
    const val EXTRA_INDEX = "index"
    const val EXTRA_AT = "at"

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    fun schedule(ctx: Context, after: LocalDateTime = LocalDateTime.now()) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val next = Prayers.next(Store.days(app), after)

        val intent = Intent(app, PrayerAlarmReceiver::class.java)
        if (next == null) {
            val old = PendingIntent.getBroadcast(app, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            if (old != null) am.cancel(old)
            return
        }

        val ms = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        intent.putExtra(EXTRA_INDEX, next.index)
        intent.putExtra(EXTRA_AT, ms)
        val pi = PendingIntent.getBroadcast(app, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        try {
            if (canExact(app)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            } else {
                // İzin yoksa birkaç dakika gecikebilen alarm.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }
}

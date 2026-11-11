package com.aydinmtl.namazvakitleri

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Notifications {
    const val CHANNEL = "vakit"               // vakit girişi (sesli)
    const val CHANNEL_SILENT = "vakit_sessiz" // ezan çalacaksa bildirim sessiz olur
    const val CHANNEL_REMINDER = "hatirlatma" // vakitten önce hatırlatma
    const val CHANNEL_ADHAN = "ezan"          // ezan çalarken görünen "Durdur" bildirimi

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Namaz vakitleri", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Vakit girdiğinde bildirim. Sesi buradan değiştirebilirsin."
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_SILENT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SILENT, "Namaz vakitleri (ezan çalarken)", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Ezan sesi çalarken gösterilen sessiz vakit bildirimi."
                    setSound(null, null)
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_REMINDER) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_REMINDER, "Vakit hatırlatması", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Vakit girmeden önce hatırlatma."
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_ADHAN) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ADHAN, "Ezan sesi", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ezan çalarken durdurma düğmesi."
                    setSound(null, null)
                },
            )
        }
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun showPrayer(ctx: Context, m: Moment, silent: Boolean = false) {
        if (!canPost(ctx)) return
        createChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val place = Store.placeName(ctx)
        val n = Notification.Builder(ctx, if (silent) CHANNEL_SILENT else CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hilal)
            .setContentTitle("${m.name} vakti")
            .setContentText("${m.name} vakti girdi: ${m.at.toLocalTime().format(HHMM)}" + if (place.isNotEmpty()) " · $place" else "")
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setWhen(System.currentTimeMillis())
            .build()
        nm.notify(100 + m.index, n)
    }

    fun showReminder(ctx: Context, m: Moment, minutes: Int) {
        if (!canPost(ctx)) return
        createChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val n = Notification.Builder(ctx, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_hilal)
            .setContentTitle("${m.name} vaktine $minutes dakika kaldı")
            .setContentText("${m.name}: ${m.at.toLocalTime().format(HHMM)}")
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setTimeoutAfter(minutes * 60_000L)
            .build()
        nm.notify(200 + m.index, n)
    }
}

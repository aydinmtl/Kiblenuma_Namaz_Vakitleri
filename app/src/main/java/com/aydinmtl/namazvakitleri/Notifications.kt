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
    const val CHANNEL = "vakit"

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Namaz vakitleri", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Vakit girdiğinde bildirim. Sesi buradan ezan sesiyle değiştirebilirsin."
        nm.createNotificationChannel(ch)
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showPrayer(ctx: Context, m: Moment) {
        if (!canPost(ctx)) return
        createChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val place = Store.placeName(ctx)
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hilal)
            .setContentTitle("${m.name} vakti")
            .setContentText("${m.name} vakti girdi: ${m.at.toLocalTime().format(HHMM)}" + if (place.isNotEmpty()) " · $place" else "")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setWhen(System.currentTimeMillis())
            .build()
        nm.notify(100 + m.index, n)
    }
}

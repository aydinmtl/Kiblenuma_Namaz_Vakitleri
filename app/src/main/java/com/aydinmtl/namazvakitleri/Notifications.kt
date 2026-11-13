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
    const val CHANNEL_DAILY = "gunun_sorusu"  // Aile Halkası: günün sorusu (sessiz)

    /**
     * Bildirim kanallarını kurar. Kanal zaten varsa yeniden "kurmak" yalnızca adını ve
     * açıklamasını günceller (sesi, önemi kullanıcının ayarı olarak kalır). Böylece dil
     * değişince telefonun bildirim ayarlarında kanal adları da yeni dilde görünür.
     */
    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val c = Lang.wrap(ctx)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, c.getString(R.string.ch_prayer), NotificationManager.IMPORTANCE_HIGH).apply {
                description = c.getString(R.string.ch_prayer_desc)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SILENT, c.getString(R.string.ch_silent), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = c.getString(R.string.ch_silent_desc)
                setSound(null, null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, c.getString(R.string.ch_reminder), NotificationManager.IMPORTANCE_HIGH).apply {
                description = c.getString(R.string.ch_reminder_desc)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DAILY, c.getString(R.string.ch_daily), NotificationManager.IMPORTANCE_LOW).apply {
                description = c.getString(R.string.ch_daily_desc)
                setSound(null, null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ADHAN, c.getString(R.string.ch_adhan), NotificationManager.IMPORTANCE_LOW).apply {
                description = c.getString(R.string.ch_adhan_desc)
                setSound(null, null)
            },
        )
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
        val c = Lang.wrap(ctx)
        val name = Lang.prayerName(ctx, m.index)
        val n = Notification.Builder(ctx, if (silent) CHANNEL_SILENT else CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hilal)
            .setContentTitle(c.getString(R.string.notif_prayer_title, name))
            .setContentText(
                c.getString(R.string.notif_prayer_text, name, m.at.toLocalTime().format(HHMM)) +
                    if (place.isNotEmpty()) " · $place" else "",
            )
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
        val name = Lang.prayerName(ctx, m.index)
        val title = Lang.wrap(ctx).resources.getQuantityString(R.plurals.notif_reminder_title, minutes, name, minutes)
        val n = Notification.Builder(ctx, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_hilal)
            .setContentTitle(title)
            .setContentText("$name: ${m.at.toLocalTime().format(HHMM)}")
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setTimeoutAfter(minutes * 60_000L)
            .build()
        nm.notify(200 + m.index, n)
    }

    /** Aile Halkası "günün sorusu" bildirimi; dokununca soru açılır. */
    fun showDailyQuestion(ctx: Context, questionText: String) {
        if (!canPost(ctx)) return
        createChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            ctx, 3,
            Intent(ctx, QuizActivity::class.java)
                .putExtra(QuizActivity.EXTRA_DAILY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_stat_hilal)
            .setContentTitle(Lang.str(ctx, R.string.notif_daily_title))
            .setContentText(questionText)
            .setStyle(Notification.BigTextStyle().bigText(questionText))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(300, n)
    }
}

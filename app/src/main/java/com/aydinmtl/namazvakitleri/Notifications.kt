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

    /** Kaldırılan "günün sorusu" özelliğinin kanalı. Eski kurulumlarda kalmasın diye silinir. */
    private const val REMOVED_CHANNEL_DAILY = "gunun_sorusu"

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
            NotificationChannel(CHANNEL_ADHAN, c.getString(R.string.ch_adhan), NotificationManager.IMPORTANCE_LOW).apply {
                description = c.getString(R.string.ch_adhan_desc)
                setSound(null, null)
            },
        )
        // Eski sürümden kalan kanal (varsa) ve içindeki bildirim silinir; kanal yoksa bir şey olmaz.
        nm.deleteNotificationChannel(REMOVED_CHANNEL_DAILY)
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
        val place = Store.placeLabel(ctx)
        val c = Lang.wrap(ctx)
        val name = Lang.prayerName(ctx, m.index)
        val n = Notification.Builder(ctx, if (silent) CHANNEL_SILENT else CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hilal)
            .setContentTitle(c.getString(R.string.notif_prayer_title, name))
            .setContentText(
                c.getString(R.string.notif_prayer_text, name, m.clock.format(HHMM)) +
                    if (place.isNotEmpty()) " · $place" else "",
            )
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setWhen(System.currentTimeMillis())
            .build()
        nm.notify(NotificationIds.prayer(m.index), n)
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
            .setContentText("$name: ${m.clock.format(HHMM)}")
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setTimeoutAfter(minutes * 60_000L)
            .build()
        nm.notify(NotificationIds.reminder(m.index), n)
    }
}

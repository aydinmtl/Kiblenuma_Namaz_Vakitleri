package com.aydinmtl.namazvakitleri

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDateTime
import java.time.ZoneId

/** Geniş widget (4x2): sıradaki vakit, geri sayım ve o günün 6 vakti. */
class PrayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, build(context, small = false))
        AlarmScheduler.schedule(context)
    }

    companion object {
        private val TIME_IDS = intArrayOf(R.id.w_t0, R.id.w_t1, R.id.w_t2, R.id.w_t3, R.id.w_t4, R.id.w_t5)
        private val NAME_IDS = intArrayOf(R.id.w_n0, R.id.w_n1, R.id.w_n2, R.id.w_n3, R.id.w_n4, R.id.w_n5)
        private val COL_IDS = intArrayOf(R.id.w_c0, R.id.w_c1, R.id.w_c2, R.id.w_c3, R.id.w_c4, R.id.w_c5)

        /** Ekrandaki bütün widget'ları (geniş ve küçük) günceller. */
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val big = mgr.getAppWidgetIds(ComponentName(ctx, PrayerWidget::class.java))
            if (big != null && big.isNotEmpty()) mgr.updateAppWidget(big, build(ctx, small = false))
            val small = mgr.getAppWidgetIds(ComponentName(ctx, PrayerWidgetSmall::class.java))
            if (small != null && small.isNotEmpty()) mgr.updateAppWidget(small, build(ctx, small = true))
        }

        fun build(ctx: Context, small: Boolean): RemoteViews {
            val v = RemoteViews(ctx.packageName, if (small) R.layout.widget_small else R.layout.widget)
            val gold = ctx.getColor(R.color.gold)
            val text = ctx.getColor(R.color.text)
            val muted = ctx.getColor(R.color.muted)

            val days = Times.days(ctx)
            val next = Prayers.next(days, LocalDateTime.now())
            v.setTextViewText(R.id.w_place, Store.placeName(ctx))

            if (next == null) {
                v.setTextViewText(R.id.w_next, "Vakitler için uygulamayı aç")
                v.setViewVisibility(R.id.w_countdown, View.GONE)
            } else {
                v.setTextViewText(R.id.w_next, "${next.name} ${next.at.toLocalTime().format(HHMM)}")
                val targetMs = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val base = SystemClock.elapsedRealtime() + (targetMs - System.currentTimeMillis())
                v.setViewVisibility(R.id.w_countdown, View.VISIBLE)
                // Küçük widget'ta sayaç cümle içinde: "Öğle vaktine kalan: 3:40:51"
                val format = if (small) "${next.name} vaktine kalan: %s" else null
                v.setChronometer(R.id.w_countdown, base, format, true)
                v.setChronometerCountDown(R.id.w_countdown, true)

                // Sıradaki vaktin ait olduğu günü göster (yatsıdan sonra yarının vakitleri).
                val day = Prayers.day(days, next.day)
                for (i in 0..5) {
                    val isNext = i == next.index
                    v.setTextViewText(TIME_IDS[i], day?.times?.get(i)?.format(HHMM) ?: "--:--")
                    // Sıradaki vakit yeşil kutu içinde
                    v.setInt(COL_IDS[i], "setBackgroundResource", if (isNext) R.drawable.row_active else 0)
                    v.setTextColor(TIME_IDS[i], text)
                    v.setTextColor(NAME_IDS[i], if (isNext) text else muted)
                }
            }

            val open = PendingIntent.getActivity(
                ctx, 2, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.w_root, open)

            val refresh = PendingIntent.getActivity(
                ctx, 3, Intent(ctx, RefreshActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.w_refresh, refresh)
            return v
        }
    }
}

/** Küçük widget (4x1): tek satırda sıradaki vakit, geri sayım, yer ve yenile düğmesi. */
class PrayerWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, PrayerWidget.build(context, small = true))
        AlarmScheduler.schedule(context)
    }
}

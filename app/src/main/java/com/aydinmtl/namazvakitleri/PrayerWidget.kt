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

/** Ana ekran widget'ı: sıradaki vakit, geri sayım ve o günün 6 vakti. */
class PrayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, build(context))
        AlarmScheduler.schedule(context)
    }

    companion object {
        private val TIME_IDS = intArrayOf(R.id.w_t0, R.id.w_t1, R.id.w_t2, R.id.w_t3, R.id.w_t4, R.id.w_t5)
        private val NAME_IDS = intArrayOf(R.id.w_n0, R.id.w_n1, R.id.w_n2, R.id.w_n3, R.id.w_n4, R.id.w_n5)

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrayerWidget::class.java))
            if (ids == null || ids.isEmpty()) return
            mgr.updateAppWidget(ids, build(ctx))
        }

        private fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            val gold = ctx.getColor(R.color.gold)
            val text = ctx.getColor(R.color.text)
            val muted = ctx.getColor(R.color.muted)

            val days = Store.days(ctx)
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
                v.setChronometer(R.id.w_countdown, base, null, true)
                v.setChronometerCountDown(R.id.w_countdown, true)

                // Sıradaki vaktin ait olduğu günü göster (yatsıdan sonra yarının vakitleri).
                val day = Prayers.day(days, next.at.toLocalDate())
                for (i in 0..5) {
                    v.setTextViewText(TIME_IDS[i], day?.times?.get(i)?.format(HHMM) ?: "--:--")
                    v.setTextColor(TIME_IDS[i], if (i == next.index) gold else text)
                    v.setTextColor(NAME_IDS[i], if (i == next.index) gold else muted)
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

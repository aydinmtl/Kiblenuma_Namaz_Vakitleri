package com.aydinmtl.namazvakitleri

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Instant

private const val TAG = "Kiblenuma"

/**
 * Bir adımı dener; hata olursa günlüğe yazar ve devam eder. Alıcının adımları birbirinden
 * bağımsızdır: birindeki hata ötekileri (özellikle sıradaki alarmın kurulmasını) engellememeli.
 */
private inline fun attempt(what: String, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "Alarm alıcısı: $what başarısız", e)
    }
}

/** Vakit girdiğinde ya da hatırlatma zamanı geldiğinde AlarmManager tarafından çağrılır. */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val index = intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, -1)
        val atMs = intent.getLongExtra(AlarmScheduler.EXTRA_AT, 0L)
        val fireMs = intent.getLongExtra(AlarmScheduler.EXTRA_FIRE, atMs)
        val reminder = intent.getBooleanExtra(AlarmScheduler.EXTRA_REMINDER, false)
        val fire = Instant.ofEpochMilli(fireMs)
        val now = Instant.now()
        val after = if (fire.isAfter(now)) fire else now

        // 1) ÖNCE sıradaki alarmı kur. Her seferinde yalnızca bir alarm kurulduğu için bu adım
        //    atlanırsa bütün bildirimler durur. Bu yüzden bildirimden, ezandan ve günün
        //    sorusundan önce yapılır; onlardaki bir hata zinciri koparamaz.
        attempt("sıradaki alarm") { AlarmScheduler.schedule(app, after) }

        // 2) Vakit bildirimi / hatırlatma / ezan
        attempt("bildirim") { notifyPrayer(app, intent, index, atMs, fireMs, reminder) }

        // 3) Aile Halkası: akşam vakti girince günün sorusu (açıksa ve bugün henüz cevaplanmadıysa)
        attempt("günün sorusu") { notifyDailyQuestion(app, index, atMs, fireMs, reminder) }

        attempt("widget") { PrayerWidget.updateAll(app) }

        // 4) Vakit verisi azaldıysa yenilemeyi kuyruğa koy. Ağ isteği burada yapılmaz: alıcının
        //    birkaç saniyelik süresi vardır; indirme WorkManager'da, internet varken çalışır.
        attempt("veri yenileme") { if (Store.needsRefresh(app)) DataRefresh.requestNow(app) }
    }

    private fun notifyPrayer(app: Context, intent: Intent, index: Int, atMs: Long, fireMs: Long, reminder: Boolean) {
        // Alarm çok geciktiyse eski olay için uyarı verme (kurallar AlarmPlan.shouldNotify'da).
        val nowMs = System.currentTimeMillis()
        val notify = index in PRAYER_NAMES.indices &&
            AlarmPlan.shouldNotify(reminder, nowMs, fireMs, atMs) &&
            Store.notificationsOn(app) && Store.notifyFor(app, index)
        if (!notify) return

        val m = Moment.fromEpochMillis(
            index, atMs,
            if (intent.hasExtra(AlarmScheduler.EXTRA_CLOCK)) intent.getIntExtra(AlarmScheduler.EXTRA_CLOCK, -1) else null,
            if (intent.hasExtra(AlarmScheduler.EXTRA_DAY)) intent.getLongExtra(AlarmScheduler.EXTRA_DAY, 0L) else null,
        )
        if (reminder) {
            Notifications.showReminder(app, m, Store.reminderMinutes(app))
            return
        }
        val playAdhan = index != SUNRISE && Store.adhanOn(app) && AdhanService.allowedNow(app) &&
            AlarmPlan.adhanInTime(nowMs, atMs) // 15 dakikadan geç ezan çalınmaz
        // Önce ezanı başlatmayı dene. Android izin vermezse (ör. "Alarmlar ve hatırlatıcılar"
        // izni yoksa) bildirim SESLİ gönderilir; yoksa kullanıcı vakti hiç duymaz.
        val adhanStarted = playAdhan && AdhanService.start(app, m)
        Notifications.showPrayer(app, m, silent = adhanStarted)
    }

    private fun notifyDailyQuestion(app: Context, index: Int, atMs: Long, fireMs: Long, reminder: Boolean) {
        if (index != MAGHRIB || reminder || !Store.dailyNotify(app)) return
        if (!AlarmPlan.shouldNotify(false, System.currentTimeMillis(), fireMs, atMs)) return
        val day = Moment.fromEpochMillis(index, atMs).at.toLocalDate()
        if (Store.dailyDoneDate(app) == day.toString()) return
        val id = QuizDeck.dailyId(QuizBank.all(app).map { it.id }, day)
        QuizBank.byId(app, id ?: "")?.let { Notifications.showDailyQuestion(app, it.text) }
    }
}

/** Telefon açılınca, saat/saat dilimi değişince ya da uygulama güncellenince alarmı yeniden kurar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        attempt("sıradaki alarm") { AlarmScheduler.schedule(context) }
        attempt("widget") { PrayerWidget.updateAll(context) }
    }
}

package com.aydinmtl.namazvakitleri

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Vakit alarmından bağımsız, kalıcı arka plan işleri (WorkManager):
 *
 *  - Veri yenileme: Diyanet listesi azalınca, internet varken yeniler. Çevrimdışıyken veri bitse de
 *    internet gelince kendiliğinden çalışır.
 *  - Alarm bekçisi: saatte bir sıradaki alarmı yeniden kurar. Normalde her alarm bir sonrakini kurar;
 *    bu zincir bir nedenle koparsa (beklenmeyen hata, uygulamanın sistemce kapatılması) bildirimler
 *    eskiden uygulama açılana kadar dururdu. Bekçi zinciri en geç bir saat içinde onarır.
 */
object DataRefresh {
    private const val DATA_WORK = "prayer-data-refresh"
    private const val DATA_NOW_WORK = "prayer-data-refresh-now"
    private const val WATCHDOG_WORK = "prayer-alarm-watchdog"

    /** İşler bu süreçte bir kez kuyruğa konur (her alarm kurulduğunda yeniden sormaya gerek yok). */
    @Volatile
    private var ensured = false

    private fun online() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensureScheduled(ctx: Context) {
        if (ensured || Store.districtId(ctx) == null) return
        val wm = WorkManager.getInstance(ctx.applicationContext)
        val data = PeriodicWorkRequest.Builder(PrayerDataWorker::class.java, 6, TimeUnit.HOURS)
            .setConstraints(online())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(DATA_WORK, ExistingPeriodicWorkPolicy.KEEP, data)
        // Bekçi internet istemez: alarm kurmak telefondaki veriyle yapılır.
        val watchdog = PeriodicWorkRequest.Builder(AlarmWatchdogWorker::class.java, 1, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(WATCHDOG_WORK, ExistingPeriodicWorkPolicy.KEEP, watchdog)
        ensured = true
    }

    /**
     * Veri azaldıysa yenilemeyi hemen (internet varsa) kuyruğa koyar. Alarm alıcısı bunu çağırır;
     * ağ isteği alıcının içinde değil, WorkManager'ın kendi iş parçacığında yapılır.
     */
    fun requestNow(ctx: Context) {
        val request = OneTimeWorkRequest.Builder(PrayerDataWorker::class.java)
            .setConstraints(online())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx.applicationContext)
            .enqueueUniqueWork(DATA_NOW_WORK, ExistingWorkPolicy.KEEP, request)
    }
}

class PrayerDataWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        // Ön planın deneme aralığı burada uygulanmaz; ağ gelince yeniden denenmelidir.
        if (!Store.prayerSnapshot(ctx).second.needsData(LocalDate.now())) {
            return Result.success()
        }
        if (!Store.refresh(ctx)) return Result.retry()
        AlarmScheduler.schedule(ctx)
        PrayerWidget.updateAll(ctx)
        return Result.success()
    }
}

/** Saatte bir: sıradaki alarmı ve widget'ı tazeler (bkz. [DataRefresh]). */
class AlarmWatchdogWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        AlarmScheduler.schedule(ctx)
        PrayerWidget.updateAll(ctx)
        return Result.success()
    }
}

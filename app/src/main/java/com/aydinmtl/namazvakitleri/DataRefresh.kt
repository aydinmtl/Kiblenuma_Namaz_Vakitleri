package com.aydinmtl.namazvakitleri

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Sıradaki vakitten bağımsız kalıcı veri işi; çevrimdışıyken veri bitse de yeniden dener. */
object DataRefresh {
    fun ensureScheduled(ctx: Context) {
        if (Store.districtId(ctx) == null) return
        val request = PeriodicWorkRequest.Builder(PrayerDataWorker::class.java, 6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx.applicationContext)
            .enqueueUniquePeriodicWork("prayer-data-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
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

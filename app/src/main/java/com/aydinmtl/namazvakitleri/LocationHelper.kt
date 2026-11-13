package com.aydinmtl.namazvakitleri

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/** Telefonun yaklaşık konumunu tek seferlik alır. */
object LocationHelper {

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationOn(ctx: Context): Boolean {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /**
     * Konumu [callback] ile ana iş parçacığında verir (bulunamazsa null).
     * [maxAgeMs]'den yeni kayıtlı bir konum varsa onu hemen kullanır.
     */
    fun current(
        ctx: Context,
        maxAgeMs: Long = 10 * 60_000L,
        timeoutMs: Long = 20_000L,
        callback: (Location?) -> Unit,
    ) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        if (lm == null || !hasPermission(ctx)) {
            callback(null)
            return
        }
        val last = lastKnown(lm)
        if (last != null && System.currentTimeMillis() - last.time < maxAgeMs) {
            callback(last)
            return
        }
        val provider = when {
            // "fused": Google'ın Wi-Fi + baz istasyonu + GPS birleşimi; genelde en hızlısı (Android 12+).
            Build.VERSION.SDK_INT >= 31 && lm.allProviders.contains(LocationManager.FUSED_PROVIDER) &&
                lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) {
            callback(last)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var done = false
        var listener: LocationListener? = null
        val deliver: (Location?) -> Unit = { l ->
            if (!done) {
                done = true
                listener?.let { lm.removeUpdates(it) }
                callback(l ?: last)
            }
        }
        // Süre dolarsa vazgeç; varsa son bilinen konum kullanılır.
        handler.postDelayed({ deliver(null) }, timeoutMs)

        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, ctx.mainExecutor) { l -> handler.post { deliver(l) } }
            } else {
                // Android 10 ve öncesinde 4 metodun hepsi yazılmalı, yoksa uygulama çöker.
                val l = object : LocationListener {
                    override fun onLocationChanged(location: Location) = deliver(location)

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                listener = l
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, l, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            deliver(null)
        }
    }

    private fun lastKnown(lm: LocationManager): Location? {
        var best: Location? = null
        for (p in lm.getProviders(true)) {
            try {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.time > best.time) best = l
            } catch (e: SecurityException) {
            }
        }
        return best
    }
}

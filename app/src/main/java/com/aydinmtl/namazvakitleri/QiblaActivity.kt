package com.aydinmtl.namazvakitleri

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class QiblaActivity : Activity(), SensorEventListener {

    companion object {
        private const val KAABA_LAT = 21.422487
        private const val KAABA_LON = 39.826206
        private const val REQ_LOCATION = 7

        /** Bulunulan noktadan Kâbe'ye büyük çember yönü (kuzeyden saat yönünde derece). */
        fun qiblaBearing(lat: Double, lon: Double): Double {
            val p1 = Math.toRadians(lat)
            val p2 = Math.toRadians(KAABA_LAT)
            val dl = Math.toRadians(KAABA_LON - lon)
            val y = sin(dl) * cos(p2)
            val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
            return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        }

        /** Haversine formülüyle km cinsinden uzaklık. */
        fun distanceKm(lat: Double, lon: Double): Double {
            val p1 = Math.toRadians(lat)
            val p2 = Math.toRadians(KAABA_LAT)
            val dp = p2 - p1
            val dl = Math.toRadians(KAABA_LON - lon)
            val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 6371.0 * 2 * asin(sqrt(a))
        }
    }

    private lateinit var sm: SensorManager
    private var sensor: Sensor? = null
    private lateinit var qv: QiblaView
    private lateinit var tvInfo: TextView
    private lateinit var tvHint: TextView

    private var declination = 0f
    private var azimuth = 0f
    private var hasAzimuth = false
    private val rot = FloatArray(9)
    private val ori = FloatArray(3)
    private var locationListener: LocationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qibla)
        Ui.fitSystemBars(this, findViewById(R.id.root))
        qv = findViewById(R.id.qibla)
        tvInfo = findViewById(R.id.tvInfo)
        tvHint = findViewById(R.id.tvHint)

        sm = getSystemService(SensorManager::class.java)
        sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (sensor == null) tvHint.text = "Bu telefonda pusula sensörü bulunamadı."
        findViewById<android.view.View>(R.id.btnAr).setOnClickListener {
            startActivity(android.content.Intent(this, QiblaArActivity::class.java))
        }

        // GPS gelene kadar (ya da hiç gelmezse) seçili yerin koordinatını kullan.
        Store.placePoint(this)?.let { p ->
            setLocation(Location("place").apply { latitude = p.first; longitude = p.second })
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locate()
        } else {
            tvInfo.text = "Kıble yönünü hesaplamak için yaklaşık konum izni gerekli."
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_LOCATION) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) locate()
        else tvInfo.text = "Konum izni verilmedi. Kıble yönü için ayarlardan izin verebilirsin."
    }

    override fun onResume() {
        super.onResume()
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sm.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }

    private fun locate() {
        val lm = getSystemService(LocationManager::class.java) ?: return
        var best: Location? = null
        for (p in lm.getProviders(true)) {
            try {
                val l = lm.getLastKnownLocation(p)
                if (l != null && (best == null || l.time > best.time)) best = l
            } catch (e: SecurityException) {
            }
        }
        best?.let { setLocation(it) }

        val stale = best == null || System.currentTimeMillis() - best.time > 60 * 60 * 1000L
        if (!stale) return
        if (best == null) tvInfo.text = "Konum aranıyor… (telefonun konum servisi açık olmalı)"

        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> {
                if (best == null) tvInfo.text = "Konum servisi kapalı. Açıp bu ekrana tekrar gel."
                return
            }
        }

        // Android 10 ve öncesinde bu 4 metodun hepsi zorunlu; yoksa uygulama çöker.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                setLocation(location)
                stopLocationUpdates()
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        locationListener = listener
        try {
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            tvInfo.text = "Konum izni yok."
        }
    }

    private fun stopLocationUpdates() {
        val l = locationListener ?: return
        getSystemService(LocationManager::class.java)?.removeUpdates(l)
        locationListener = null
    }

    private fun setLocation(l: Location) {
        val q = qiblaBearing(l.latitude, l.longitude)
        val km = distanceKm(l.latitude, l.longitude)
        declination = GeomagneticField(
            l.latitude.toFloat(), l.longitude.toFloat(), l.altitude.toFloat(), System.currentTimeMillis(),
        ).declination
        qv.qibla = q.toFloat()
        tvInfo.text = String.format(
            Locale.ROOT, "Kıble açısı: %.0f° (kuzeyden saat yönünde)\nKâbe'ye uzaklık: %,.0f km", q, km,
        ).replace(',', '.')
        qv.invalidate()
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Bazı eski cihazlar 5 değer gönderir; ilk 4'ü yeterli.
        val values = if (event.values.size > 4) event.values.copyOfRange(0, 4) else event.values
        SensorManager.getRotationMatrixFromVector(rot, values)
        SensorManager.getOrientation(rot, ori)
        // Manyetik kuzeyi coğrafi kuzeye çevir (sapma açısı).
        val raw = ((Math.toDegrees(ori[0].toDouble()).toFloat() + declination) + 360f) % 360f

        if (!hasAzimuth) {
            azimuth = raw
            hasAzimuth = true
        } else {
            // Titremeyi azaltmak için yumuşatma; 359°→0° geçişini doğru hesapla.
            val diff = ((raw - azimuth + 540f) % 360f) - 180f
            azimuth = (azimuth + diff * 0.15f + 360f) % 360f
        }
        qv.azimuth = azimuth
        qv.invalidate()

        val off = qv.offset()
        if (off != null && sensor != null) {
            tvHint.text = when {
                abs(off) < 3f -> "✓ Kıble yönündesin"
                off > 0 -> String.format(Locale.ROOT, "Sağa dön: %.0f°", off)
                else -> String.format(Locale.ROOT, "Sola dön: %.0f°", -off)
            }
        }
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {
        if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            tvHint.text = "Pusula hassasiyeti düşük: telefonu havada birkaç kez 8 çizerek kalibre et."
        }
    }
}

package com.aydinmtl.namazvakitleri

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs

/**
 * Kamerayla kıble: telefon dik tutulur, kamera görüntüsünün üstünde Kâbe'nin yönü gösterilir.
 * Kameranın baktığı yön, telefonun yön sensörüyle hesaplanır.
 */
class QiblaArActivity : ComponentActivity(), SensorEventListener {

    private lateinit var preview: PreviewView
    private lateinit var overlay: ArOverlayView
    private lateinit var tvInfo: TextView
    private lateinit var sm: SensorManager
    private var sensor: Sensor? = null

    private var qibla: Float? = null
    private var declination = 0f
    private var azimuth = 0f
    private var hasAzimuth = false
    private val rot = FloatArray(9)
    private val remapped = FloatArray(9)
    private val ori = FloatArray(3)

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else tvInfo.text = "Kamera izni verilmedi. Pusulalı kıble ekranını kullanabilirsin."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qibla_ar)
        preview = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        tvInfo = findViewById(R.id.tvInfo)
        Ui.fitSystemBars(this, findViewById(R.id.root))

        sm = getSystemService(SensorManager::class.java)
        sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (sensor == null) tvInfo.text = "Bu telefonda yön sensörü bulunamadı."

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            tvInfo.text = "Kâbe yönünü kamera görüntüsü üzerinde göstermek için kamera izni gerekli. " +
                "Görüntü kaydedilmez ve hiçbir yere gönderilmez."
            askCamera.launch(Manifest.permission.CAMERA)
        }
        findLocation()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val p = Preview.Builder().build()
                p.setSurfaceProvider(preview.surfaceProvider)
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p)
                overlay.cameraFov = backCameraFov()
            } catch (e: Exception) {
                tvInfo.text = "Kamera açılamadı."
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Arka kameranın tam yatay görüş açısını kameranın kendi bilgilerinden hesaplar
     * (sensör boyu + odak uzaklığı). CameraX de varsayılan olarak ilk arka kamerayı açar.
     * Okunamazsa varsayılan açı kullanılır.
     */
    private fun backCameraFov(): Float = try {
        val cm = getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.first {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        }
        val c = cm.getCameraCharacteristics(id)
        val size = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        // Sensör yatay durur; telefon dik tutulunca ekranın yatayı sensörün KISA kenarıdır.
        if (size == null || focal == null) ArMath.DEFAULT_CAMERA_FOV
        else ArMath.cameraFov(minOf(size.width, size.height), focal)
    } catch (e: Exception) {
        ArMath.DEFAULT_CAMERA_FOV
    }

    private fun findLocation() {
        if (LocationHelper.hasPermission(this)) {
            LocationHelper.current(this, maxAgeMs = 30 * 60_000L, timeoutMs = 8_000L) { loc ->
                if (loc != null) setLocation(loc.latitude, loc.longitude, loc.altitude)
                else usePlacePoint()
            }
        } else {
            usePlacePoint()
        }
    }

    private fun usePlacePoint() {
        val p = Store.placePoint(this)
        if (p != null) setLocation(p.first, p.second, 0.0)
        else tvInfo.text = "Konum bulunamadı. Ayarlar'dan konum iznini ver."
    }

    private fun setLocation(lat: Double, lon: Double, alt: Double) {
        qibla = QiblaActivity.qiblaBearing(lat, lon).toFloat()
        declination = GeomagneticField(lat.toFloat(), lon.toFloat(), alt.toFloat(), System.currentTimeMillis()).declination
    }

    override fun onResume() {
        super.onResume()
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = if (event.values.size > 4) event.values.copyOfRange(0, 4) else event.values
        SensorManager.getRotationMatrixFromVector(rot, values)
        // Telefon dik tutulurken kameranın baktığı yönü bulmak için eksenleri yeniden düzenle.
        SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
        SensorManager.getOrientation(remapped, ori)
        val raw = ((Math.toDegrees(ori[0].toDouble()).toFloat() + declination) + 360f) % 360f

        if (!hasAzimuth) {
            azimuth = raw
            hasAzimuth = true
        } else {
            val diff = ((raw - azimuth + 540f) % 360f) - 180f
            azimuth = (azimuth + diff * 0.12f + 360f) % 360f
        }

        val q = qibla ?: return
        val off = ((q - azimuth + 540f) % 360f) - 180f
        overlay.offset = off
        overlay.invalidate()
        tvInfo.text = when {
            abs(off) < 3f -> "✓ Kâbe tam karşında"
            off > 0 -> String.format(Locale.ROOT, "Kâbe sağında: %.0f°", off)
            else -> String.format(Locale.ROOT, "Kâbe solunda: %.0f°", -off)
        }
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {
        if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            tvInfo.text = "Pusula hassasiyeti düşük: telefonu havada birkaç kez 8 çizerek kalibre et."
        }
    }
}

package com.aydinmtl.namazvakitleri

import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Kamerayla kıble ekranının hesapları. Android'e bağlı değil; bilgisayarda test edilebilir
 * (bkz. ArMathTest).
 *
 * Neden gerekli? Kâbe işaretini ekranda doğru yere koymak için ekranda görünen yatay açıyı
 * bilmek gerekir. Eskiden bu her telefonda 62° varsayılıyordu. Oysa:
 *  1) Her kameranın açısı farklıdır (lens ve sensör boyuna bağlı).
 *  2) Kamera görüntüsü 3:4 oranındadır, telefon ekranı ise çok daha uzundur. Görüntü ekranı
 *     dolduracak kadar büyütülünce sağı ve solu kırpılır; ekranda görünen açı daralır.
 */
object ArMath {

    /** Kamerayı okuyamazsak varsayılan: telefonu dik tutunca kameranın tam yatay açısı. */
    const val DEFAULT_CAMERA_FOV = 60f

    /**
     * Kameranın (telefon dik tutulunca) tam yatay görüş açısı, derece.
     * [sensorShortMm]: sensörün kısa kenarı (mm), [focalMm]: lensin odak uzaklığı (mm).
     */
    fun cameraFov(sensorShortMm: Float, focalMm: Float): Float {
        if (sensorShortMm <= 0f || focalMm <= 0f) return DEFAULT_CAMERA_FOV
        return Math.toDegrees(2.0 * atan(sensorShortMm / (2.0 * focalMm))).toFloat()
    }

    /**
     * Ekranda görünen yatay açı. [fullFov]: kameranın tam yatay açısı, [viewW]x[viewH]:
     * görüntünün gösterildiği alanın boyutu (piksel). Görüntü dikey 3:4 kabul edilir ve
     * alanı tamamen dolduracak şekilde büyütülür (kenarlar kırpılır).
     */
    fun visibleFov(fullFov: Float, viewW: Int, viewH: Int, streamW: Float = 3f, streamH: Float = 4f): Float {
        if (viewW <= 0 || viewH <= 0) return fullFov
        val scale = max(viewW / streamW, viewH / streamH)
        val shown = min(1f, viewW / (streamW * scale)) // görüntünün yatayda ekranda kalan payı
        val half = Math.toRadians(fullFov / 2.0)
        return Math.toDegrees(2.0 * atan(tan(half) * shown)).toFloat()
    }

    /**
     * [offset] derece sağdaki (−: soldaki) bir yönün ekrandaki yatay konumu (piksel).
     * Kamera düz çizgileri koruyan bir mercek gibi davranır: konum açıyla değil açının
     * tanjantıyla orantılıdır. [halfFov]: ekranda görünen açının yarısı.
     */
    fun screenX(offset: Float, halfFov: Float, width: Int): Float {
        val ratio = tan(Math.toRadians(offset.toDouble())) / tan(Math.toRadians(halfFov.toDouble()))
        return (width / 2f) * (1f + ratio.toFloat())
    }
}

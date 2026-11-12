package com.aydinmtl.namazvakitleri

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Harita üzerinde küçük hesaplar: iki nokta arası mesafe ve bir noktadan belli yöne gidince
 * varılan yer. Android'e bağlı değildir; bilgisayarda test edilir (bkz. GeoMathTest).
 */
object GeoMath {
    private const val EARTH_KM = 6371.0

    /** İki nokta arasındaki kuş uçuşu mesafe (km). Noktalar (enlem, boylam) derecedir. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * (lat, lon) noktasından [bearingDeg] yönüne (0 = kuzey, 90 = doğu) [km] gidince varılan nokta.
     * Otomatik konum, çevredeki yerleşimlerin adını öğrenmek için bunu kullanır.
     */
    fun offset(lat: Double, lon: Double, km: Double, bearingDeg: Double): Pair<Double, Double> {
        val d = km / EARTH_KM
        val b = Math.toRadians(bearingDeg)
        val p1 = Math.toRadians(lat)
        val l1 = Math.toRadians(lon)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        var lonDeg = Math.toDegrees(l2)
        // Boylamı -180..180 aralığında tut
        while (lonDeg > 180.0) lonDeg -= 360.0
        while (lonDeg < -180.0) lonDeg += 360.0
        return Pair(Math.toDegrees(p2), lonDeg)
    }
}

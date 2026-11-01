package com.endo.trackr.core.util

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utilitas kalkulasi geospasial dasar menggunakan rumus Haversine.
 */
object LocationUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Menghitung jarak lurus (orthodromic) antara dua titik koordinat GPS dalam meter.
     */
    fun calculateHaversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Memformat angka jarak dalam meter menjadi representasi string yang ramah pengguna
     * (misal: "450 m" atau "3.25 km").
     */
    fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters < 1000.0) {
            String.format(Locale.getDefault(), "%.0f m", distanceMeters)
        } else {
            String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1000.0)
        }
    }
}

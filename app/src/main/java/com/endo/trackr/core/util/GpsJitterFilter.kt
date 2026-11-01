package com.endo.trackr.core.util

import android.location.Location

/**
 * Filter penangkal sinyal GPS liar (drift / jitter) saat berada di dalam ruangan
 * atau saat terjadi loncatan sinyal multipath.
 */
object GpsJitterFilter {

    /**
     * Ambang batas akurasi horizontal maksimum (dalam meter).
     * Jika akurasi > 30 meter (misal 50-80 meter di dalam gedung), titik ditandai sebagai jitter.
     */
    const val MAX_ACCEPTABLE_ACCURACY_METERS = 30.0f

    /**
     * Kecepatan maksimum yang masuk akal untuk perjalanan darat (45 m/s ≈ 162 km/jam).
     * Loncatan koordinat instan yang melampaui batas ini diklasifikasikan sebagai anomali/drift.
     */
    const val MAX_PLAUSIBLE_SPEED_MPS = 45.0f

    /**
     * Mengevaluasi apakah suatu titik [Location] adalah loncatan koordinat (jitter/drift).
     *
     * @param location Titik koordinat baru yang baru saja diterima.
     * @param previousLocation Titik koordinat valid sebelumnya (jika ada).
     * @return true jika titik terindikasi sebagai jitter/drift, false jika valid.
     */
    fun isJitter(location: Location, previousLocation: Location?): Boolean {
        // 1. Validasi Akurasi Horizontal
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            return true
        }

        // 2. Validasi Loncatan Kecepatan (Speed Jump) jika ada titik pembanding
        if (previousLocation != null) {
            val timeDeltaMillis = location.time - previousLocation.time
            val timeDeltaSeconds = (timeDeltaMillis / 1000.0).coerceAtLeast(0.1)

            val distanceMeters = LocationUtils.calculateHaversineDistance(
                lat1 = previousLocation.latitude,
                lon1 = previousLocation.longitude,
                lat2 = location.latitude,
                lon2 = location.longitude
            )

            val impliedSpeedMps = distanceMeters / timeDeltaSeconds

            // Jika kecepatan lompatan > 45 m/s (~162 km/h) dan akurasinya meragukan (> 15m)
            if (impliedSpeedMps > MAX_PLAUSIBLE_SPEED_MPS && location.accuracy > 15.0f) {
                return true
            }
        }

        return false
    }
}

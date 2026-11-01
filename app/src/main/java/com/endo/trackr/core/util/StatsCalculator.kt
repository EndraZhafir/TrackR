package com.endo.trackr.core.util

import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity
import com.endo.trackr.data.local.entity.TrackingSessionEntity
import java.util.Calendar

/**
 * Pilihan rentang waktu untuk agregasi data analitik statistik perjalanan.
 */
enum class StatsTimeRange(val displayName: String) {
    TODAY("Hari Ini"),
    WEEK("7 Hari"),
    MONTH("30 Hari"),
    ALL_TIME("Semua Waktu")
}

/**
 * Ringkasan metrik statistik hasil kalkulasi rumus Haversine dan data sensor GPS.
 */
data class TravelStatsSummary(
    val timeRange: StatsTimeRange = StatsTimeRange.ALL_TIME,
    val totalDistanceMeters: Double = 0.0,
    val totalDurationSeconds: Long = 0L,
    val averageSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val totalPointsCount: Int = 0,
    val stayPointsCount: Int = 0,
    val totalSessionsCount: Int = 0
)

/**
 * Mesin kalkulasi statistik analitik perjalanan pengguna.
 */
object StatsCalculator {

    /**
     * Menghitung nilai ambang batas waktu (epoch millis) berdasarkan rentang waktu yang dipilih.
     */
    fun getStartTimestampForRange(range: StatsTimeRange, now: Long = System.currentTimeMillis()): Long {
        return when (range) {
            StatsTimeRange.TODAY -> {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                calendar.timeInMillis
            }
            StatsTimeRange.WEEK -> now - (7L * 24 * 3600 * 1000L)
            StatsTimeRange.MONTH -> now - (30L * 24 * 3600 * 1000L)
            StatsTimeRange.ALL_TIME -> 0L
        }
    }

    /**
     * Mengagregasi metrik jarak Haversine, durasi, kecepatan, dan titik singgah.
     */
    fun calculate(
        range: StatsTimeRange,
        sessions: List<TrackingSessionEntity>,
        points: List<LocationPointEntity>,
        stayPoints: List<StayPointEntity>,
        now: Long = System.currentTimeMillis()
    ): TravelStatsSummary {
        val startThreshold = getStartTimestampForRange(range, now)

        val filteredSessions = sessions.filter { it.startTime >= startThreshold }
        val filteredPoints = points.filter { it.timestamp >= startThreshold && !it.isJitter }
        val filteredStayPoints = stayPoints.filter { it.arrivalTime >= startThreshold }

        // 1. Hitung Total Jarak (Haversine akumulatif per sesi perjalanan)
        var totalDistanceMeters = 0.0
        val pointsBySession = filteredPoints.groupBy { it.sessionId }
        for ((_, sessionPoints) in pointsBySession) {
            val sorted = sessionPoints.sortedBy { it.timestamp }
            for (i in 0 until sorted.size - 1) {
                totalDistanceMeters += LocationUtils.calculateHaversineDistance(
                    sorted[i].latitude, sorted[i].longitude,
                    sorted[i + 1].latitude, sorted[i + 1].longitude
                )
            }
        }

        // 2. Hitung Total Durasi (detik)
        var totalDurationSeconds = 0L
        for (session in filteredSessions) {
            val sessionDuration = if (session.endTime != null && session.totalDurationSeconds > 0) {
                session.totalDurationSeconds
            } else {
                val sPoints = pointsBySession[session.id]
                if (!sPoints.isNullOrEmpty() && sPoints.size > 1) {
                    (sPoints.last().timestamp - sPoints.first().timestamp) / 1000
                } else {
                    (now - session.startTime) / 1000
                }
            }
            totalDurationSeconds += sessionDuration
        }

        // 3. Hitung Kecepatan Rata-rata (km/jam)
        val averageSpeedKmh = if (totalDurationSeconds > 0 && totalDistanceMeters > 0) {
            val totalDistanceKm = (totalDistanceMeters / 1000.0).toFloat()
            val totalHours = totalDurationSeconds.toFloat() / 3600f
            totalDistanceKm / totalHours
        } else {
            0f
        }

        // 4. Hitung Kecepatan Maksimum (km/jam) - saring anomali > 180 km/jam
        val maxSpeedMps = filteredPoints.map { it.speed }.filter { (it * 3.6f) < 180f }.maxOrNull() ?: 0f
        val maxSpeedKmh = maxSpeedMps * 3.6f

        return TravelStatsSummary(
            timeRange = range,
            totalDistanceMeters = totalDistanceMeters,
            totalDurationSeconds = totalDurationSeconds,
            averageSpeedKmh = averageSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            totalPointsCount = filteredPoints.size,
            stayPointsCount = filteredStayPoints.size,
            totalSessionsCount = filteredSessions.size
        )
    }
}

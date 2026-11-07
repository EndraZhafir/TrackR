package com.endo.trackr.core.util

import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity
import com.endo.trackr.data.local.entity.TrackingSessionEntity
import com.endo.trackr.data.model.FuelProfile
import com.endo.trackr.data.repository.DefaultFuelPreferencesRepository
import java.util.Calendar

/**
 * Pilihan rentang waktu cepat untuk agregasi data analitik statistik perjalanan.
 */
enum class StatsTimeRange(val displayName: String) {
    TODAY("Hari Ini"),
    WEEK("7 Hari"),
    MONTH("30 Hari"),
    ALL_TIME("Semua Waktu")
}

/**
 * Ringkasan metrik statistik hasil kalkulasi rumus Haversine, data sensor GPS,
 * dan estimasi konsumsi serta biaya bahan bakar (BBM).
 */
data class TravelStatsSummary(
    val timeRange: StatsTimeRange = StatsTimeRange.ALL_TIME,
    val dateFilter: HistoryDateFilter = HistoryDateFilter(),
    val totalDistanceMeters: Double = 0.0,
    val totalDurationSeconds: Long = 0L,
    val averageSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val totalPointsCount: Int = 0,
    val stayPointsCount: Int = 0,
    val totalSessionsCount: Int = 0,
    // Estimasi Bahan Bakar & Biaya Perjalanan (Null jika tidak ada profil aktif)
    val idleDurationSeconds: Long = 0L,
    val movingDurationSeconds: Long = 0L,
    val activeFuelProfile: FuelProfile? = null,
    val movingFuelLiters: Double? = null,
    val idleFuelLiters: Double? = null,
    val totalFuelLiters: Double? = null,
    val totalFuelCost: Double? = null
)

/**
 * Mesin kalkulasi statistik analitik perjalanan pengguna dan estimasi konsumsi BBM.
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
     * Mengagregasi metrik analitik perjalanan menggunakan [HistoryDateFilter] yang robust
     * (mendukung tanggal spesifik, rentang tanggal kustom, serta bulan & tahun).
     */
    fun calculate(
        filter: HistoryDateFilter,
        sessions: List<TrackingSessionEntity>,
        points: List<LocationPointEntity>,
        stayPoints: List<StayPointEntity>,
        activeFuelProfile: FuelProfile? = null,
        idleRateLitersPerHour: Double = DefaultFuelPreferencesRepository.DEFAULT_IDLE_RATE_LITERS_PER_HOUR,
        now: Long = System.currentTimeMillis()
    ): TravelStatsSummary {
        val (startThreshold, endThreshold) = filter.getTimeBounds(now)

        val filteredSessions = sessions.filter { it.startTime in startThreshold..endThreshold }
        val filteredPoints = points.filter { it.timestamp in startThreshold..endThreshold && !it.isJitter }
        val filteredStayPoints = stayPoints.filter { it.arrivalTime in startThreshold..endThreshold }

        // 1. Hitung Total Jarak (Haversine akumulatif per sesi perjalanan) & Akumulasi Idle Interval
        var totalDistanceMeters = 0.0
        var totalIdleSeconds = 0L
        val pointsBySession = filteredPoints.groupBy { it.sessionId }

        for ((_, sessionPoints) in pointsBySession) {
            val sorted = sessionPoints.sortedBy { it.timestamp }
            for (i in 0 until sorted.size - 1) {
                val p1 = sorted[i]
                val p2 = sorted[i + 1]
                val dist = LocationUtils.calculateHaversineDistance(
                    p1.latitude, p1.longitude,
                    p2.latitude, p2.longitude
                )
                totalDistanceMeters += dist

                val dt = (p2.timestamp - p1.timestamp) / 1000
                if (dt in 1..7200) {
                    val speedKmh = if (dt > 0) (dist / dt) * 3.6 else 0.0
                    // Kecepatan < 3 km/jam diklasifikasikan sebagai idle (lampu merah, macet, atau diam)
                    if (speedKmh < 3.0 || (p2.speed * 3.6f) < 3.0f) {
                        totalIdleSeconds += dt
                    }
                }
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

        // Pastikan totalIdleSeconds tidak melebihi totalDurationSeconds
        val clampedIdleSeconds = totalIdleSeconds.coerceAtMost(totalDurationSeconds)
        val movingDurationSeconds = (totalDurationSeconds - clampedIdleSeconds).coerceAtLeast(0L)

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

        // 5. Hitung Estimasi Konsumsi dan Biaya BBM (Hanya jika profil aktif ada)
        val (movingFuelLiters, idleFuelLiters, totalFuelLiters, totalFuelCost) = if (activeFuelProfile != null) {
            val totalDistanceKm = totalDistanceMeters / 1000.0
            val movingL = if (activeFuelProfile.efficiencyKmL > 0.0) {
                totalDistanceKm / activeFuelProfile.efficiencyKmL
            } else 0.0

            val idleHours = clampedIdleSeconds.toDouble() / 3600.0
            val idleL = idleHours * (if (idleRateLitersPerHour >= 0.0) idleRateLitersPerHour else 0.0)

            val totalL = movingL + idleL
            val cost = totalL * activeFuelProfile.pricePerLiter
            Quad(movingL, idleL, totalL, cost)
        } else {
            Quad(null, null, null, null)
        }

        val quickRange = when (filter.type) {
            DateFilterType.TODAY -> StatsTimeRange.TODAY
            DateFilterType.WEEK -> StatsTimeRange.WEEK
            DateFilterType.MONTH -> StatsTimeRange.MONTH
            DateFilterType.ALL_TIME -> StatsTimeRange.ALL_TIME
            else -> StatsTimeRange.ALL_TIME
        }

        return TravelStatsSummary(
            timeRange = quickRange,
            dateFilter = filter,
            totalDistanceMeters = totalDistanceMeters,
            totalDurationSeconds = totalDurationSeconds,
            averageSpeedKmh = averageSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            totalPointsCount = filteredPoints.size,
            stayPointsCount = filteredStayPoints.size,
            totalSessionsCount = filteredSessions.size,
            idleDurationSeconds = clampedIdleSeconds,
            movingDurationSeconds = movingDurationSeconds,
            activeFuelProfile = activeFuelProfile,
            movingFuelLiters = movingFuelLiters,
            idleFuelLiters = idleFuelLiters,
            totalFuelLiters = totalFuelLiters,
            totalFuelCost = totalFuelCost
        )
    }

    /**
     * Overload helper untuk kompatibilitas ke belakang dengan [StatsTimeRange].
     */
    fun calculate(
        range: StatsTimeRange,
        sessions: List<TrackingSessionEntity>,
        points: List<LocationPointEntity>,
        stayPoints: List<StayPointEntity>,
        activeFuelProfile: FuelProfile? = null,
        idleRateLitersPerHour: Double = DefaultFuelPreferencesRepository.DEFAULT_IDLE_RATE_LITERS_PER_HOUR,
        now: Long = System.currentTimeMillis()
    ): TravelStatsSummary {
        val filterType = when (range) {
            StatsTimeRange.TODAY -> DateFilterType.TODAY
            StatsTimeRange.WEEK -> DateFilterType.WEEK
            StatsTimeRange.MONTH -> DateFilterType.MONTH
            StatsTimeRange.ALL_TIME -> DateFilterType.ALL_TIME
        }
        return calculate(
            filter = HistoryDateFilter(type = filterType),
            sessions = sessions,
            points = points,
            stayPoints = stayPoints,
            activeFuelProfile = activeFuelProfile,
            idleRateLitersPerHour = idleRateLitersPerHour,
            now = now
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

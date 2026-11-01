package com.endo.trackr.core.util

import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity

/**
 * Representasi segmen kartu dalam antarmuka Timeline harian.
 */
sealed class TimelineSegment {

    /**
     * Segmen singgah di suatu lokasi (Stay-Point cluster).
     */
    data class Stay(
        val stayPoint: StayPointEntity
    ) : TimelineSegment()

    /**
     * Segmen perjalanan bergerak di antara dua titik singgah (Trip segment).
     */
    data class Trip(
        val startTime: Long,
        val endTime: Long,
        val durationSeconds: Long,
        val distanceMeters: Double,
        val startLat: Double,
        val startLng: Double,
        val endLat: Double,
        val endLng: Double,
        val pointCount: Int
    ) : TimelineSegment()
}

/**
 * Mesin ekstraksi titik singgah (Stay-Point Clustering) berbasis algoritma Li et al.
 * Mengelompokkan ratusan titik GPS interval rapat (5 detik) yang berada dalam radius
 * sempit (20–30 meter) selama durasi tertentu menjadi satu titik singgah (Stay Point).
 */
object StayPointDetector {

    const val DEFAULT_DISTANCE_THRESHOLD_METERS = 30.0
    const val DEFAULT_TIME_THRESHOLD_MILLIS = 10 * 60 * 1000L // 10 menit

    /**
     * Mengekstrak daftar [StayPointEntity] dari kumpulan titik koordinat mentah suatu sesi.
     *
     * @param rawPoints Titik koordinat GPS dari sesi terkait.
     * @param distanceThresholdMeters Batas radius maksimum cluster (default 30m).
     * @param timeThresholdMillis Batas waktu minimum berada di dalam radius (default 10 menit).
     */
    fun extractStayPoints(
        rawPoints: List<LocationPointEntity>,
        distanceThresholdMeters: Double = DEFAULT_DISTANCE_THRESHOLD_METERS,
        timeThresholdMillis: Long = DEFAULT_TIME_THRESHOLD_MILLIS
    ): List<StayPointEntity> {
        val points = rawPoints.filter { !it.isJitter }.sortedBy { it.timestamp }
        if (points.size < 2) return emptyList()

        val stayPoints = mutableListOf<StayPointEntity>()
        var i = 0
        val n = points.size

        while (i < n) {
            val pi = points[i]
            var j = i + 1
            var reachedEnd = true

            while (j < n) {
                val pj = points[j]
                val distance = LocationUtils.calculateHaversineDistance(
                    lat1 = pi.latitude,
                    lon1 = pi.longitude,
                    lat2 = pj.latitude,
                    lon2 = pj.longitude
                )

                if (distance > distanceThresholdMeters) {
                    val timeSpan = points[j - 1].timestamp - pi.timestamp
                    if (timeSpan >= timeThresholdMillis) {
                        val cluster = points.subList(i, j)
                        stayPoints.add(buildStayPointEntity(cluster))
                        i = j
                    } else {
                        i++
                    }
                    reachedEnd = false
                    break
                }
                j++
            }

            if (reachedEnd) {
                val timeSpan = points.last().timestamp - pi.timestamp
                if (timeSpan >= timeThresholdMillis) {
                    val cluster = points.subList(i, n)
                    stayPoints.add(buildStayPointEntity(cluster))
                }
                break
            }
        }

        return stayPoints
    }

    /**
     * Membangun daftar terurut [TimelineSegment] (perjalanan dan singgah) dari titik GPS
     * dan titik singgah yang sudah diekstrak.
     */
    fun buildTimelineSegments(
        rawPoints: List<LocationPointEntity>,
        stayPoints: List<StayPointEntity>
    ): List<TimelineSegment> {
        val points = rawPoints.filter { !it.isJitter }.sortedBy { it.timestamp }
        if (points.isEmpty()) return emptyList()

        if (stayPoints.isEmpty()) {
            // Jika tidak ada titik singgah yang cukup lama, seluruh sesi adalah satu Trip tunggal
            return listOf(createTripSegment(points))
        }

        val segments = mutableListOf<TimelineSegment>()
        var currentPointIndex = 0

        for (stay in stayPoints.sortedBy { it.arrivalTime }) {
            // 1. Ambil titik-titik perjalanan sebelum stay point ini
            val tripPointsBefore = mutableListOf<LocationPointEntity>()
            while (currentPointIndex < points.size && points[currentPointIndex].timestamp < stay.arrivalTime) {
                tripPointsBefore.add(points[currentPointIndex])
                currentPointIndex++
            }

            if (tripPointsBefore.size >= 2) {
                segments.add(createTripSegment(tripPointsBefore))
            }

            // 2. Tambahkan segmen Singgah (Stay)
            segments.add(TimelineSegment.Stay(stay))

            // Lewatkan titik-titik yang berada dalam rentang stay point ini
            while (currentPointIndex < points.size && points[currentPointIndex].timestamp <= stay.departureTime) {
                currentPointIndex++
            }
        }

        // 3. Sisa titik setelah stay point terakhir (jika ada)
        if (currentPointIndex < points.size) {
            val remainingTripPoints = points.subList(currentPointIndex, points.size)
            if (remainingTripPoints.size >= 2) {
                segments.add(createTripSegment(remainingTripPoints))
            }
        }

        return segments
    }

    private fun buildStayPointEntity(cluster: List<LocationPointEntity>): StayPointEntity {
        val first = cluster.first()
        val last = cluster.last()
        val avgLat = cluster.map { it.latitude }.average()
        val avgLng = cluster.map { it.longitude }.average()
        val durationSeconds = ((last.timestamp - first.timestamp) / 1000).coerceAtLeast(0)

        return StayPointEntity(
            sessionId = first.sessionId,
            latitude = avgLat,
            longitude = avgLng,
            arrivalTime = first.timestamp,
            departureTime = last.timestamp,
            durationSeconds = durationSeconds,
            pointCount = cluster.size
        )
    }

    private fun createTripSegment(tripPoints: List<LocationPointEntity>): TimelineSegment.Trip {
        val first = tripPoints.first()
        val last = tripPoints.last()
        var totalDist = 0.0

        for (k in 0 until tripPoints.size - 1) {
            totalDist += LocationUtils.calculateHaversineDistance(
                tripPoints[k].latitude,
                tripPoints[k].longitude,
                tripPoints[k + 1].latitude,
                tripPoints[k + 1].longitude
            )
        }

        val duration = ((last.timestamp - first.timestamp) / 1000).coerceAtLeast(0)

        return TimelineSegment.Trip(
            startTime = first.timestamp,
            endTime = last.timestamp,
            durationSeconds = duration,
            distanceMeters = totalDist,
            startLat = first.latitude,
            startLng = first.longitude,
            endLat = last.latitude,
            endLng = last.longitude,
            pointCount = tripPoints.size
        )
    }
}

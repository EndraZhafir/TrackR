package com.endo.trackr.data.repository

import android.util.Log
import com.endo.trackr.core.util.LocationUtils
import com.endo.trackr.core.util.StayPointDetector
import com.endo.trackr.data.local.dao.LocationPointDao
import com.endo.trackr.data.local.dao.StayPointDao
import com.endo.trackr.data.local.dao.TrackingSessionDao
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity
import com.endo.trackr.data.local.entity.TrackingSessionEntity
import com.endo.trackr.data.local.entity.TrackingStatus
import kotlinx.coroutines.flow.Flow

/**
 * Implementasi offline-first dari [LocationRepository] menggunakan Room SQLite DAOs.
 */
class OfflineLocationRepository(
    private val sessionDao: TrackingSessionDao,
    private val pointDao: LocationPointDao,
    private val stayPointDao: StayPointDao
) : LocationRepository {

    override fun getAllSessions(): Flow<List<TrackingSessionEntity>> =
        sessionDao.getAllSessions()

    override fun getSessionById(sessionId: Long): Flow<TrackingSessionEntity?> =
        sessionDao.getSessionById(sessionId)

    override suspend fun getSessionByIdSync(sessionId: Long): TrackingSessionEntity? =
        sessionDao.getSessionByIdSync(sessionId)

    override fun getActiveSession(): Flow<TrackingSessionEntity?> =
        sessionDao.getActiveSession()

    override suspend fun getActiveSessionSync(): TrackingSessionEntity? =
        sessionDao.getActiveSessionSync()

    override suspend fun startNewSession(title: String?): Long {
        val now = System.currentTimeMillis()
        val sessionName = title ?: "Jejak Perjalanan"
        val newSession = TrackingSessionEntity(
            title = sessionName,
            startTime = now,
            status = TrackingStatus.ACTIVE.name
        )
        return sessionDao.insertSession(newSession)
    }

    override suspend fun updateSession(session: TrackingSessionEntity) {
        sessionDao.updateSession(session)
    }

    override suspend fun pauseSession(sessionId: Long) {
        val session = sessionDao.getSessionByIdSync(sessionId) ?: return
        sessionDao.updateSession(session.copy(status = TrackingStatus.PAUSED.name))
    }

    override suspend fun resumeSession(sessionId: Long) {
        val session = sessionDao.getSessionByIdSync(sessionId) ?: return
        sessionDao.updateSession(session.copy(status = TrackingStatus.ACTIVE.name))
    }

    override suspend fun completeSession(sessionId: Long) {
        val session = sessionDao.getSessionByIdSync(sessionId) ?: return
        val now = System.currentTimeMillis()
        val duration = ((now - session.startTime) / 1000).coerceAtLeast(0)

        // Hitung ulang akumulasi jarak dan jumlah titik secara pasti dari Room
        val points = pointDao.getPointsForSessionSync(sessionId)
        var totalDist = 0.0
        val validPoints = points.filter { !it.isJitter }
        for (i in 0 until validPoints.size - 1) {
            totalDist += LocationUtils.calculateHaversineDistance(
                validPoints[i].latitude, validPoints[i].longitude,
                validPoints[i + 1].latitude, validPoints[i + 1].longitude
            )
        }

        sessionDao.updateSession(
            session.copy(
                endTime = now,
                totalDurationSeconds = duration,
                pointCount = points.size,
                totalDistanceMeters = if (totalDist > 0) totalDist else session.totalDistanceMeters,
                status = TrackingStatus.COMPLETED.name
            )
        )

        // Otomatis ekstrak dan simpan stay points untuk sesi yang baru selesai
        try {
            recalculateStayPoints(sessionId)
        } catch (e: Exception) {
            Log.e("OfflineLocationRepo", "Gagal mengekstrak stay points saat completeSession ($sessionId)", e)
        }
    }

    override suspend fun deleteSession(sessionId: Long) {
        pointDao.deletePointsForSession(sessionId)
        stayPointDao.deleteStayPointsForSession(sessionId)
        sessionDao.deleteSessionById(sessionId)
    }

    override suspend fun insertLocationPoint(point: LocationPointEntity): Long {
        return pointDao.insertPoint(point)
    }

    override suspend fun insertLocationPoints(points: List<LocationPointEntity>) {
        pointDao.insertPoints(points)
    }

    override fun getPointsForSession(sessionId: Long): Flow<List<LocationPointEntity>> =
        pointDao.getPointsForSession(sessionId)

    override suspend fun getPointsForSessionSync(sessionId: Long): List<LocationPointEntity> =
        pointDao.getPointsForSessionSync(sessionId)

    override fun getValidPointsForSession(sessionId: Long): Flow<List<LocationPointEntity>> =
        pointDao.getValidPointsForSession(sessionId)

    override fun getAllValidPoints(): Flow<List<LocationPointEntity>> =
        pointDao.getAllValidPoints()

    override fun getLatestPoint(sessionId: Long): Flow<LocationPointEntity?> =
        pointDao.getLatestPointForSession(sessionId)

    override suspend fun getLatestPointSync(sessionId: Long): LocationPointEntity? =
        pointDao.getLatestPointForSessionSync(sessionId)

    override fun getPointCountForSession(sessionId: Long): Flow<Int> =
        pointDao.getPointCountForSession(sessionId)

    override fun getStayPointsForSession(sessionId: Long): Flow<List<StayPointEntity>> =
        stayPointDao.getStayPointsForSession(sessionId)

    override suspend fun getStayPointsForSessionSync(sessionId: Long): List<StayPointEntity> =
        stayPointDao.getStayPointsForSessionSync(sessionId)

    override fun getAllStayPoints(): Flow<List<StayPointEntity>> =
        stayPointDao.getAllStayPoints()

    override suspend fun insertStayPoints(stayPoints: List<StayPointEntity>) {
        stayPointDao.insertStayPoints(stayPoints)
    }

    override suspend fun updateStayPointAddress(stayPointId: Long, address: String) {
        stayPointDao.updateResolvedAddress(stayPointId, address)
    }

    override suspend fun recalculateStayPoints(
        sessionId: Long,
        timeThresholdMillis: Long
    ): List<StayPointEntity> {
        val rawPoints = pointDao.getPointsForSessionSync(sessionId)
        val extracted = StayPointDetector.extractStayPoints(
            rawPoints = rawPoints,
            timeThresholdMillis = timeThresholdMillis
        )
        stayPointDao.deleteStayPointsForSession(sessionId)
        if (extracted.isNotEmpty()) {
            stayPointDao.insertStayPoints(extracted)
        }
        return extracted
    }
}

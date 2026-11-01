package com.endo.trackr.data.repository

import com.endo.trackr.data.local.dao.LocationPointDao
import com.endo.trackr.data.local.dao.TrackingSessionDao
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.TrackingSessionEntity
import com.endo.trackr.data.local.entity.TrackingStatus
import kotlinx.coroutines.flow.Flow

/**
 * Implementasi offline-first dari [LocationRepository] yang langsung berkomunikasi
 * dengan basis data lokal Room via [TrackingSessionDao] dan [LocationPointDao].
 */
class OfflineLocationRepository(
    private val sessionDao: TrackingSessionDao,
    private val pointDao: LocationPointDao
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
        val newSession = TrackingSessionEntity(
            startTime = System.currentTimeMillis(),
            title = title,
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
        val durationSeconds = ((now - session.startTime) / 1000).coerceAtLeast(0)
        sessionDao.updateSession(
            session.copy(
                endTime = now,
                totalDurationSeconds = durationSeconds,
                status = TrackingStatus.COMPLETED.name
            )
        )
    }

    override suspend fun deleteSession(sessionId: Long) {
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

    override fun getLatestPoint(sessionId: Long): Flow<LocationPointEntity?> =
        pointDao.getLatestPointForSession(sessionId)

    override suspend fun getLatestPointSync(sessionId: Long): LocationPointEntity? =
        pointDao.getLatestPointForSessionSync(sessionId)

    override fun getPointCountForSession(sessionId: Long): Flow<Int> =
        pointDao.getPointCountForSession(sessionId)
}

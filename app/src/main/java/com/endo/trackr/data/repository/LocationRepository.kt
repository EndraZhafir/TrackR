package com.endo.trackr.data.repository

import com.endo.trackr.core.util.StayPointDetector
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity
import com.endo.trackr.data.local.entity.TrackingSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Interface Repository untuk mengabstraksi sumber data lokasi, sesi pelacakan,
 * dan titik singgah (Stay Points).
 */
interface LocationRepository {

    // --- Manajemen Sesi Pelacakan ---
    fun getAllSessions(): Flow<List<TrackingSessionEntity>>
    fun getSessionById(sessionId: Long): Flow<TrackingSessionEntity?>
    suspend fun getSessionByIdSync(sessionId: Long): TrackingSessionEntity?
    fun getActiveSession(): Flow<TrackingSessionEntity?>
    suspend fun getActiveSessionSync(): TrackingSessionEntity?
    suspend fun startNewSession(title: String? = null): Long
    suspend fun updateSession(session: TrackingSessionEntity)
    suspend fun pauseSession(sessionId: Long)
    suspend fun resumeSession(sessionId: Long)
    suspend fun completeSession(sessionId: Long)
    suspend fun deleteSession(sessionId: Long)

    // --- Manajemen Titik Koordinat GPS ---
    suspend fun insertLocationPoint(point: LocationPointEntity): Long
    suspend fun insertLocationPoints(points: List<LocationPointEntity>)
    fun getPointsForSession(sessionId: Long): Flow<List<LocationPointEntity>>
    suspend fun getPointsForSessionSync(sessionId: Long): List<LocationPointEntity>
    fun getValidPointsForSession(sessionId: Long): Flow<List<LocationPointEntity>>
    fun getLatestPoint(sessionId: Long): Flow<LocationPointEntity?>
    suspend fun getLatestPointSync(sessionId: Long): LocationPointEntity?
    fun getPointCountForSession(sessionId: Long): Flow<Int>

    // --- Manajemen Titik Singgah (Stay-Point Clustering) ---
    fun getStayPointsForSession(sessionId: Long): Flow<List<StayPointEntity>>
    suspend fun getStayPointsForSessionSync(sessionId: Long): List<StayPointEntity>
    suspend fun insertStayPoints(stayPoints: List<StayPointEntity>)
    suspend fun recalculateStayPoints(
        sessionId: Long,
        timeThresholdMillis: Long = StayPointDetector.DEFAULT_TIME_THRESHOLD_MILLIS
    ): List<StayPointEntity>
}

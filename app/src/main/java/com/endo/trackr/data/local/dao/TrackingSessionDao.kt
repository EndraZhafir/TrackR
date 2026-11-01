package com.endo.trackr.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.endo.trackr.data.local.entity.TrackingSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) untuk mengelola entitas [TrackingSessionEntity].
 */
@Dao
interface TrackingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TrackingSessionEntity): Long

    @Update
    suspend fun updateSession(session: TrackingSessionEntity)

    @Delete
    suspend fun deleteSession(session: TrackingSessionEntity)

    @Query("SELECT * FROM tracking_sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionById(sessionId: Long): Flow<TrackingSessionEntity?>

    @Query("SELECT * FROM tracking_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionByIdSync(sessionId: Long): TrackingSessionEntity?

    @Query("SELECT * FROM tracking_sessions WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY startTime DESC LIMIT 1")
    fun getActiveSession(): Flow<TrackingSessionEntity?>

    @Query("SELECT * FROM tracking_sessions WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSessionSync(): TrackingSessionEntity?

    @Query("SELECT * FROM tracking_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<TrackingSessionEntity>>

    @Query("DELETE FROM tracking_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
}

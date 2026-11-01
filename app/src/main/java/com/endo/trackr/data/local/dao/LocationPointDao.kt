package com.endo.trackr.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.endo.trackr.data.local.entity.LocationPointEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) untuk mengelola entitas [LocationPointEntity].
 */
@Dao
interface LocationPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: LocationPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<LocationPointEntity>)

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getPointsForSession(sessionId: Long): Flow<List<LocationPointEntity>>

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getPointsForSessionSync(sessionId: Long): List<LocationPointEntity>

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId AND isJitter = 0 ORDER BY timestamp ASC")
    fun getValidPointsForSession(sessionId: Long): Flow<List<LocationPointEntity>>

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestPointForSession(sessionId: Long): Flow<LocationPointEntity?>

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPointForSessionSync(sessionId: Long): LocationPointEntity?

    @Query("SELECT COUNT(*) FROM location_points WHERE sessionId = :sessionId")
    fun getPointCountForSession(sessionId: Long): Flow<Int>

    @Query("DELETE FROM location_points WHERE sessionId = :sessionId")
    suspend fun deletePointsForSession(sessionId: Long)
}

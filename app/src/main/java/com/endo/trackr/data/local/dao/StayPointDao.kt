package com.endo.trackr.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.endo.trackr.data.local.entity.StayPointEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) untuk mengelola entitas titik singgah [StayPointEntity].
 */
@Dao
interface StayPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStayPoint(stayPoint: StayPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStayPoints(stayPoints: List<StayPointEntity>)

    @Query("SELECT * FROM stay_points WHERE sessionId = :sessionId ORDER BY arrivalTime ASC")
    fun getStayPointsForSession(sessionId: Long): Flow<List<StayPointEntity>>

    @Query("SELECT * FROM stay_points WHERE sessionId = :sessionId ORDER BY arrivalTime ASC")
    suspend fun getStayPointsForSessionSync(sessionId: Long): List<StayPointEntity>

    @Query("SELECT * FROM stay_points ORDER BY arrivalTime DESC")
    fun getAllStayPoints(): Flow<List<StayPointEntity>>

    @Query("DELETE FROM stay_points WHERE sessionId = :sessionId")
    suspend fun deleteStayPointsForSession(sessionId: Long)

    @Query("UPDATE stay_points SET resolvedAddress = :address WHERE id = :stayPointId")
    suspend fun updateResolvedAddress(stayPointId: Long, address: String)
}

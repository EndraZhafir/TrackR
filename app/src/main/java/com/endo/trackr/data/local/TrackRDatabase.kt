package com.endo.trackr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.endo.trackr.data.local.dao.LocationPointDao
import com.endo.trackr.data.local.dao.TrackingSessionDao
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.TrackingSessionEntity

/**
 * Basis data Room utama untuk TrackR yang mengelola tabel [TrackingSessionEntity]
 * dan [LocationPointEntity].
 */
@Database(
    entities = [
        TrackingSessionEntity::class,
        LocationPointEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TrackRDatabase : RoomDatabase() {

    abstract fun trackingSessionDao(): TrackingSessionDao
    abstract fun locationPointDao(): LocationPointDao

    companion object {
        @Volatile
        private var Instance: TrackRDatabase? = null

        fun getDatabase(context: Context): TrackRDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    TrackRDatabase::class.java,
                    "trackr_database.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

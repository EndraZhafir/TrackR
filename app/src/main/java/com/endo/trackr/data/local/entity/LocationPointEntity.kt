package com.endo.trackr.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity Room yang merepresentasikan satu titik koordinat GPS dari [TrackingSessionEntity].
 *
 * @property id Primary key autoincrement untuk titik lokasi.
 * @property sessionId ID sesi perjalanan terkait (Foreign Key ke tracking_sessions).
 * @property latitude Garis lintang (derajat desimal).
 * @property longitude Garis bujur (derajat desimal).
 * @property altitude Ketinggian di atas permukaan laut (meter).
 * @property accuracy Estimasi radius akurasi horizontal 68% (meter).
 * @property speed Kecepatan gerak perangkat (meter/detik).
 * @property timestamp Waktu pengambilan koordinat (epoch millis).
 * @property isJitter Flag penanda jika titik dideteksi sebagai GPS drift/loncatan sinyal yang tidak wajar.
 */
@Entity(
    tableName = "location_points",
    foreignKeys = [
        ForeignKey(
            entity = TrackingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["timestamp"])
    ]
)
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val isJitter: Boolean = false
)

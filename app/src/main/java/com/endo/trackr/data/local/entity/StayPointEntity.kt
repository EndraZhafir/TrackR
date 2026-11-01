package com.endo.trackr.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity Room yang merepresentasikan titik singgah (Stay-Point) hasil pengelompokan (clustering)
 * dari puluhan hingga ratusan titik koordinat GPS yang berada dalam radius sempit (20–30 meter)
 * selama durasi tertentu (misal: >= 15-30 menit).
 *
 * @property id Primary key autoincrement untuk stay-point.
 * @property sessionId ID sesi perjalanan terkait (Foreign Key ke tracking_sessions).
 * @property latitude Rata-rata garis lintang (centroid) dari kelompok titik singgah.
 * @property longitude Rata-rata garis bujur (centroid) dari kelompok titik singgah.
 * @property arrivalTime Waktu kedatangan / titik pertama cluster (epoch millis).
 * @property departureTime Waktu keberangkatan / titik terakhir cluster (epoch millis).
 * @property durationSeconds Total durasi singgah dalam detik (departureTime - arrivalTime).
 * @property pointCount Jumlah titik GPS mentah yang tergabung ke dalam cluster ini.
 * @property resolvedAddress Nama tempat/jalan yang diterjemahkan via Geocoder (Modul 6).
 */
@Entity(
    tableName = "stay_points",
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
        Index(value = ["arrivalTime"])
    ]
)
data class StayPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val arrivalTime: Long,
    val departureTime: Long,
    val durationSeconds: Long,
    val pointCount: Int,
    val resolvedAddress: String? = null
)

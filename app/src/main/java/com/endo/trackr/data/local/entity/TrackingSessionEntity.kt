package com.endo.trackr.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Status dari sesi perekaman jejak GPS.
 */
enum class TrackingStatus {
    ACTIVE,
    PAUSED,
    COMPLETED
}

/**
 * Entity Room yang merepresentasikan satu sesi perekaman jejak rute.
 *
 * @property id Primary key autoincrement untuk sesi.
 * @property startTime Waktu mulai perekaman (epoch millis).
 * @property endTime Waktu berakhir perekaman (epoch millis), null jika sesi masih aktif.
 * @property title Judul opsional untuk sesi perjalanan (misal: "Gowes Pagi", "Perjalanan Kerja").
 * @property totalDistanceMeters Total akumulasi jarak tempuh dalam meter.
 * @property totalDurationSeconds Total durasi aktif perekaman dalam detik.
 * @property pointCount Jumlah titik koordinat yang berhasil direkam pada sesi ini.
 * @property status Status sesi saat ini ([TrackingStatus.ACTIVE], [TrackingStatus.PAUSED], [TrackingStatus.COMPLETED]).
 */
@Entity(tableName = "tracking_sessions")
data class TrackingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val title: String? = null,
    val totalDistanceMeters: Double = 0.0,
    val totalDurationSeconds: Long = 0L,
    val pointCount: Int = 0,
    val status: String = TrackingStatus.ACTIVE.name
)

package com.endo.trackr.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utility helper untuk memformat timestamp (epoch millis) dan durasi waktu.
 */
object DateTimeUtils {

    private val fullDateTimeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private val timeOnlyFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

    fun formatFullDateTime(epochMillis: Long): String {
        return fullDateTimeFormat.format(Date(epochMillis))
    }

    fun formatTimeOnly(epochMillis: Long): String {
        return timeOnlyFormat.format(Date(epochMillis))
    }

    fun formatDateOnly(epochMillis: Long): String {
        return dateOnlyFormat.format(Date(epochMillis))
    }

    /**
     * Memformat timestamp menjadi format ISO 8601 UTC standar (misal: "2026-11-01T11:00:00Z").
     * Wajib untuk format GIS seperti GPX, KML, dan GeoJSON.
     */
    fun formatIso8601Utc(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(epochMillis))
    }

    /**
     * Memformat timestamp untuk penamaan file ekspor yang bersih tanpa karakter terlarang (misal: "20261101_180000").
     */
    fun formatFilenameTimestamp(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return sdf.format(Date(epochMillis))
    }

    /**
     * Memformat durasi dalam detik menjadi format "HH:mm:ss" atau "mm:ss".
     */
    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}

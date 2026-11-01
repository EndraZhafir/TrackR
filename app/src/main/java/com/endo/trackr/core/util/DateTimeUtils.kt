package com.endo.trackr.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

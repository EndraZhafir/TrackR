package com.endo.trackr.core.util

import android.content.Context
import com.endo.trackr.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utility helper untuk memformat timestamp (epoch millis), durasi waktu, dan evaluasi kalender.
 */
object DateTimeUtils {

    fun formatFullDateTime(epochMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }

    fun formatTimeOnly(epochMillis: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }

    fun formatDateOnly(epochMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        return sdf.format(Date(epochMillis))
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

    /**
     * Memeriksa apakah dua epoch timestamp berada pada tanggal kalender yang sama (berdasarkan timezone lokal perangkat).
     */
    fun isSameDay(timeA: Long, timeB: Long): Boolean {
        val calA = Calendar.getInstance().apply { timeInMillis = timeA }
        val calB = Calendar.getInstance().apply { timeInMillis = timeB }
        return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
                calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Mengelompokkan timestamp ke dalam kategori tanggal yang ramah pengguna dan terlokalisasi:
     * - "Hari Ini" / "Today"
     * - "Kemarin" / "Yesterday"
     * - "Minggu Ini" / "This Week"
     * - "Bulan Ini" / "This Month"
     * - Format bulan & tahun (misal: "Oktober 2026") untuk yang lebih lama.
     */
    fun getDateCategory(epochMillis: Long, context: Context? = null): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = epochMillis }

        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        ) {
            return context?.getString(R.string.date_today) ?: "Hari Ini"
        }

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        ) {
            return context?.getString(R.string.date_yesterday) ?: "Kemarin"
        }

        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.WEEK_OF_YEAR) == target.get(Calendar.WEEK_OF_YEAR)
        ) {
            return context?.getString(R.string.date_this_week) ?: "Minggu Ini"
        }

        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.MONTH) == target.get(Calendar.MONTH)
        ) {
            return context?.getString(R.string.date_this_month) ?: "Bulan Ini"
        }

        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return monthYearFormat.format(Date(epochMillis))
    }
}

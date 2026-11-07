package com.endo.trackr.core.util

import android.content.Context
import com.endo.trackr.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tipe filter waktu untuk analitik riwayat dan statistik perjalanan.
 */
enum class DateFilterType {
    TODAY,
    WEEK,
    MONTH,
    ALL_TIME,
    SPECIFIC_DATE,
    DATE_RANGE,
    MONTH_YEAR
}

/**
 * Model data status filter riwayat yang fleksibel dan lengkap.
 * Mendukung filter cepat (Hari Ini, 7 Hari, 30 Hari, Semua Waktu),
 * tanggal tertentu (Single Date), rentang tanggal (Date Range),
 * serta bulan & tahun spesifik (Month & Year).
 */
data class HistoryDateFilter(
    val type: DateFilterType = DateFilterType.ALL_TIME,
    val specificDateMillis: Long? = null,
    val rangeStartMillis: Long? = null,
    val rangeEndMillis: Long? = null,
    val selectedMonth: Int? = null, // 0 = Jan, 11 = Des
    val selectedYear: Int? = null
) {
    /**
     * Menghitung batas awal dan batas akhir epoch timestamp dalam milidetik (startMillis, endMillis).
     */
    fun getTimeBounds(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        return when (type) {
            DateFilterType.TODAY -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            DateFilterType.WEEK -> {
                val end = now
                val start = now - (7L * 24 * 3600 * 1000L)
                Pair(start, end)
            }
            DateFilterType.MONTH -> {
                val end = now
                val start = now - (30L * 24 * 3600 * 1000L)
                Pair(start, end)
            }
            DateFilterType.ALL_TIME -> {
                Pair(0L, Long.MAX_VALUE)
            }
            DateFilterType.SPECIFIC_DATE -> {
                val targetMillis = specificDateMillis ?: now
                calendar.timeInMillis = targetMillis
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            DateFilterType.DATE_RANGE -> {
                val startMillis = rangeStartMillis ?: now
                val endMillis = rangeEndMillis ?: now
                calendar.timeInMillis = minOf(startMillis, endMillis)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                calendar.timeInMillis = maxOf(startMillis, endMillis)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            DateFilterType.MONTH_YEAR -> {
                val m = selectedMonth ?: calendar.get(Calendar.MONTH)
                val y = selectedYear ?: calendar.get(Calendar.YEAR)

                calendar.clear()
                calendar.set(Calendar.YEAR, y)
                calendar.set(Calendar.MONTH, m)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val start = calendar.timeInMillis

                val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                calendar.set(Calendar.DAY_OF_MONTH, maxDay)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
        }
    }

    /**
     * Menghasilkan teks label yang ramah pengguna dan terjemahan otomatis.
     */
    fun getDisplayLabel(context: Context): String {
        return when (type) {
            DateFilterType.TODAY -> context.getString(R.string.stats_range_today)
            DateFilterType.WEEK -> context.getString(R.string.stats_range_week)
            DateFilterType.MONTH -> context.getString(R.string.stats_range_month)
            DateFilterType.ALL_TIME -> context.getString(R.string.stats_range_all_time)
            DateFilterType.SPECIFIC_DATE -> {
                val date = specificDateMillis?.let { DateTimeUtils.formatDateOnly(it) } ?: ""
                "📅 $date"
            }
            DateFilterType.DATE_RANGE -> {
                val startSdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                val endSdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val start = rangeStartMillis?.let { startSdf.format(Date(it)) } ?: ""
                val end = rangeEndMillis?.let { endSdf.format(Date(it)) } ?: ""
                "📅 $start - $end"
            }
            DateFilterType.MONTH_YEAR -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.YEAR, selectedYear ?: cal.get(Calendar.YEAR))
                cal.set(Calendar.MONTH, selectedMonth ?: cal.get(Calendar.MONTH))
                val label = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
                "📅 $label"
            }
        }
    }

    /**
     * Menentukan apakah filter saat ini merupakan filter kustom (bukan bawaan 4 filter cepat).
     */
    val isCustom: Boolean
        get() = type in setOf(DateFilterType.SPECIFIC_DATE, DateFilterType.DATE_RANGE, DateFilterType.MONTH_YEAR)
}

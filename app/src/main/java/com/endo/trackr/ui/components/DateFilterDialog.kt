package com.endo.trackr.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.endo.trackr.R
import com.endo.trackr.core.util.DateFilterType
import com.endo.trackr.core.util.HistoryDateFilter
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Dialog interaktif untuk memilih filter tanggal kustom:
 * 1. Tanggal Spesifik (Single Date via DatePicker)
 * 2. Rentang Tanggal (Date Range berbasis kalender bulanan dengan panah navigasi bulan tanpa stutter)
 * 3. Bulan & Tahun (Mulai dari 2026 dan bertambah dinamis di tahun-tahun mendatang)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFilterDialog(
    isVisible: Boolean,
    currentFilter: HistoryDateFilter,
    onFilterSelected: (HistoryDateFilter) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    var selectedTabIndex by remember {
        mutableIntStateOf(
            when (currentFilter.type) {
                DateFilterType.DATE_RANGE -> 1
                DateFilterType.MONTH_YEAR -> 2
                else -> 0
            }
        )
    }

    val currentCalendar = remember { Calendar.getInstance() }
    val currentYear = currentCalendar.get(Calendar.YEAR)
    val appStartYear = 2026
    val availableYears = remember(currentYear) {
        (appStartYear..maxOf(appStartYear, currentYear)).toList()
    }

    // State untuk Tab 0: Tanggal Spesifik (Mulai dari 2026)
    val initialDateMillis = currentFilter.specificDateMillis ?: System.currentTimeMillis()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis,
        yearRange = appStartYear..maxOf(appStartYear, currentYear)
    )

    // State untuk Tab 1: Rentang Tanggal (Kalender Bulanan Paged dengan Panah)
    var rangeStartMillis by remember { mutableStateOf(currentFilter.rangeStartMillis) }
    var rangeEndMillis by remember { mutableStateOf(currentFilter.rangeEndMillis) }

    val initialDisplayCal = remember {
        Calendar.getInstance().apply {
            timeInMillis = currentFilter.rangeStartMillis ?: System.currentTimeMillis()
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    var rangeDisplayedYear by remember {
        mutableIntStateOf(maxOf(appStartYear, initialDisplayCal.get(Calendar.YEAR)))
    }
    var rangeDisplayedMonth by remember {
        mutableIntStateOf(initialDisplayCal.get(Calendar.MONTH))
    }

    // State untuk Tab 2: Bulan & Tahun (Pilihan tahun mulai dari 2026 saja)
    var selectedYear by remember {
        mutableIntStateOf(currentFilter.selectedYear ?: maxOf(appStartYear, currentYear))
    }
    var selectedMonth by remember {
        mutableIntStateOf(currentFilter.selectedMonth ?: currentCalendar.get(Calendar.MONTH))
    }

    val monthNames = remember {
        DateFormatSymbols(Locale.getDefault()).months.take(12)
    }

    val shortWeekdays = remember {
        val symbols = DateFormatSymbols(Locale.getDefault()).shortWeekdays
        listOf(
            symbols[Calendar.SUNDAY],
            symbols[Calendar.MONDAY],
            symbols[Calendar.TUESDAY],
            symbols[Calendar.WEDNESDAY],
            symbols[Calendar.THURSDAY],
            symbols[Calendar.FRIDAY],
            symbols[Calendar.SATURDAY]
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.filter_dialog_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.filter_dialog_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.btn_close))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Pilihan Mode Filter
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text(stringResource(R.string.filter_tab_single_date), style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text(stringResource(R.string.filter_tab_date_range), style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text(stringResource(R.string.filter_tab_month_year), style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Konten Picker Sesuai Tab Terpilih
                Box(modifier = Modifier.fillMaxWidth()) {
                    when (selectedTabIndex) {
                        0 -> {
                            // Mode 1: Tanggal Spesifik (DatePicker bawaan Material 3)
                            DatePicker(
                                state = datePickerState,
                                modifier = Modifier.fillMaxWidth(),
                                title = null,
                                showModeToggle = false
                            )
                        }
                        1 -> {
                            // Mode 2: Rentang Tanggal (Kalender Bulanan Paged dengan Panah Navigasi)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Banner Info Rentang Terpilih
                                RangeSummaryBanner(
                                    rangeStartMillis = rangeStartMillis,
                                    rangeEndMillis = rangeEndMillis,
                                    onClear = {
                                        rangeStartMillis = null
                                        rangeEndMillis = null
                                    }
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Tampilan Kalender 1 Bulan dengan Tombol Navigasi Panah [<] [>]
                                PagedMonthRangeCalendar(
                                    displayedYear = rangeDisplayedYear,
                                    displayedMonth = rangeDisplayedMonth,
                                    monthNames = monthNames,
                                    shortWeekdays = shortWeekdays,
                                    rangeStartMillis = rangeStartMillis,
                                    rangeEndMillis = rangeEndMillis,
                                    minYear = appStartYear,
                                    onPreviousMonth = {
                                        if (rangeDisplayedMonth == 0) {
                                            if (rangeDisplayedYear > appStartYear) {
                                                rangeDisplayedMonth = 11
                                                rangeDisplayedYear--
                                            }
                                        } else {
                                            rangeDisplayedMonth--
                                        }
                                    },
                                    onNextMonth = {
                                        if (rangeDisplayedMonth == 11) {
                                            rangeDisplayedMonth = 0
                                            rangeDisplayedYear++
                                        } else {
                                            rangeDisplayedMonth++
                                        }
                                    },
                                    onDayClicked = { clickedMillis ->
                                        if (rangeStartMillis == null) {
                                            rangeStartMillis = clickedMillis
                                            rangeEndMillis = null
                                        } else if (rangeEndMillis == null) {
                                            if (clickedMillis < rangeStartMillis!!) {
                                                rangeEndMillis = rangeStartMillis
                                                rangeStartMillis = clickedMillis
                                            } else {
                                                rangeEndMillis = clickedMillis
                                            }
                                        } else {
                                            // Sudah ada start dan end, mulai pemilihan baru dengan tanggal yang diklik
                                            rangeStartMillis = clickedMillis
                                            rangeEndMillis = null
                                        }
                                    }
                                )
                            }
                        }
                        2 -> {
                            // Mode 3: Bulan & Tahun (Tahun mulai dari 2026 saja + Grid 12 Bulan)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.filter_year_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Chip Tahun: Mulai 2026, jika tahun 2027 tiba akan otomatis bertambah
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    availableYears.forEach { year ->
                                        val isSelected = year == selectedYear
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedYear = year },
                                            label = {
                                                Text(
                                                    text = year.toString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.filter_month_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Grid 12 Bulan (4 baris x 3 kolom)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (row in 0 until 4) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            for (col in 0 until 3) {
                                                val index = row * 3 + col
                                                if (index < monthNames.size) {
                                                    val monthName = monthNames[index]
                                                    val isSelected = index == selectedMonth
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (isSelected) {
                                                            MaterialTheme.colorScheme.primaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.surfaceContainerLow
                                                        },
                                                        border = if (isSelected) {
                                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                                        } else null,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .clickable { selectedMonth = index }
                                                    ) {
                                                        Text(
                                                            text = monthName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) {
                                                                MaterialTheme.colorScheme.onPrimaryContainer
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurface
                                                            },
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.padding(vertical = 12.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Tombol Aksi: Terapkan, Reset, Batal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentFilter.isCustom) {
                        OutlinedButton(
                            onClick = {
                                onFilterSelected(HistoryDateFilter(type = DateFilterType.ALL_TIME))
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.btn_reset_filter), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }

                    Button(
                        onClick = {
                            val newFilter = when (selectedTabIndex) {
                                0 -> {
                                    HistoryDateFilter(
                                        type = DateFilterType.SPECIFIC_DATE,
                                        specificDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                                    )
                                }
                                1 -> {
                                    val start = rangeStartMillis ?: System.currentTimeMillis()
                                    val end = rangeEndMillis ?: start
                                    HistoryDateFilter(
                                        type = DateFilterType.DATE_RANGE,
                                        rangeStartMillis = minOf(start, end),
                                        rangeEndMillis = maxOf(start, end)
                                    )
                                }
                                2 -> {
                                    HistoryDateFilter(
                                        type = DateFilterType.MONTH_YEAR,
                                        selectedMonth = selectedMonth,
                                        selectedYear = selectedYear
                                    )
                                }
                                else -> HistoryDateFilter(type = DateFilterType.ALL_TIME)
                            }
                            onFilterSelected(newFilter)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.btn_apply_filter))
                    }
                }
            }
        }
    }
}

/**
 * Banner status pemilihan rentang tanggal.
 */
@Composable
private fun RangeSummaryBanner(
    rangeStartMillis: Long?,
    rangeEndMillis: Long?,
    onClear: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val bannerText = when {
                    rangeStartMillis != null && rangeEndMillis != null -> {
                        val start = minOf(rangeStartMillis, rangeEndMillis)
                        val end = maxOf(rangeStartMillis, rangeEndMillis)
                        val days = ((end - start) / (1000 * 3600 * 24L)) + 1
                        val s = dateFormat.format(Date(start))
                        val e = dateFormat.format(Date(end))
                        "$s – $e (" + stringResource(R.string.filter_range_days_fmt, days) + ")"
                    }
                    rangeStartMillis != null -> {
                        val s = dateFormat.format(Date(rangeStartMillis))
                        stringResource(R.string.filter_range_start_fmt, s)
                    }
                    else -> {
                        stringResource(R.string.filter_range_select_start)
                    }
                }

                Text(
                    text = bannerText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (rangeStartMillis != null && rangeEndMillis != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (rangeStartMillis != null || rangeEndMillis != null) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hapus Rentang",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Komponen kalender rentang tanggal per-bulan dengan panah navigasi [<] dan [>].
 * Bebas stutter karena hanya merender 1 bulan aktif tanpa LazyColumn tak terhingga.
 */
@Composable
private fun PagedMonthRangeCalendar(
    displayedYear: Int,
    displayedMonth: Int,
    monthNames: List<String>,
    shortWeekdays: List<String>,
    rangeStartMillis: Long?,
    rangeEndMillis: Long?,
    minYear: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClicked: (Long) -> Unit
) {
    val cal = remember(displayedYear, displayedMonth) {
        Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sun, 7 = Sat
    val leadingEmptyCount = firstDayOfWeek - 1
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val totalCells = leadingEmptyCount + daysInMonth
    val rowsCount = (totalCells + 6) / 7

    val isRangeSelected = rangeStartMillis != null && rangeEndMillis != null
    val sortedStart = if (isRangeSelected) minOf(rangeStartMillis!!, rangeEndMillis!!) else rangeStartMillis
    val sortedEnd = if (isRangeSelected) maxOf(rangeStartMillis!!, rangeEndMillis!!) else null

    Column(modifier = Modifier.fillMaxWidth()) {
        // Navigasi Bulan: Nama Bulan & Tahun serta Tombol Panah [<] [>]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${monthNames[displayedMonth]} $displayedYear",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onPreviousMonth,
                    enabled = !(displayedYear <= minYear && displayedMonth <= 0),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Bulan Sebelumnya",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Bulan Berikutnya",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Baris Nama Hari (Min, Sen, Sel, ...)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            shortWeekdays.forEach { weekday ->
                Text(
                    text = weekday,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Grid Tanggal Bulan Ini (5-6 Baris x 7 Kolom)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (row in 0 until rowsCount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        if (cellIndex < leadingEmptyCount || cellIndex >= totalCells) {
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            val dayNum = cellIndex - leadingEmptyCount + 1
                            val cellCal = Calendar.getInstance().apply {
                                clear()
                                set(Calendar.YEAR, displayedYear)
                                set(Calendar.MONTH, displayedMonth)
                                set(Calendar.DAY_OF_MONTH, dayNum)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val cellMillis = cellCal.timeInMillis

                            val isStart = sortedStart != null && isSameDay(cellMillis, sortedStart)
                            val isEnd = sortedEnd != null && isSameDay(cellMillis, sortedEnd)
                            val isBetween = sortedStart != null && sortedEnd != null && cellMillis > sortedStart && cellMillis < sortedEnd
                            val isToday = isSameDay(cellMillis, System.currentTimeMillis())

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Strip Highlight Rentang di Belakang Badge
                                if (isRangeSelected && sortedStart != null && sortedEnd != null && sortedStart < sortedEnd) {
                                    if (isBetween) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(30.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                        )
                                    } else if (isStart) {
                                        Row(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                                            Spacer(modifier = Modifier.weight(0.5f))
                                            Box(
                                                modifier = Modifier
                                                    .weight(0.5f)
                                                    .height(30.dp)
                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                            )
                                        }
                                    } else if (isEnd) {
                                        Row(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(0.5f)
                                                    .height(30.dp)
                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                            )
                                            Spacer(modifier = Modifier.weight(0.5f))
                                        }
                                    }
                                }

                                // Badge Tanggal Utama
                                val isPrimaryBadge = isStart || isEnd
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (isPrimaryBadge) {
                                                Modifier.background(MaterialTheme.colorScheme.primary)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .then(
                                            if (isToday && !isPrimaryBadge) {
                                                Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary), CircleShape)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable { onDayClicked(cellMillis) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isPrimaryBadge || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isPrimaryBadge -> MaterialTheme.colorScheme.onPrimary
                                            isBetween -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Helper untuk membandingkan apakah dua timestamp epoch berada di hari yang sama.
 */
private fun isSameDay(time1: Long?, time2: Long?): Boolean {
    if (time1 == null || time2 == null) return false
    val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

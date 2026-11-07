package com.endo.trackr.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import java.util.Calendar
import java.util.Locale

/**
 * Dialog interaktif untuk memilih filter tanggal kustom:
 * 1. Tanggal Spesifik (Single Date via DatePicker)
 * 2. Rentang Tanggal (Date Range via DateRangePicker)
 * 3. Bulan & Tahun (Month & Year Grid)
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

    val initialDateMillis = currentFilter.specificDateMillis ?: System.currentTimeMillis()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)

    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = currentFilter.rangeStartMillis,
        initialSelectedEndDateMillis = currentFilter.rangeEndMillis
    )

    // State untuk Bulan & Tahun
    val currentCalendar = remember { Calendar.getInstance() }
    var selectedYear by remember {
        mutableIntStateOf(currentFilter.selectedYear ?: currentCalendar.get(Calendar.YEAR))
    }
    var selectedMonth by remember {
        mutableIntStateOf(currentFilter.selectedMonth ?: currentCalendar.get(Calendar.MONTH))
    }

    val monthNames = remember {
        DateFormatSymbols(Locale.getDefault()).months.take(12)
    }

    val availableYears = remember {
        val y = currentCalendar.get(Calendar.YEAR)
        listOf(y - 2, y - 1, y, y + 1)
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    when (selectedTabIndex) {
                        0 -> {
                            // Mode 1: Tanggal Spesifik (DatePicker)
                            DatePicker(
                                state = datePickerState,
                                modifier = Modifier.fillMaxWidth(),
                                showModeToggle = false
                            )
                        }
                        1 -> {
                            // Mode 2: Rentang Tanggal (DateRangePicker)
                            DateRangePicker(
                                state = dateRangePickerState,
                                modifier = Modifier.fillMaxWidth(),
                                showModeToggle = false
                            )
                        }
                        2 -> {
                            // Mode 3: Bulan & Tahun
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                // Pemilih Tahun
                                Text(
                                    text = stringResource(R.string.filter_year_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    availableYears.forEach { year ->
                                        val isSelected = year == selectedYear
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedYear = year },
                                            label = { Text(year.toString(), style = MaterialTheme.typography.bodySmall) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = stringResource(R.string.filter_month_label),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // Grid 12 Bulan
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(monthNames) { index, monthName ->
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
                                                .fillMaxWidth()
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
                                    val start = dateRangePickerState.selectedStartDateMillis ?: System.currentTimeMillis()
                                    val end = dateRangePickerState.selectedEndDateMillis ?: start
                                    HistoryDateFilter(
                                        type = DateFilterType.DATE_RANGE,
                                        rangeStartMillis = start,
                                        rangeEndMillis = end
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

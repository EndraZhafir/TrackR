package com.endo.trackr.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.core.util.LocationUtils
import com.endo.trackr.core.util.StatsTimeRange
import com.endo.trackr.core.util.TravelStatsSummary
import java.util.Locale

/**
 * Komponen kartu metrik statistik modular untuk menampilkan satu indikator analitik.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * Grid lengkap metrik analitik perjalanan (Jarak Haversine, Durasi, Kecepatan, Titik Singgah,
 * serta Estimasi Konsumsi BBM dan Pengeluaran Biaya).
 */
@Composable
fun StatsMetricsGrid(
    stats: TravelStatsSummary,
    modifier: Modifier = Modifier,
    onOpenFuelSettings: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Baris 1: Total Jarak (Haversine) & Total Durasi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Total Jarak",
                value = LocationUtils.formatDistance(stats.totalDistanceMeters),
                subtitle = "Rumus Haversine",
                icon = Icons.Default.Route,
                iconTint = Color(0xFF1976D2),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "Total Waktu",
                value = DateTimeUtils.formatDuration(stats.totalDurationSeconds),
                subtitle = "Durasi Pelacakan",
                icon = Icons.Default.Timer,
                iconTint = Color(0xFF388E3C),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            )
        }

        // Baris 2: Kecepatan Rata-Rata & Kecepatan Puncak
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Kecepatan Rata-Rata",
                value = String.format(Locale.getDefault(), "%.1f km/h", stats.averageSpeedKmh),
                subtitle = "Jarak / Total Jam",
                icon = Icons.Default.Speed,
                iconTint = Color(0xFF7B1FA2),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "Kecepatan Puncak",
                value = String.format(Locale.getDefault(), "%.1f km/h", stats.maxSpeedKmh),
                subtitle = "Top Speed Terdeteksi",
                icon = Icons.Default.ElectricBolt,
                iconTint = Color(0xFFF57C00),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            )
        }

        // Baris 3: Titik Singgah & Total Titik GPS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Titik Singgah",
                value = "${stats.stayPointsCount} Tempat",
                subtitle = "Radius ≤30m, Durasi ≥10m",
                icon = Icons.Default.Place,
                iconTint = Color(0xFFD32F2F),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "Titik GPS Valid",
                value = "${stats.totalPointsCount} Titik",
                subtitle = "Dari ${stats.totalSessionsCount} Sesi Jejak",
                icon = Icons.Default.AutoGraph,
                iconTint = Color(0xFF00796B),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            )
        }

        // Baris 4: Estimasi Konsumsi BBM & Pengeluaran Biaya
        val activeProfile = stats.activeFuelProfile
        if (activeProfile != null && stats.totalFuelLiters != null && stats.totalFuelCost != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Estimasi BBM",
                    value = String.format(Locale.getDefault(), "%.2f Liter", stats.totalFuelLiters),
                    subtitle = String.format(
                        Locale.getDefault(),
                        "Laju: %.2fL • Idle: %.2fL",
                        stats.movingFuelLiters ?: 0.0,
                        stats.idleFuelLiters ?: 0.0
                    ),
                    icon = Icons.Default.LocalGasStation,
                    iconTint = Color(0xFF00897B),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFuelSettings
                )

                MetricCard(
                    title = "Estimasi Biaya",
                    value = String.format(Locale.getDefault(), "Rp %,.0f", stats.totalFuelCost).replace(',', '.'),
                    subtitle = "${activeProfile.name} @ Rp ${String.format(Locale.getDefault(), "%,.0f", activeProfile.pricePerLiter).replace(',', '.')}",
                    icon = Icons.Default.Payments,
                    iconTint = Color(0xFFE65100),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFuelSettings
                )
            }
        } else {
            // Empty State: Tidak ada profil bahan bakar yang aktif
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Estimasi Biaya BBM Dinonaktifkan",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Belum ada jenis bensin aktif di tabel data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    if (onOpenFuelSettings != null) {
                        FilledTonalButton(
                            onClick = onOpenFuelSettings,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Atur BBM", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Selektor rentang waktu (Hari Ini, 7 Hari, 30 Hari, Semua Waktu).
 */
@Composable
fun TimeRangeSelector(
    selectedRange: StatsTimeRange,
    onRangeSelected: (StatsTimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatsTimeRange.entries.forEach { range ->
            val isSelected = range == selectedRange
            FilterChip(
                selected = isSelected,
                onClick = { onRangeSelected(range) },
                label = { Text(range.displayName, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

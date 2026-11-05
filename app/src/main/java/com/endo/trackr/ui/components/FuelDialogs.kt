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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.endo.trackr.data.model.FuelProfile
import java.util.Locale

/**
 * Dialog utama Pengaturan Bahan Bakar & Kendaraan:
 * Mengatur laju idle mesin, memilih profil bahan bakar aktif, serta mengelola CRUD
 * (tambah, edit, dan hapus profil termasuk Pertalite & Pertamax).
 */
@Composable
fun FuelSettingsDialog(
    isVisible: Boolean,
    profiles: List<FuelProfile>,
    selectedProfileId: String?,
    idleRate: Double,
    onSelectProfile: (String) -> Unit,
    onEditProfile: (FuelProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onAddProfileClick: () -> Unit,
    onUpdateIdleRate: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    var isEditingIdleRate by remember { mutableStateOf(false) }
    var idleInputText by remember(idleRate) {
        mutableStateOf(String.format(Locale.US, "%.2f", idleRate))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Pengaturan Bahan Bakar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kelola harga, efisiensi km/L, & konsumsi idle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bagian 1: Pengaturan Laju Idle Mesin
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
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
                                            imageVector = Icons.Default.HourglassTop,
                                            contentDescription = null,
                                            tint = Color(0xFFF57C00),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Laju Konsumsi Idle (Diam/Macet)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (!isEditingIdleRate) {
                                        TextButton(onClick = { isEditingIdleRate = true }) {
                                            Text("Ubah")
                                        }
                                    }
                                }

                                Text(
                                    text = "Konsumsi bensin saat berhenti/lampu merah (Bawaan Honda Vario 160: 0,18 L/jam).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                if (isEditingIdleRate) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = idleInputText,
                                            onValueChange = { idleInputText = it },
                                            label = { Text("Liter / Jam") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )

                                        IconButton(
                                            onClick = {
                                                val parsed = idleInputText.toDoubleOrNull() ?: idleRate
                                                onUpdateIdleRate(parsed)
                                                isEditingIdleRate = false
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Simpan",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(onClick = { isEditingIdleRate = false }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Batal",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.2f Liter / Jam", idleRate),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bagian 2: Header Profil Bahan Bakar
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daftar Profil Bahan Bakar (${profiles.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Pilih salah satu untuk aktif",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // Bagian 3: Empty State jika data tabel kosong
                    if (profiles.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalGasStation,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Data Tabel Bahan Bakar Kosong",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Kalkulasi pengeluaran bensin dinonaktifkan. Tekan tombol '+ Tambah Bahan Bakar' di bawah untuk mulai menghitung estimasi biaya.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        // Daftar Item Bahan Bakar
                        items(profiles, key = { it.id }) { profile ->
                            val isSelected = profile.id == selectedProfileId
                            FuelProfileCard(
                                profile = profile,
                                isSelected = isSelected,
                                onSelect = { onSelectProfile(profile.id) },
                                onEdit = { onEditProfile(profile) },
                                onDelete = { onDeleteProfile(profile.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))

                // Tombol Aksi Tambah Profil Baru
                Button(
                    onClick = onAddProfileClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Bahan Bakar Baru")
                }
            }
        }
    }
}

/**
 * Kartu baris data untuk satu profil bahan bakar.
 */
@Composable
private fun FuelProfileCard(
    profile: FuelProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect
                )

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (isSelected) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "Aktif",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = String.format(
                            Locale.getDefault(),
                            "Rp %,.0f / Liter • Efisiensi: %.1f km/L",
                            profile.pricePerLiter,
                            profile.efficiencyKmL
                        ).replace(',', '.'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tombol Edit & Delete
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Bahan Bakar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus Bahan Bakar",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dialog form input untuk Menambah (+) atau Mengedit profil bahan bakar.
 */
@Composable
fun FuelFormDialog(
    isVisible: Boolean,
    profileToEdit: FuelProfile?,
    onSave: (name: String, price: Double, efficiency: Double, profileId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val isEditing = profileToEdit != null
    var name by remember(profileToEdit) { mutableStateOf(profileToEdit?.name ?: "") }
    var priceText by remember(profileToEdit) {
        mutableStateOf(
            profileToEdit?.let { String.format(Locale.US, "%.0f", it.pricePerLiter) } ?: ""
        )
    }
    var efficiencyText by remember(profileToEdit) {
        mutableStateOf(
            profileToEdit?.let { String.format(Locale.US, "%.1f", it.efficiencyKmL) } ?: "45.0"
        )
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Bahan Bakar" else "Tambah Bahan Bakar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Nama Bahan Bakar") },
                    placeholder = { Text("Contoh: Pertamax, Pertalite, Shell") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = priceText,
                    onValueChange = {
                        priceText = it
                        errorMessage = null
                    },
                    label = { Text("Harga per Liter (Rp)") },
                    placeholder = { Text("Contoh: 15950") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = efficiencyText,
                    onValueChange = {
                        efficiencyText = it
                        errorMessage = null
                    },
                    label = { Text("Rata-Rata Pemakaian (km/L)") },
                    supportingText = { Text("Bawaan Honda Vario 160: 45.0 km/L") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val price = priceText.toDoubleOrNull()
                    val efficiency = efficiencyText.toDoubleOrNull()

                    if (cleanName.isBlank()) {
                        errorMessage = "Nama bahan bakar tidak boleh kosong"
                        return@Button
                    }
                    if (price == null || price <= 0.0) {
                        errorMessage = "Masukkan harga per liter yang valid (> 0)"
                        return@Button
                    }
                    if (efficiency == null || efficiency <= 0.0) {
                        errorMessage = "Masukkan efisiensi km/L yang valid (> 0)"
                        return@Button
                    }

                    onSave(cleanName, price, efficiency, profileToEdit?.id)
                }
            ) {
                Text(if (isEditing) "Simpan Perubahan" else "Tambahkan")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

package com.endo.trackr.data.model

import java.util.UUID

/**
 * Model data representasi profil bahan bakar dan efisiensi kendaraan.
 *
 * @param id Identifier unik untuk profil bahan bakar.
 * @param name Nama jenis bahan bakar (misal: "Pertamax", "Pertalite", "Shell Super").
 * @param pricePerLiter Harga per liter dalam Rupiah (misal: 15950.0 atau 10000.0).
 * @param efficiencyKmL Rata-rata jarak tempuh per liter (km/L) untuk kendaraan (default Honda Vario 160: 45.0 km/L).
 */
data class FuelProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val pricePerLiter: Double,
    val efficiencyKmL: Double
)

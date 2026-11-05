package com.endo.trackr.data.repository

import com.endo.trackr.data.model.FuelProfile
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface repository untuk mengelola data preferensi bahan bakar,
 * profil efisiensi kendaraan, dan laju konsumsi saat mesin diam (idle).
 */
interface FuelPreferencesRepository {

    /**
     * Aliran daftar seluruh profil bahan bakar yang tersimpan (Pertalite, Pertamax, Custom).
     */
    val fuelProfilesFlow: StateFlow<List<FuelProfile>>

    /**
     * Aliran profil bahan bakar yang saat ini dipilih aktif untuk kalkulasi biaya perjalanan.
     * Bernilai `null` jika tidak ada profil yang dipilih atau data table kosong.
     */
    val selectedProfileFlow: StateFlow<FuelProfile?>

    /**
     * Aliran nilai laju konsumsi bensin saat idle/diam dalam satuan Liter per Jam (L/h).
     */
    val idleRateFlow: StateFlow<Double>

    /**
     * Menyimpan profil bahan bakar baru atau memperbarui profil yang sudah ada.
     */
    suspend fun saveProfile(profile: FuelProfile)

    /**
     * Menghapus profil bahan bakar berdasarkan ID-nya.
     */
    suspend fun deleteProfile(profileId: String)

    /**
     * Menetapkan profil bahan bakar yang aktif dipilih.
     */
    suspend fun selectProfile(profileId: String?)

    /**
     * Memperbarui laju konsumsi bensin saat idle mesin (L/jam).
     */
    suspend fun setIdleConsumptionRate(rate: Double)
}

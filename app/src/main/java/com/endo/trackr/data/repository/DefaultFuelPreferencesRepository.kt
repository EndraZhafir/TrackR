package com.endo.trackr.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.endo.trackr.data.model.FuelProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Implementasi [FuelPreferencesRepository] berbasis [SharedPreferences] dengan format serialisasi JSON.
 * Menyediakan nilai default bawaan untuk Honda Vario 160 (Pertamax @ Rp 15.950, Pertalite @ Rp 10.000, 45 km/L, idle 0.18 L/h).
 */
class DefaultFuelPreferencesRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FuelPreferencesRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _fuelProfilesFlow = MutableStateFlow<List<FuelProfile>>(emptyList())
    override val fuelProfilesFlow: StateFlow<List<FuelProfile>> = _fuelProfilesFlow.asStateFlow()

    private val _selectedProfileFlow = MutableStateFlow<FuelProfile?>(null)
    override val selectedProfileFlow: StateFlow<FuelProfile?> = _selectedProfileFlow.asStateFlow()

    private val _idleRateFlow = MutableStateFlow(DEFAULT_IDLE_RATE_LITERS_PER_HOUR)
    override val idleRateFlow: StateFlow<Double> = _idleRateFlow.asStateFlow()

    private var selectedProfileId: String? = null

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        val isInitialized = prefs.getBoolean(KEY_IS_INITIALIZED, false)

        val profiles: List<FuelProfile> = if (!isInitialized) {
            // Inisialisasi awal bawaan aplikasi
            val defaultList = listOf(
                FuelProfile(
                    id = DEFAULT_PERTAMAX_ID,
                    name = "Pertamax",
                    pricePerLiter = 15950.0,
                    efficiencyKmL = 45.0 // Efisiensi Honda Vario 160
                ),
                FuelProfile(
                    id = DEFAULT_PERTALITE_ID,
                    name = "Pertalite",
                    pricePerLiter = 10000.0,
                    efficiencyKmL = 45.0 // Efisiensi Honda Vario 160
                )
            )
            selectedProfileId = DEFAULT_PERTAMAX_ID // Pertamax dipilih secara default
            saveToDisk(defaultList, selectedProfileId, DEFAULT_IDLE_RATE_LITERS_PER_HOUR, initialized = true)
            defaultList
        } else {
            val jsonString = prefs.getString(KEY_PROFILES_JSON, null)
            selectedProfileId = prefs.getString(KEY_SELECTED_PROFILE_ID, null)
            val parsedProfiles = deserializeProfiles(jsonString)
            parsedProfiles
        }

        val idleRate = prefs.getFloat(KEY_IDLE_RATE, DEFAULT_IDLE_RATE_LITERS_PER_HOUR.toFloat()).toDouble()

        _fuelProfilesFlow.value = profiles
        _idleRateFlow.value = idleRate
        updateSelectedProfileState(profiles, selectedProfileId)
    }

    private fun updateSelectedProfileState(profiles: List<FuelProfile>, activeId: String?) {
        val active = if (activeId != null) {
            profiles.find { it.id == activeId }
        } else {
            null
        }
        _selectedProfileFlow.value = active
    }

    override suspend fun saveProfile(profile: FuelProfile) = withContext(ioDispatcher) {
        val currentList = _fuelProfilesFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == profile.id }

        if (index >= 0) {
            currentList[index] = profile
        } else {
            currentList.add(profile)
        }

        // Jika sebelumnya belum ada yang dipilih dan ini profil pertama, otomatis pilih
        if (selectedProfileId == null || !currentList.any { it.id == selectedProfileId }) {
            selectedProfileId = profile.id
        }

        _fuelProfilesFlow.value = currentList
        updateSelectedProfileState(currentList, selectedProfileId)
        saveToDisk(currentList, selectedProfileId, _idleRateFlow.value, initialized = true)
    }

    override suspend fun deleteProfile(profileId: String) = withContext(ioDispatcher) {
        val currentList = _fuelProfilesFlow.value.toMutableList()
        currentList.removeAll { it.id == profileId }

        // Jika profil yang dihapus adalah profil yang sedang aktif dipilih
        if (selectedProfileId == profileId) {
            selectedProfileId = currentList.firstOrNull()?.id
        }

        _fuelProfilesFlow.value = currentList
        updateSelectedProfileState(currentList, selectedProfileId)
        saveToDisk(currentList, selectedProfileId, _idleRateFlow.value, initialized = true)
    }

    override suspend fun selectProfile(profileId: String?) = withContext(ioDispatcher) {
        selectedProfileId = profileId
        updateSelectedProfileState(_fuelProfilesFlow.value, profileId)
        prefs.edit().putString(KEY_SELECTED_PROFILE_ID, profileId).apply()
    }

    override suspend fun setIdleConsumptionRate(rate: Double) = withContext(ioDispatcher) {
        val validRate = if (rate < 0.0) 0.0 else rate
        _idleRateFlow.value = validRate
        prefs.edit().putFloat(KEY_IDLE_RATE, validRate.toFloat()).apply()
    }

    private fun saveToDisk(
        profiles: List<FuelProfile>,
        selectedId: String?,
        idleRate: Double,
        initialized: Boolean
    ) {
        val jsonString = serializeProfiles(profiles)
        prefs.edit()
            .putString(KEY_PROFILES_JSON, jsonString)
            .putString(KEY_SELECTED_PROFILE_ID, selectedId)
            .putFloat(KEY_IDLE_RATE, idleRate.toFloat())
            .putBoolean(KEY_IS_INITIALIZED, initialized)
            .apply()
    }

    private fun serializeProfiles(profiles: List<FuelProfile>): String {
        val array = JSONArray()
        for (p in profiles) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("pricePerLiter", p.pricePerLiter)
                put("efficiencyKmL", p.efficiencyKmL)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeProfiles(jsonString: String?): List<FuelProfile> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            val list = mutableListOf<FuelProfile>()
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    FuelProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        pricePerLiter = obj.getDouble("pricePerLiter"),
                        efficiencyKmL = obj.getDouble("efficiencyKmL")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "trackr_fuel_preferences"
        private const val KEY_PROFILES_JSON = "key_fuel_profiles_json"
        private const val KEY_SELECTED_PROFILE_ID = "key_selected_profile_id"
        private const val KEY_IDLE_RATE = "key_idle_rate"
        private const val KEY_IS_INITIALIZED = "key_is_initialized"

        const val DEFAULT_PERTAMAX_ID = "default_pertamax"
        const val DEFAULT_PERTALITE_ID = "default_pertalite"
        const val DEFAULT_IDLE_RATE_LITERS_PER_HOUR = 0.18 // Honda Vario 160 idling rate
    }
}

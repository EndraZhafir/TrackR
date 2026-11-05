package com.endo.trackr.core.di

import android.content.Context
import com.endo.trackr.data.local.TrackRDatabase
import com.endo.trackr.data.repository.DefaultFuelPreferencesRepository
import com.endo.trackr.data.repository.DefaultGeocodingRepository
import com.endo.trackr.data.repository.FuelPreferencesRepository
import com.endo.trackr.data.repository.GeocodingRepository
import com.endo.trackr.data.repository.LocationRepository
import com.endo.trackr.data.repository.OfflineLocationRepository

/**
 * Implementasi default dari [AppContainer] yang menginisialisasi Room Database
 * dan menyediakan instance [LocationRepository], [GeocodingRepository], serta [FuelPreferencesRepository].
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    private val database: TrackRDatabase by lazy {
        TrackRDatabase.getDatabase(context)
    }

    override val locationRepository: LocationRepository by lazy {
        OfflineLocationRepository(
            sessionDao = database.trackingSessionDao(),
            pointDao = database.locationPointDao(),
            stayPointDao = database.stayPointDao()
        )
    }

    override val geocodingRepository: GeocodingRepository by lazy {
        DefaultGeocodingRepository(context)
    }

    override val fuelPreferencesRepository: FuelPreferencesRepository by lazy {
        DefaultFuelPreferencesRepository(context)
    }
}

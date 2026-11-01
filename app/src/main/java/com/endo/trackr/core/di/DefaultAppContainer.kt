package com.endo.trackr.core.di

import android.content.Context
import com.endo.trackr.data.local.TrackRDatabase
import com.endo.trackr.data.repository.LocationRepository
import com.endo.trackr.data.repository.OfflineLocationRepository

/**
 * Implementasi default dari [AppContainer] yang menginisialisasi Room Database
 * dan menyediakan instance [LocationRepository].
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
}

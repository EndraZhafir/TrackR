package com.endo.trackr.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endo.trackr.TrackRApplication
import com.endo.trackr.ui.screens.home.HomeViewModel

/**
 * Factory penyedia instance ViewModel untuk seluruh aplikasi TrackR
 * dengan menginjeksikan dependensi dari [AppContainer].
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                locationRepository = trackRApplication().container.locationRepository,
                geocodingRepository = trackRApplication().container.geocodingRepository
            )
        }
    }
}

/**
 * Extension function untuk mengambil instance [TrackRApplication] dari [CreationExtras].
 */
fun CreationExtras.trackRApplication(): TrackRApplication =
    (this[APPLICATION_KEY] as TrackRApplication)

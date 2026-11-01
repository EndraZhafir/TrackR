package com.endo.trackr.core.di

import com.endo.trackr.data.repository.LocationRepository

/**
 * Interface Dependency Injection (DI) Container untuk menyediakan dependensi
 * di seluruh aplikasi TrackR.
 */
interface AppContainer {
    val locationRepository: LocationRepository
}

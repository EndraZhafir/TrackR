package com.endo.trackr

import android.app.Application
import com.endo.trackr.core.di.AppContainer
import com.endo.trackr.core.di.DefaultAppContainer

/**
 * Kelas [Application] kustom untuk TrackR yang memegang instance tunggal [AppContainer].
 */
class TrackRApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

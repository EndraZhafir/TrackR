package com.endo.trackr

import android.app.Application
import android.util.Log
import com.endo.trackr.core.di.AppContainer
import com.endo.trackr.core.di.DefaultAppContainer
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kelas [Application] kustom untuk TrackR yang memegang instance tunggal [AppContainer]
 * dan menangani pencatatan log crash unhandled secara lokal untuk keandalan di latar belakang.
 */
class TrackRApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        setupUncaughtCrashLogger()
        container = DefaultAppContainer(this)
    }

    private fun setupUncaughtCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logDir = getExternalFilesDir(null) ?: filesDir
                val logFile = File(logDir, "crash_log.txt")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                PrintWriter(FileWriter(logFile, true)).use { writer ->
                    writer.println("=== TRACKR CRASH AT $timestamp ===")
                    writer.println("Thread: ${thread.name}")
                    throwable.printStackTrace(writer)
                    writer.println("=====================================\n")
                }
                Log.e("TrackRApplication", "CRASH DETECTED and logged to ${logFile.absolutePath}", throwable)
            } catch (e: Exception) {
                Log.e("TrackRApplication", "Failed to write crash log", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

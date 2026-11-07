package com.endo.trackr.core.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Pengelola preferensi bahasa aplikasi (In-App Language Selection).
 * Mendukung Bahasa Indonesia ("in") dan Bahasa Inggris ("en") dengan kompatibilitas
 * penuh untuk Android 13+ (LocaleManager Per-App Language) serta Android versi sebelumnya.
 */
object LanguageManager {

    const val LANG_INDONESIAN = "in"
    const val LANG_ENGLISH = "en"

    private const val PREFS_NAME = "trackr_language_preferences"
    private const val KEY_LANGUAGE = "key_selected_language"

    /**
     * Mengambil kode bahasa tersimpan dari SharedPreferences.
     * Default: Bahasa Indonesia ("in").
     */
    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANG_INDONESIAN) ?: LANG_INDONESIAN
    }

    /**
     * Memperbarui bahasa aplikasi secara persisten dan memicu pembaruan tampilan antarmuka.
     */
    fun setLanguage(activity: Activity, langCode: String) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, langCode).apply()

        // 1. Integrasi Per-App Language Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = activity.getSystemService(LocaleManager::class.java)
            localeManager?.applicationLocales = LocaleList.forLanguageTags(langCode)
        }

        // 2. Re-create Activity untuk memuat string resource bahasa baru di Jetpack Compose
        activity.recreate()
    }

    /**
     * Membungkus context dengan konfigurasi [Locale] yang dipilih.
     * Digunakan pada [Activity.attachBaseContext] untuk memastikan komposisi Compose
     * membaca string resource bahasa yang tepat sejak awal lifecycle.
     */
    fun applyLanguageContext(context: Context, langCode: String = getSavedLanguage(context)): Context {
        val locale = if (langCode == LANG_ENGLISH) {
            Locale.ENGLISH
        } else {
            Locale.forLanguageTag("in-ID")
        }
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }
}

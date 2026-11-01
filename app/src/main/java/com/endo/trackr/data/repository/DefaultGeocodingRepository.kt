package com.endo.trackr.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Implementasi default [GeocodingRepository] menggunakan native Android [Geocoder].
 * Mendukung callback asinkron modern (Android 13+ / API 33+) serta eksekusi IO thread
 * yang aman untuk versi Android sebelumnya.
 */
class DefaultGeocodingRepository(
    private val context: Context
) : GeocodingRepository {

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) {
            Log.w(TAG, "Geocoder service is not present on this device.")
            return null
        }

        val geocoder = Geocoder(context, Locale.getDefault())

        return try {
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getFromLocationAsync(geocoder, latitude, longitude)
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)
                }
            }

            val address = addresses?.firstOrNull() ?: return null
            formatAddress(address)
        } catch (e: IOException) {
            Log.e(TAG, "Network or I/O error during reverse geocoding ($latitude, $longitude)", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during reverse geocoding", e)
            null
        }
    }

    private suspend fun getFromLocationAsync(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double
    ): List<Address>? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(
                latitude,
                longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        Log.e(TAG, "GeocodeListener error: $errorMessage")
                        continuation.resume(null)
                    }
                }
            )
        } else {
            continuation.resume(null)
        }
    }

    /**
     * Memformat objek [Address] menjadi string nama jalan/tempat yang ringkas dan alami.
     * Contoh: "Jl. M.H. Thamrin, Gambir, Jakarta Pusat"
     */
    private fun formatAddress(address: Address): String {
        val parts = mutableListOf<String>()

        // 1. Nama jalan atau nama tempat
        val streetOrFeature = address.thoroughfare ?: address.featureName
        if (!streetOrFeature.isNullOrBlank()) {
            parts.add(streetOrFeature)
        }

        // 2. Kelurahan / Desa / Sub-locality
        if (!address.subLocality.isNullOrBlank() && address.subLocality != streetOrFeature) {
            parts.add(address.subLocality)
        }

        // 3. Kecamatan / Kota / Locality
        val city = address.locality ?: address.subAdminArea
        if (!city.isNullOrBlank() && city != address.subLocality) {
            parts.add(city)
        }

        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            // Fallback ke baris alamat lengkap pertama jika parsing spesifik kosong
            address.getAddressLine(0) ?: "${String.format("%.4f", address.latitude)}, ${String.format("%.4f", address.longitude)}"
        }
    }

    companion object {
        private const val TAG = "TrackRGeocoding"
    }
}

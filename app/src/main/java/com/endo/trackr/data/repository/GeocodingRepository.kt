package com.endo.trackr.data.repository

/**
 * Interface Repository untuk menangani penerjemahan koordinat geospasial (Latitude, Longitude)
 * menjadi representasi nama jalan, kelurahan/kecamatan, dan kota yang ramah dibaca pengguna
 * (Reverse Geocoding).
 */
interface GeocodingRepository {

    /**
     * Menerjemahkan koordinat GPS menjadi string nama alamat yang diformat.
     * Mengembalikan null jika Geocoder tidak tersedia atau terjadi kegagalan jaringan.
     *
     * @param latitude Garis lintang titik koordinat.
     * @param longitude Garis bujur titik koordinat.
     * @return Alamat terformat (misal: "Jl. M.H. Thamrin, Menteng, Jakarta Pusat") atau null.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String?
}

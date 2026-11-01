package com.endo.trackr.data.exporter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * Manajer orkestrator ekspor data riwayat perjalanan TrackR.
 * Menangani pemilihan generator format (GPX, KML, GeoJSON), penulisan berkas ke URI (Storage Access Framework),
 * dan pembuatan berkas cache sementara untuk fitur berbagi langsung (ShareSheet via FileProvider).
 */
object DataExporter {

    private const val TAG = "DataExporter"

    /**
     * Menghasilkan isi dokumen teks GIS berdasarkan format yang dipilih.
     */
    fun generateDocument(
        format: ExportFormat,
        sessionTitle: String,
        points: List<LocationPointEntity>,
        stayPoints: List<StayPointEntity> = emptyList(),
        includeStayPoints: Boolean = true,
        filterJitter: Boolean = true
    ): String {
        return when (format) {
            ExportFormat.GPX -> GpxExporter.export(
                sessionTitle = sessionTitle,
                points = points,
                stayPoints = stayPoints,
                includeStayPoints = includeStayPoints,
                filterJitter = filterJitter
            )
            ExportFormat.KML -> KmlExporter.export(
                sessionTitle = sessionTitle,
                points = points,
                stayPoints = stayPoints,
                includeStayPoints = includeStayPoints,
                filterJitter = filterJitter
            )
            ExportFormat.GEOJSON -> GeoJsonExporter.export(
                sessionTitle = sessionTitle,
                points = points,
                stayPoints = stayPoints,
                includeStayPoints = includeStayPoints,
                filterJitter = filterJitter
            )
        }
    }

    /**
     * Menghasilkan nama berkas default yang bersih dari karakter ilegal sistem berkas.
     * Contoh: "TrackR_Jejak_01_Nov_2026_181500.gpx"
     */
    fun generateFilename(sessionTitle: String, format: ExportFormat, epochMillis: Long = System.currentTimeMillis()): String {
        val sanitizedTitle = sessionTitle
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(30)
            .ifEmpty { "Jejak" }

        val timeSuffix = DateTimeUtils.formatFilenameTimestamp(epochMillis)
        return "TrackR_${sanitizedTitle}_${timeSuffix}.${format.extension}"
    }

    /**
     * Menulis teks dokumen ke berkas tujuan yang dipilih pengguna melalui Storage Access Framework (SAF).
     */
    fun writeContentToUri(context: Context, destinationUri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menulis berkas ekspor ke URI: $destinationUri", e)
            false
        }
    }

    /**
     * Menyimpan berkas ke direktori cache lokal dan mengembalikan [Intent] pemanggilan ShareSheet Android.
     */
    fun createShareIntent(
        context: Context,
        filename: String,
        content: String,
        format: ExportFormat
    ): Intent? {
        return try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val exportFile = File(exportDir, filename)
            FileOutputStream(exportFile).use { fos ->
                OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            }

            val authority = "${context.packageName}.fileprovider"
            val fileUri = FileProvider.getUriForFile(context, authority, exportFile)

            Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "Ekspor Jejak TrackR: $filename")
                putExtra(Intent.EXTRA_TEXT, "Berikut adalah berkas riwayat perjalanan ${format.title} dari aplikasi TrackR.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat berkas cache untuk berbagi file: $filename", e)
            null
        }
    }
}

package com.endo.trackr.ui.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import com.endo.trackr.data.local.entity.LocationPointEntity
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.max
import kotlin.math.min

/**
 * Overlay kanvas kustom untuk OpenStreetMap (osmdroid) yang merender visualisasi
 * kepadatan titik pergerakan dan titik singgah (Density Heatmap) berbasis gradien warna.
 *
 * Rentang intensitas kepadatan:
 * - Rendah: Biru / Cyan (Jalur pergerakan cepat yang jarang dilalui)
 * - Sedang: Hijau / Kuning (Rute harian reguler)
 * - Tinggi: Oranye / Merah (Pusat titik singgah atau persimpangan padat aktivitas)
 */
class HeatmapOverlay(
    private val points: List<LocationPointEntity>
) : Overlay() {

    private val screenPoint = Point()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || points.isEmpty()) return

        val projection = mapView.projection
        val boundingBox = mapView.boundingBox

        // Filter hanya titik yang berada di dalam atau di sekitar viewport layar saat ini
        val visiblePoints = points.filter { point ->
            !point.isJitter &&
                point.latitude >= boundingBox.latSouth - 0.01 &&
                point.latitude <= boundingBox.latNorth + 0.01 &&
                point.longitude >= boundingBox.lonWest - 0.01 &&
                point.longitude <= boundingBox.lonEast + 0.01
        }

        if (visiblePoints.isEmpty()) return

        // 1. Grid spatial binning pada ruang koordinat layar (viewport pixel)
        // Ukuran sel grid dinamis berdasarkan tingkat pembesaran (zoom level)
        val zoom = mapView.zoomLevelDouble
        val cellSize = (36f * (zoom / 15.0)).toFloat().coerceIn(24f, 64f)
        val densityGrid = mutableMapOf<Pair<Int, Int>, HeatmapCell>()

        val geoPoint = GeoPoint(0.0, 0.0)
        for (point in visiblePoints) {
            geoPoint.setCoords(point.latitude, point.longitude)
            projection.toPixels(geoPoint, screenPoint)

            val cellX = (screenPoint.x / cellSize).toInt()
            val cellY = (screenPoint.y / cellSize).toInt()
            val key = Pair(cellX, cellY)

            val existing = densityGrid[key]
            if (existing != null) {
                existing.count++
                existing.totalX += screenPoint.x
                existing.totalY += screenPoint.y
            } else {
                densityGrid[key] = HeatmapCell(
                    count = 1,
                    totalX = screenPoint.x.toLong(),
                    totalY = screenPoint.y.toLong()
                )
            }
        }

        if (densityGrid.isEmpty()) return

        val maxCount = densityGrid.values.maxOfOrNull { it.count } ?: 1
        val radius = cellSize * 0.95f

        // 2. Gambar bulatan kepadatan dengan gradasi warna berbasis intensitas
        for (cell in densityGrid.values) {
            val centerX = (cell.totalX / cell.count).toFloat()
            val centerY = (cell.totalY / cell.count).toFloat()

            // Normalisasi intensitas kepadatan (0.0f hingga 1.0f)
            val intensity = (cell.count.toFloat() / max(maxCount.toFloat(), 1f)).coerceIn(0.15f, 1.0f)
            val (centerColor, edgeColor) = getColorGradientForIntensity(intensity)

            paint.shader = RadialGradient(
                centerX,
                centerY,
                radius,
                centerColor,
                edgeColor,
                Shader.TileMode.CLAMP
            )

            canvas.drawCircle(centerX, centerY, radius, paint)
        }

        paint.shader = null
    }

    /**
     * Menghitung warna gradien radial berdasarkan intensitas kepadatan.
     */
    private fun getColorGradientForIntensity(intensity: Float): Pair<Int, Int> {
        return when {
            // Intensitas Ekstrem (Titik Singgah Utama): Merah Api / Oranye Pekat
            intensity >= 0.70f -> Pair(
                Color.argb(180, 255, 61, 0),    // Deep Orange (#FF3D00)
                Color.argb(0, 255, 61, 0)
            )
            // Intensitas Tinggi: Kuning Terang / Amber
            intensity >= 0.45f -> Pair(
                Color.argb(150, 255, 214, 0),   // Amber (#FFD600)
                Color.argb(0, 255, 214, 0)
            )
            // Intensitas Menengah: Hijau Terang
            intensity >= 0.25f -> Pair(
                Color.argb(130, 118, 255, 3),   // Lime Light (#76FF03)
                Color.argb(0, 118, 255, 3)
            )
            // Intensitas Rendah: Biru Langit / Cyan Transparan
            else -> Pair(
                Color.argb(110, 0, 176, 255),   // Cyan Blue (#00B0FF)
                Color.argb(0, 0, 176, 255)
            )
        }
    }

    private class HeatmapCell(
        var count: Int,
        var totalX: Long,
        var totalY: Long
    )
}

package com.endo.trackr.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Komponen peta OpenStreetMap (osmdroid) berbasis Jetpack Compose yang aman terhadap lifecycle,
 * merender jejak rute perjalanan (Polyline) dan marker titik singgah (Stay Points).
 */
@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    points: List<LocationPointEntity> = emptyList(),
    stayPoints: List<StayPointEntity> = emptyList(),
    latestPoint: LocationPointEntity? = null,
    isTrackingActive: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Inisialisasi konfigurasi user-agent osmdroid sesuai kepatuhan OpenStreetMap
    remember {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(16.5)

            // Koordinat default awal (Jakarta / Monas jika belum ada titik GPS)
            val defaultPoint = GeoPoint(-6.175392, 106.827153)
            controller.setCenter(defaultPoint)
        }
    }

    // Manajemen lifecycle aman untuk osmdroid di dalam Compose
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.matchParentSize(),
            update = { view ->
                // Bersihkan overlay lama sebelum menggambar titik terbaru
                view.overlays.clear()

                // 1. Gambar Polyline Jejak Rute (Hanya titik valid, abaikan jitter)
                val validPoints = points.filter { !it.isJitter }
                if (validPoints.size >= 2) {
                    val geoPoints = validPoints.map { GeoPoint(it.latitude, it.longitude) }
                    val polyline = Polyline(view).apply {
                        outlinePaint.color = Color.parseColor("#1976D2") // Biru Material Utama
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        setPoints(geoPoints)
                    }
                    view.overlays.add(polyline)
                }

                // 2. Marker Titik Awal Keberangkatan
                if (validPoints.isNotEmpty()) {
                    val startPoint = validPoints.first()
                    val startMarker = Marker(view).apply {
                        position = GeoPoint(startPoint.latitude, startPoint.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Titik Keberangkatan"
                        snippet = DateTimeUtils.formatTimeOnly(startPoint.timestamp)
                    }
                    view.overlays.add(startMarker)
                }

                // 3. Marker Titik Singgah (Stay Points)
                for (stay in stayPoints) {
                    val stayMarker = Marker(view).apply {
                        position = GeoPoint(stay.latitude, stay.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = stay.resolvedAddress ?: "Titik Singgah"
                        snippet = "Singgah: ${DateTimeUtils.formatDuration(stay.durationSeconds)} (${stay.pointCount} titik)"
                    }
                    view.overlays.add(stayMarker)
                }

                // 4. Marker Posisi Terkini
                if (latestPoint != null) {
                    val currentGeoPoint = GeoPoint(latestPoint.latitude, latestPoint.longitude)
                    val currentMarker = Marker(view).apply {
                        position = currentGeoPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Posisi Saat Ini"
                        snippet = "Akurasi: ±${String.format("%.1f", latestPoint.accuracy)}m"
                    }
                    view.overlays.add(currentMarker)

                    // Jika tracking sedang berjalan aktif, pusatkan peta ke posisi terkini
                    if (isTrackingActive) {
                        view.controller.animateTo(currentGeoPoint)
                    }
                }

                view.invalidate()
            }
        )

        // Tombol FAB kecil di atas peta untuk memusatkan kembali lokasi (Re-center)
        if (latestPoint != null) {
            FloatingActionButton(
                onClick = {
                    val targetGeoPoint = GeoPoint(latestPoint.latitude, latestPoint.longitude)
                    mapView.controller.animateTo(targetGeoPoint)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(44.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Pusatkan Lokasi Terkini",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

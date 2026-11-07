package com.endo.trackr.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.endo.trackr.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Komponen peta OpenStreetMap (osmdroid) berbasis Jetpack Compose yang aman terhadap lifecycle,
 * merender jejak rute perjalanan (Polyline), marker titik singgah (Stay Points),
 * visualisasi lapisan kepadatan panas (Density Heatmap), serta interaksi sentuh bebas janky
 * dan pemusatan otomatis ke koordinat riil pengguna.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    points: List<LocationPointEntity> = emptyList(),
    stayPoints: List<StayPointEntity> = emptyList(),
    latestPoint: LocationPointEntity? = null,
    isTrackingActive: Boolean = false,
    showHeatmap: Boolean = false,
    onToggleHeatmap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userCurrentLocation by remember { mutableStateOf<GeoPoint?>(null) }

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

            // Koordinat default awal (Jakarta / Monas jika GPS belum dapat dibaca)
            val defaultPoint = GeoPoint(-6.175392, 106.827153)
            controller.setCenter(defaultPoint)

            // Poin 6: Cegah LazyColumn / parent scroll container membajak gesture drag & pan peta
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
        }
    }

    // Poin 10: Inisialisasi koordinat awal peta ke lokasi riil GPS pengguna saat pertama dibuka
    LaunchedEffect(Unit) {
        if (hasMapLocationPermission(context)) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        val geo = GeoPoint(loc.latitude, loc.longitude)
                        userCurrentLocation = geo
                        if (points.isEmpty() && latestPoint == null) {
                            mapView.controller.setCenter(geo)
                            mapView.controller.setZoom(16.5)
                        }
                    } else {
                        // Fallback ke getCurrentLocation seimbang daya jika lastLocation bernilai null
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                            .addOnSuccessListener { curLoc ->
                                if (curLoc != null) {
                                    val geo = GeoPoint(curLoc.latitude, curLoc.longitude)
                                    userCurrentLocation = geo
                                    if (points.isEmpty() && latestPoint == null) {
                                        mapView.controller.setCenter(geo)
                                        mapView.controller.setZoom(16.5)
                                    }
                                }
                            }
                    }
                }
            } catch (e: SecurityException) {
                // Izin belum aktif
            }
        }
    }

    // Pusatkan peta ke titik terakhir jika terdapat riwayat rute yang dimuat
    LaunchedEffect(points.size) {
        val validPoints = points.filter { !it.isJitter }
        if (validPoints.isNotEmpty() && !isTrackingActive) {
            val last = validPoints.last()
            mapView.controller.setCenter(GeoPoint(last.latitude, last.longitude))
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

                val validPoints = points.filter { !it.isJitter }

                // 1. Lapisan Heatmap Kepadatan (Jika mode Heatmap aktif)
                if (showHeatmap && validPoints.isNotEmpty()) {
                    view.overlays.add(HeatmapOverlay(validPoints))
                }

                // 2. Gambar Polyline Jejak Rute
                if (!showHeatmap && validPoints.size >= 2) {
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

                // 3. Marker Titik Awal Keberangkatan
                if (validPoints.isNotEmpty()) {
                    val startPoint = validPoints.first()
                    val startMarker = Marker(view).apply {
                        position = GeoPoint(startPoint.latitude, startPoint.longitude)
                        title = "Titik Keberangkatan"
                        snippet = "Mulai: ${DateTimeUtils.formatTimeOnly(startPoint.timestamp)}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    view.overlays.add(startMarker)
                }

                // 4. Marker untuk Titik-Titik Singgah (Stay Points)
                stayPoints.forEachIndexed { index, stay ->
                    val stayMarker = Marker(view).apply {
                        position = GeoPoint(stay.latitude, stay.longitude)
                        title = stay.resolvedAddress ?: "Titik Singgah #${index + 1}"
                        val arriveStr = DateTimeUtils.formatTimeOnly(stay.arrivalTime)
                        val departStr = DateTimeUtils.formatTimeOnly(stay.departureTime)
                        val durationStr = DateTimeUtils.formatDuration(stay.durationSeconds)
                        snippet = "Singgah $durationStr ($arriveStr - $departStr)"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    view.overlays.add(stayMarker)
                }

                // 5. Marker Titik Terkini / Live Tracking / Posisi Riil Pengguna
                val currentPoint = latestPoint ?: validPoints.lastOrNull()
                if (currentPoint != null) {
                    val currentMarker = Marker(view).apply {
                        position = GeoPoint(currentPoint.latitude, currentPoint.longitude)
                        title = if (isTrackingActive) "Posisi Terkini (Aktif)" else "Posisi Terakhir"
                        snippet = "Akurasi: ±${String.format("%.1f", currentPoint.accuracy)}m, Speed: ${String.format("%.1f", currentPoint.speed * 3.6f)} km/h"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    view.overlays.add(currentMarker)

                    // Jika tracking sedang aktif merekam, pusatkan peta ke titik GPS terbaru
                    if (isTrackingActive) {
                        view.controller.setCenter(GeoPoint(currentPoint.latitude, currentPoint.longitude))
                    }
                } else if (userCurrentLocation != null) {
                    // Tampilkan marker posisi pengguna riil saat belum merekam rute
                    val userMarker = Marker(view).apply {
                        position = userCurrentLocation
                        title = "Lokasi Anda Saat Ini"
                        snippet = "Siap untuk merekam perjalanan"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    view.overlays.add(userMarker)
                }

                view.invalidate()
            }
        )

        // Tombol FAB & Kontrol Lapisan di Atas Peta
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tombol Toggle Heatmap Kepadatan (jika callback disediakan)
            if (onToggleHeatmap != null) {
                FloatingActionButton(
                    onClick = onToggleHeatmap,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    containerColor = if (showHeatmap) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (showHeatmap) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Icon(
                        imageVector = if (showHeatmap) Icons.Default.Whatshot else Icons.Default.Layers,
                        contentDescription = "Toggle Heatmap",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Tombol FAB untuk memusatkan kembali lokasi (Re-center) - Poin 10: Selalu aktif & responsif
            FloatingActionButton(
                onClick = {
                    val validPoints = points.filter { !it.isJitter }
                    val targetPoint = latestPoint ?: validPoints.lastOrNull()
                    if (targetPoint != null) {
                        mapView.controller.animateTo(GeoPoint(targetPoint.latitude, targetPoint.longitude))
                    } else if (userCurrentLocation != null) {
                        mapView.controller.animateTo(userCurrentLocation)
                    } else if (hasMapLocationPermission(context)) {
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                if (loc != null) {
                                    val geo = GeoPoint(loc.latitude, loc.longitude)
                                    userCurrentLocation = geo
                                    mapView.controller.animateTo(geo)
                                } else {
                                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                        .addOnSuccessListener { curLoc ->
                                            if (curLoc != null) {
                                                val geo = GeoPoint(curLoc.latitude, curLoc.longitude)
                                                userCurrentLocation = geo
                                                mapView.controller.animateTo(geo)
                                            } else {
                                                Toast.makeText(context, "Sinyal GPS belum terdeteksi", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                }
                            }
                        } catch (e: SecurityException) {
                            Toast.makeText(context, "Izin lokasi diperlukan", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Izin lokasi diperlukan untuk memusatkan peta", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(44.dp),
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

        // Legenda Heatmap di Pojok Kiri Bawah (hanya saat mode Heatmap aktif)
        if (showHeatmap) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.heatmap_density_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(androidx.compose.ui.graphics.Color(0xFF00B0FF), CircleShape)
                        )
                        Text(stringResource(R.string.heatmap_density_low), style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(androidx.compose.ui.graphics.Color(0xFFFFD600), CircleShape)
                        )
                        Text(stringResource(R.string.heatmap_density_medium), style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(androidx.compose.ui.graphics.Color(0xFFFF3D00), CircleShape)
                        )
                        Text(stringResource(R.string.heatmap_density_high), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * Helper internal untuk memeriksa ketersediaan izin akses lokasi.
 */
private fun hasMapLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

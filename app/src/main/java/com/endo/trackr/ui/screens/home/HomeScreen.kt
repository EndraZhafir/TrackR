package com.endo.trackr.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.core.util.LocationUtils
import com.endo.trackr.core.util.TimelineSegment
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.TrackingSessionEntity
import com.endo.trackr.data.local.entity.TrackingStatus
import com.endo.trackr.service.motion.MotionState
import com.endo.trackr.ui.AppViewModelProvider
import com.endo.trackr.ui.components.ExportDialog
import com.endo.trackr.ui.components.OsmMapView
import com.endo.trackr.ui.components.StatsMetricsGrid
import com.endo.trackr.ui.components.TimeRangeSelector
import com.endo.trackr.ui.components.TimelineSegmentItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Konfigurasi Izin Runtime (Location & Notification untuk Android 13+)
    val requiredPermissions = remember {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            viewModel.startTracking(context)
            Toast.makeText(context, "Foreground Service dimulai", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "Izin lokasi diperlukan untuk merekam rute perjalanan",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Launcher untuk Storage Access Framework (SAF) - Simpan Berkas Ekspor GIS
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null) {
            val success = viewModel.saveExportToUri(context, uri)
            if (success) {
                Toast.makeText(context, "Berkas berhasil diekspor dan disimpan!", Toast.LENGTH_SHORT).show()
                viewModel.closeExportDialog()
            } else {
                Toast.makeText(context, "Gagal menyimpan berkas ke penyimpanan.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TrackR",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Modul 8: Heatmap & Statistik Jarak (Haversine)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (uiState.activePoints.isEmpty()) {
                                Toast.makeText(context, "Belum ada titik jejak untuk diekspor", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.openExportDialog()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Ekspor Riwayat Jejak"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Tab Navigasi: Peta & Pelacak, Timeline, Statistik & Heatmap
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Peta & Pelacak") },
                        icon = { Icon(Icons.Default.Map, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            val count = uiState.timelineSegments.size
                            Text(if (count > 0) "Timeline ($count)" else "Timeline")
                        },
                        icon = { Icon(Icons.Default.Route, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text("Statistik & Heatmap") },
                        icon = { Icon(Icons.Default.QueryStats, contentDescription = null) }
                    )
                }

                when (selectedTabIndex) {
                    0 -> {
                        // TAB 0: PETA DAN KONTROL TRACKING
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    )
                                ) {
                                    OsmMapView(
                                        modifier = Modifier.fillMaxSize(),
                                        points = uiState.activePoints,
                                        stayPoints = uiState.stayPoints,
                                        latestPoint = uiState.latestPoint,
                                        isTrackingActive = uiState.isServiceRunning && !uiState.isServicePaused,
                                        showHeatmap = uiState.showStatsHeatmap,
                                        onToggleHeatmap = { viewModel.toggleStatsHeatmap() }
                                    )
                                }
                            }

                        item {
                            TrackingControlCard(
                                isServiceRunning = uiState.isServiceRunning,
                                isServicePaused = uiState.isServicePaused,
                                motionState = uiState.motionState,
                                jitterCount = uiState.jitterCount,
                                activeSession = uiState.activeSession,
                                latestPoint = uiState.latestPoint,
                                pointCount = uiState.totalPointCount,
                                onStartClick = {
                                    if (hasLocationPermission(context)) {
                                        viewModel.startTracking(context)
                                    } else {
                                        permissionLauncher.launch(requiredPermissions)
                                    }
                                },
                                onPauseClick = { viewModel.pauseTracking(context) },
                                onResumeClick = { viewModel.resumeTracking(context) },
                                onStopClick = { viewModel.stopTracking(context) },
                                onToggleMotionClick = { viewModel.toggleSimulatedMotionState(context) },
                                onSimulateJitterClick = { viewModel.insertSimulatedJitterPoint() }
                            )
                        }

                        item {
                            Text(
                                text = "Riwayat Sesi Tersimpan (${uiState.sessions.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (uiState.sessions.isEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Text(
                                        text = "Belum ada rekaman rute GPS tersimpan. Klik Mulai Pelacakan untuk mencatat jejak rute.",
                                        modifier = Modifier.padding(20.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        } else {
                            items(
                                items = uiState.sessions,
                                key = { it.id }
                            ) { session ->
                                SessionHistoryCard(
                                    session = session,
                                    onDelete = { viewModel.deleteSession(session.id) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 1: SMART STAY-POINT CLUSTERING TIMELINE DENGAN ON-DEMAND GEOCODING
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            val unresolvedCount = uiState.stayPoints.count { it.resolvedAddress == null }
                            TimelineHeaderCard(
                                stayPointCount = uiState.stayPoints.size,
                                totalPointCount = uiState.totalPointCount,
                                unresolvedCount = unresolvedCount,
                                isBatchGeocoding = uiState.isBatchGeocoding,
                                onGenerateDemo = {
                                    viewModel.generateSimulatedTimelineSession()
                                    Toast.makeText(context, "Sesi simulasi perjalanan & singgah dibuat!", Toast.LENGTH_SHORT).show()
                                },
                                onRecluster = {
                                    viewModel.extractStayPointsNow(thresholdMinutes = 5)
                                    Toast.makeText(context, "Stay points diekstrak ulang!", Toast.LENGTH_SHORT).show()
                                },
                                onResolveAllAddresses = {
                                    viewModel.resolveAllUnresolvedStayPoints()
                                },
                                onExportClick = {
                                    viewModel.openExportDialog()
                                }
                            )
                        }

                        if (uiState.timelineSegments.isEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Belum Ada Segmen Timeline",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Mulai pelacakan atau tekan tombol 'Simulasi Demo' di atas untuk melihat bagaimana ratusan titik GPS dikelompokkan dan diterjemahkan menjadi nama jalan/tempat.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = uiState.timelineSegments
                            ) { index, segment ->
                                val isResolving = if (segment is TimelineSegment.Stay) {
                                    uiState.resolvingStayPointIds.contains(segment.stayPoint.id)
                                } else {
                                    false
                                }

                                TimelineSegmentItem(
                                    segment = segment,
                                    isResolvingAddress = isResolving,
                                    onResolveAddress = {
                                        if (segment is TimelineSegment.Stay) {
                                            viewModel.resolveAddressForStayPoint(
                                                stayPointId = segment.stayPoint.id,
                                                latitude = segment.stayPoint.latitude,
                                                longitude = segment.stayPoint.longitude
                                            )
                                        }
                                    },
                                    isLastItem = index == uiState.timelineSegments.size - 1
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
                2 -> {
                    // TAB 2: ANALITIK STATISTIK & HEATMAP KEPADATAN
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "Analitik Jarak & Heatmap",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Kalkulasi jarak tempuh matematis rumus Haversine dan visualisasi lapisan kepadatan panas spasial dari rekaman sensor GPS.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TimeRangeSelector(
                                        selectedRange = uiState.selectedStatsRange,
                                        onRangeSelected = { viewModel.setSelectedStatsRange(it) }
                                    )
                                }
                            }
                        }

                        item {
                            StatsMetricsGrid(stats = uiState.statsSummary)
                        }

                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                OsmMapView(
                                    modifier = Modifier.fillMaxSize(),
                                    points = uiState.statsPoints,
                                    stayPoints = uiState.statsStayPoints,
                                    showHeatmap = uiState.showStatsHeatmap,
                                    onToggleHeatmap = { viewModel.toggleStatsHeatmap() }
                                )
                            }
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Whatshot,
                                        contentDescription = null,
                                        tint = Color(0xFFFF3D00),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Lapisan Heatmap Dinamis",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Ketuk tombol api di pojok kanan bawah peta untuk beralih antara lapisan kepadatan gradien panas (Heatmap) dan garis rute perjalanan (Polyline).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

    // Modal Dialog Ekspor Data GIS (GPX / KML / GeoJSON)
    if (uiState.exportDialogState.isVisible) {
        ExportDialog(
            sessionTitle = uiState.activeSession?.title ?: "Jejak TrackR",
            totalPoints = uiState.totalPointCount,
            stayPointsCount = uiState.stayPoints.size,
            selectedFormat = uiState.exportDialogState.selectedFormat,
            includeStayPoints = uiState.exportDialogState.includeStayPoints,
            filterJitter = uiState.exportDialogState.filterJitter,
            suggestedFilename = viewModel.getExportFilename(),
            onFormatSelected = { viewModel.setExportFormat(it) },
            onIncludeStayPointsChanged = { viewModel.setExportIncludeStayPoints(it) },
            onFilterJitterChanged = { viewModel.setExportFilterJitter(it) },
            onSaveToFileClick = {
                createDocumentLauncher.launch(viewModel.getExportFilename())
            },
            onShareClick = {
                val shareIntent = viewModel.shareExport(context)
                if (shareIntent != null) {
                    context.startActivity(Intent.createChooser(shareIntent, "Bagikan Berkas Jejak TrackR"))
                    viewModel.closeExportDialog()
                } else {
                    Toast.makeText(context, "Gagal menyiapkan berkas ekspor untuk dibagikan", Toast.LENGTH_SHORT).show()
                }
            },
            onDismissRequest = { viewModel.closeExportDialog() }
        )
    }
}

@Composable
private fun TimelineHeaderCard(
    stayPointCount: Int,
    totalPointCount: Int,
    unresolvedCount: Int,
    isBatchGeocoding: Boolean,
    onGenerateDemo: () -> Unit,
    onRecluster: () -> Unit,
    onResolveAllAddresses: () -> Unit,
    onExportClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Smart Stay-Point Clustering & Geocoding",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Algoritma Li et al. mendeteksi $stayPointCount titik singgah dari $totalPointCount total titik GPS. Alamat diterjemahkan on-demand dan disimpan di Room.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGenerateDemo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.AutoGraph, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simulasi Demo")
                }

                OutlinedButton(
                    onClick = onRecluster,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ekstrak Ulang")
                }
            }

            if (unresolvedCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onResolveAllAddresses,
                    enabled = !isBatchGeocoding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isBatchGeocoding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onTertiary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Menerjemahkan Alamat...")
                    } else {
                        Icon(imageVector = Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Terjemahkan Semua Alamat ($unresolvedCount)")
                    }
                }
            }

            // Tombol Pintas Ekspor Riwayat Jejak GIS
            if (totalPointCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onExportClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ekspor Jejak GIS (GPX / KML / GeoJSON)")
                }
            }
        }
    }
}

@Composable
private fun TrackingControlCard(
    isServiceRunning: Boolean,
    isServicePaused: Boolean,
    motionState: MotionState,
    jitterCount: Int,
    activeSession: TrackingSessionEntity?,
    latestPoint: LocationPointEntity?,
    pointCount: Int,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit,
    onToggleMotionClick: () -> Unit,
    onSimulateJitterClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isServiceRunning) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Baris Status & Adaptive Polling Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isServiceRunning) {
                        activeSession?.title ?: "Pelacakan Aktif"
                    } else {
                        "Pelacak Lokasi Siap"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isServiceRunning) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (isServiceRunning) {
                    val statusText = if (isServicePaused) "DIJEDA" else "MEREKAM"
                    val statusColor = if (isServicePaused) Color(0xFFF57C00) else Color(0xFF2E7D32)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(statusColor)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Badge Adaptive Polling (Bergerak vs Diam)
            if (isServiceRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isMoving = motionState == MotionState.MOVING
                    val badgeColor = if (isMoving) Color(0xFF1B5E20) else Color(0xFF0D47A1)
                    val badgeIcon = if (isMoving) Icons.AutoMirrored.Filled.DirectionsWalk else Icons.Default.BatterySaver
                    val badgeText = if (isMoving) "BERGERAK (Polling 5s)" else "DIAM / IDLE (Polling 60s)"

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }

                    if (jitterCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$jitterCount Jitter Ditolak",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Metrik Real-time saat Service Berjalan
            AnimatedVisibility(visible = isServiceRunning && activeSession != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricItem(
                            icon = Icons.Default.MyLocation,
                            label = "Titik GPS Valid",
                            value = "$pointCount titik",
                            modifier = Modifier.weight(1f)
                        )
                        MetricItem(
                            icon = Icons.Default.GpsFixed,
                            label = "Jarak Tempuh",
                            value = LocationUtils.formatDistance(activeSession?.totalDistanceMeters ?: 0.0),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val speedKmh = ((latestPoint?.speed ?: 0f) * 3.6f)
                        MetricItem(
                            icon = Icons.Default.Speed,
                            label = "Kecepatan",
                            value = String.format(java.util.Locale.getDefault(), "%.1f km/h", speedKmh),
                            modifier = Modifier.weight(1f)
                        )
                        MetricItem(
                            icon = Icons.Default.Timer,
                            label = "Akurasi GPS",
                            value = String.format(java.util.Locale.getDefault(), "±%.1f m", latestPoint?.accuracy ?: 0f),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (latestPoint != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "Lat: ${String.format("%.6f", latestPoint.latitude)}, Lng: ${String.format("%.6f", latestPoint.longitude)}",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Baris Tombol Uji Coba Pengujian Fitur
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onToggleMotionClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (motionState == MotionState.MOVING) "Simulasi Diam" else "Simulasi Bergerak",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        OutlinedButton(
                            onClick = onSimulateJitterClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Tes Jitter (+1)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Tombol Aksi Kontrol
            if (!isServiceRunning) {
                Text(
                    text = "Tekan tombol di bawah untuk menyalakan Foreground Service dan mencatat jejak rute di atas peta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onStartClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mulai Pelacakan GPS")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isServicePaused) {
                        OutlinedButton(
                            onClick = onPauseClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Jeda")
                        }
                    } else {
                        Button(
                            onClick = onResumeClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lanjutkan")
                        }
                    }

                    Button(
                        onClick = onStopClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Selesai")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(
    session: TrackingSessionEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = session.title ?: "Sesi #${session.id}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    StatusBadge(status = session.status)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = DateTimeUtils.formatFullDateTime(session.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Total ${session.pointCount} titik • Jarak: ${LocationUtils.formatDistance(session.totalDistanceMeters)} • Durasi: ${DateTimeUtils.formatDuration(session.totalDurationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus Sesi",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bgColor, textColor) = when (status) {
        TrackingStatus.ACTIVE.name -> Color(0xFF2E7D32) to Color.White
        TrackingStatus.PAUSED.name -> Color(0xFFF57C00) to Color.White
        else -> MaterialTheme.colorScheme.outlineVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

private fun hasLocationPermission(context: Context): Boolean {
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

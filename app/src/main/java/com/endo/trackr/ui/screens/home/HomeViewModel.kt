package com.endo.trackr.ui.screens.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.core.util.LocationUtils
import com.endo.trackr.core.util.StatsCalculator
import com.endo.trackr.core.util.StatsTimeRange
import com.endo.trackr.core.util.StayPointDetector
import com.endo.trackr.core.util.TimelineSegment
import com.endo.trackr.core.util.TravelStatsSummary
import com.endo.trackr.data.exporter.DataExporter
import com.endo.trackr.data.exporter.ExportFormat
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity
import com.endo.trackr.data.local.entity.TrackingSessionEntity
import com.endo.trackr.data.repository.GeocodingRepository
import com.endo.trackr.data.repository.LocationRepository
import com.endo.trackr.service.LocationTrackingService
import com.endo.trackr.service.motion.MotionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State dialog ekspor data GIS.
 */
data class ExportDialogState(
    val isVisible: Boolean = false,
    val selectedFormat: ExportFormat = ExportFormat.GPX,
    val includeStayPoints: Boolean = true,
    val filterJitter: Boolean = true
)

/**
 * State UI untuk layar utama TrackR.
 */
data class HomeUiState(
    val activeSession: TrackingSessionEntity? = null,
    val sessions: List<TrackingSessionEntity> = emptyList(),
    val activePoints: List<LocationPointEntity> = emptyList(),
    val stayPoints: List<StayPointEntity> = emptyList(),
    val timelineSegments: List<TimelineSegment> = emptyList(),
    val latestPoint: LocationPointEntity? = null,
    val totalPointCount: Int = 0,
    val isServiceRunning: Boolean = false,
    val isServicePaused: Boolean = false,
    val motionState: MotionState = MotionState.MOVING,
    val jitterCount: Int = 0,
    val resolvingStayPointIds: Set<Long> = emptySet(),
    val isBatchGeocoding: Boolean = false,
    val exportDialogState: ExportDialogState = ExportDialogState(),
    val selectedStatsRange: StatsTimeRange = StatsTimeRange.ALL_TIME,
    val statsSummary: TravelStatsSummary = TravelStatsSummary(),
    val statsPoints: List<LocationPointEntity> = emptyList(),
    val statsStayPoints: List<StayPointEntity> = emptyList(),
    val showStatsHeatmap: Boolean = true,
    val isLoading: Boolean = false
)

/**
 * ViewModel untuk Dashboard utama, pengontrol Foreground Service,
 * visualisasi peta, Smart Stay-Point Clustering Timeline, On-Demand Reverse Geocoding,
 * Ekspor Data GIS, dan Analitik Heatmap & Statistik Jarak (Haversine).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val locationRepository: LocationRepository,
    private val geocodingRepository: GeocodingRepository
) : ViewModel() {

    private val activeSessionFlow = locationRepository.getActiveSession()
    private val allSessionsFlow = locationRepository.getAllSessions()

    private val activePointsFlow = activeSessionFlow.flatMapLatest { activeSession ->
        if (activeSession != null) {
            locationRepository.getPointsForSession(activeSession.id)
        } else {
            flowOf(emptyList())
        }
    }

    private val stayPointsFlow = activeSessionFlow.flatMapLatest { activeSession ->
        if (activeSession != null) {
            locationRepository.getStayPointsForSession(activeSession.id)
        } else {
            flowOf(emptyList())
        }
    }

    private val allValidPointsFlow = locationRepository.getAllValidPoints()
    private val allStayPointsFlow = locationRepository.getAllStayPoints()

    private val _resolvingStayPointIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isBatchGeocoding = MutableStateFlow(false)
    private val _exportDialogState = MutableStateFlow(ExportDialogState())
    private val _selectedStatsRange = MutableStateFlow(StatsTimeRange.ALL_TIME)
    private val _showStatsHeatmap = MutableStateFlow(true)

    val uiState: StateFlow<HomeUiState> = combine(
        combine(activeSessionFlow, allSessionsFlow, activePointsFlow, stayPointsFlow) { activeSession, sessions, activePoints, stayPoints ->
            Quintuple(activeSession, sessions, activePoints, stayPoints, Unit)
        },
        combine(
            LocationTrackingService.isTrackingRunning,
            LocationTrackingService.isTrackingPaused,
            LocationTrackingService.currentMotionState,
            LocationTrackingService.jitterCount
        ) { isRunning, isPaused, motionState, jitterCount ->
            Quad(isRunning, isPaused, motionState, jitterCount)
        },
        combine(_resolvingStayPointIds, _isBatchGeocoding, _exportDialogState) { resolvingIds, isBatch, exportState ->
            Triple(resolvingIds, isBatch, exportState)
        },
        combine(allValidPointsFlow, allStayPointsFlow, _selectedStatsRange, _showStatsHeatmap) { allValid, allStay, statsRange, showHeatmap ->
            Quad(allValid, allStay, statsRange, showHeatmap)
        }
    ) { (activeSession, sessions, activePoints, stayPoints), (isRunning, isPaused, motionState, jitterCount), (resolvingIds, isBatch, exportState), (allValid, allStay, statsRange, showHeatmap) ->
        val segments = StayPointDetector.buildTimelineSegments(activePoints, stayPoints)

        val statsThreshold = StatsCalculator.getStartTimestampForRange(statsRange)
        val filteredStatsPoints = allValid.filter { it.timestamp >= statsThreshold }
        val filteredStatsStayPoints = allStay.filter { it.arrivalTime >= statsThreshold }

        val statsSummary = StatsCalculator.calculate(
            range = statsRange,
            sessions = sessions,
            points = allValid,
            stayPoints = allStay
        )

        HomeUiState(
            activeSession = activeSession,
            sessions = sessions,
            activePoints = activePoints,
            stayPoints = stayPoints,
            timelineSegments = segments,
            latestPoint = activePoints.lastOrNull(),
            totalPointCount = activePoints.count { !it.isJitter },
            isServiceRunning = isRunning,
            isServicePaused = isPaused,
            motionState = motionState,
            jitterCount = jitterCount,
            resolvingStayPointIds = resolvingIds,
            isBatchGeocoding = isBatch,
            exportDialogState = exportState,
            selectedStatsRange = statsRange,
            statsSummary = statsSummary,
            statsPoints = filteredStatsPoints,
            statsStayPoints = filteredStatsStayPoints,
            showStatsHeatmap = showHeatmap,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun startTracking(context: Context, customTitle: String? = null) {
        viewModelScope.launch {
            val existingActive = locationRepository.getActiveSessionSync()
            val targetSessionId = if (existingActive != null) {
                existingActive.id
            } else {
                val title = customTitle ?: "Jejak ${DateTimeUtils.formatFullDateTime(System.currentTimeMillis())}"
                locationRepository.startNewSession(title)
            }
            LocationTrackingService.startService(context, targetSessionId)
        }
    }

    fun pauseTracking(context: Context) {
        LocationTrackingService.pauseService(context)
    }

    fun resumeTracking(context: Context) {
        LocationTrackingService.resumeService(context)
    }

    fun stopTracking(context: Context) {
        LocationTrackingService.stopService(context)
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            locationRepository.deleteSession(sessionId)
        }
    }

    fun toggleSimulatedMotionState(context: Context) {
        val nextState = if (uiState.value.motionState == MotionState.MOVING) {
            MotionState.STATIONARY
        } else {
            MotionState.MOVING
        }
        LocationTrackingService.setSimulatedMotionState(context, nextState)
    }

    fun insertSimulatedJitterPoint() {
        val currentSession = uiState.value.activeSession ?: return
        viewModelScope.launch {
            val jitterPoint = LocationPointEntity(
                sessionId = currentSession.id,
                latitude = -6.175392 + 0.05,
                longitude = 106.827153 + 0.05,
                accuracy = 65.0f,
                speed = 80.0f,
                timestamp = System.currentTimeMillis(),
                isJitter = true
            )
            locationRepository.insertLocationPoint(jitterPoint)
        }
    }

    fun extractStayPointsNow(thresholdMinutes: Int = 10) {
        val currentSession = uiState.value.activeSession ?: return
        viewModelScope.launch {
            locationRepository.recalculateStayPoints(
                sessionId = currentSession.id,
                timeThresholdMillis = thresholdMinutes * 60 * 1000L
            )
        }
    }

    /**
     * Menerjemahkan koordinat satu titik singgah menjadi nama jalan/tempat secara On-Demand
     * dan menyimpannya ke cache basis data Room.
     */
    fun resolveAddressForStayPoint(stayPointId: Long, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _resolvingStayPointIds.value = _resolvingStayPointIds.value + stayPointId
            val address = geocodingRepository.reverseGeocode(latitude, longitude)
            if (address != null) {
                locationRepository.updateStayPointAddress(stayPointId, address)
            }
            _resolvingStayPointIds.value = _resolvingStayPointIds.value - stayPointId
        }
    }

    /**
     * Menerjemahkan seluruh titik singgah yang belum memiliki nama alamat secara batch.
     */
    fun resolveAllUnresolvedStayPoints() {
        val unresolved = uiState.value.stayPoints.filter { it.resolvedAddress == null }
        if (unresolved.isEmpty()) return

        viewModelScope.launch {
            _isBatchGeocoding.value = true
            for (stay in unresolved) {
                _resolvingStayPointIds.value = _resolvingStayPointIds.value + stay.id
                val address = geocodingRepository.reverseGeocode(stay.latitude, stay.longitude)
                if (address != null) {
                    locationRepository.updateStayPointAddress(stay.id, address)
                }
                _resolvingStayPointIds.value = _resolvingStayPointIds.value - stay.id
            }
            _isBatchGeocoding.value = false
        }
    }

    // ==========================================
    // METODE OPERASI EKSPOR DATA GIS (MODUL 7)
    // ==========================================

    fun openExportDialog() {
        _exportDialogState.value = _exportDialogState.value.copy(isVisible = true)
    }

    fun closeExportDialog() {
        _exportDialogState.value = _exportDialogState.value.copy(isVisible = false)
    }

    fun setExportFormat(format: ExportFormat) {
        _exportDialogState.value = _exportDialogState.value.copy(selectedFormat = format)
    }

    fun setExportIncludeStayPoints(include: Boolean) {
        _exportDialogState.value = _exportDialogState.value.copy(includeStayPoints = include)
    }

    fun setExportFilterJitter(filter: Boolean) {
        _exportDialogState.value = _exportDialogState.value.copy(filterJitter = filter)
    }

    fun getExportFilename(): String {
        val currentSessionTitle = uiState.value.activeSession?.title ?: "Jejak"
        val format = _exportDialogState.value.selectedFormat
        return DataExporter.generateFilename(currentSessionTitle, format)
    }

    fun generateExportContent(): String {
        val currentSession = uiState.value.activeSession
        val sessionTitle = currentSession?.title ?: "Jejak TrackR"
        val state = _exportDialogState.value

        return DataExporter.generateDocument(
            format = state.selectedFormat,
            sessionTitle = sessionTitle,
            points = uiState.value.activePoints,
            stayPoints = uiState.value.stayPoints,
            includeStayPoints = state.includeStayPoints,
            filterJitter = state.filterJitter
        )
    }

    fun saveExportToUri(context: Context, destinationUri: Uri): Boolean {
        val content = generateExportContent()
        return DataExporter.writeContentToUri(context, destinationUri, content)
    }

    fun shareExport(context: Context): Intent? {
        val filename = getExportFilename()
        val content = generateExportContent()
        val format = _exportDialogState.value.selectedFormat
        return DataExporter.createShareIntent(context, filename, content, format)
    }

    // ==========================================
    // METODE OPERASI STATISTIK & HEATMAP (MODUL 8)
    // ==========================================

    fun setSelectedStatsRange(range: StatsTimeRange) {
        _selectedStatsRange.value = range
    }

    fun toggleStatsHeatmap() {
        _showStatsHeatmap.value = !_showStatsHeatmap.value
    }

    /**
     * Menghasilkan data sesi simulasi harian realistis:
     * - Titik Singgah 1 (Monas): Singgah 25 menit (30 titik cluster)
     * - Perjalanan Bergerak: 2.8 km menuju Bundaran HI
     * - Titik Singgah 2 (Bundaran HI / Plaza): Singgah 40 menit (45 titik cluster)
     */
    fun generateSimulatedTimelineSession() {
        viewModelScope.launch {
            val sessionId = locationRepository.startNewSession("Simulasi Perjalanan & Singgah")
            val points = mutableListOf<LocationPointEntity>()
            val now = System.currentTimeMillis()
            var timeCursor = now - (90 * 60 * 1000L) // 1.5 jam yang lalu

            // 1. Cluster Singgah #1 (Monas: -6.175392, 106.827153) - 25 menit
            val monasLat = -6.175392
            val monasLng = 106.827153
            for (i in 0 until 25) {
                points.add(
                    LocationPointEntity(
                        sessionId = sessionId,
                        latitude = monasLat + (Math.random() - 0.5) * 0.0001,
                        longitude = monasLng + (Math.random() - 0.5) * 0.0001,
                        accuracy = 4.0f,
                        speed = 0.2f,
                        timestamp = timeCursor
                    )
                )
                timeCursor += 60 * 1000L
            }

            // 2. Perjalanan Bergerak dari Monas ke Bundaran HI (~2.8 km, ~20 menit)
            val hiLat = -6.195150
            val hiLng = 106.823050
            val moveSteps = 20
            for (i in 1..moveSteps) {
                val fraction = i.toDouble() / moveSteps
                val lat = monasLat + fraction * (hiLat - monasLat) + (Math.random() - 0.5) * 0.00005
                val lng = monasLng + fraction * (hiLng - monasLng) + (Math.random() - 0.5) * 0.00005
                points.add(
                    LocationPointEntity(
                        sessionId = sessionId,
                        latitude = lat,
                        longitude = lng,
                        accuracy = 6.0f,
                        speed = 8.5f,
                        timestamp = timeCursor
                    )
                )
                timeCursor += 60 * 1000L
            }

            // 3. Cluster Singgah #2 (Bundaran HI): Singgah 35 menit
            for (i in 0 until 35) {
                points.add(
                    LocationPointEntity(
                        sessionId = sessionId,
                        latitude = hiLat + (Math.random() - 0.5) * 0.0001,
                        longitude = hiLng + (Math.random() - 0.5) * 0.0001,
                        accuracy = 5.0f,
                        speed = 0.1f,
                        timestamp = timeCursor
                    )
                )
                timeCursor += 60 * 1000L
            }

            // Simpan seluruh titik ke database
            locationRepository.insertLocationPoints(points)

            // Hitung total jarak
            var totalDistance = 0.0
            for (k in 0 until points.size - 1) {
                totalDistance += LocationUtils.calculateHaversineDistance(
                    points[k].latitude, points[k].longitude,
                    points[k + 1].latitude, points[k + 1].longitude
                )
            }

            // Update sesi
            val session = locationRepository.getSessionByIdSync(sessionId)
            if (session != null) {
                locationRepository.updateSession(
                    session.copy(
                        pointCount = points.size,
                        totalDistanceMeters = totalDistance,
                        totalDurationSeconds = (timeCursor - (now - (90 * 60 * 1000L))) / 1000
                    )
                )
            }

            // Jalankan ekstraksi stay points (threshold 10 menit)
            locationRepository.recalculateStayPoints(sessionId, 10 * 60 * 1000L)
        }
    }
}

/**
 * Data holder helper untuk de-structuring 4 elemen.
 */
private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

/**
 * Data holder helper untuk de-structuring 5 elemen.
 */
private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

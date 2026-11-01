package com.endo.trackr.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.core.util.LocationUtils
import com.endo.trackr.core.util.StayPointDetector
import com.endo.trackr.core.util.TimelineSegment
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.StayPointEntity
import com.endo.trackr.data.local.entity.TrackingSessionEntity
import com.endo.trackr.data.repository.LocationRepository
import com.endo.trackr.service.LocationTrackingService
import com.endo.trackr.service.motion.MotionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val isLoading: Boolean = false
)

/**
 * ViewModel untuk Dashboard utama, pengontrol Foreground Service,
 * visualisasi peta, dan Smart Stay-Point Clustering Timeline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val locationRepository: LocationRepository
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
        }
    ) { (activeSession, sessions, activePoints, stayPoints), (isRunning, isPaused, motionState, jitterCount) ->
        val segments = StayPointDetector.buildTimelineSegments(activePoints, stayPoints)

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

    /**
     * Memicu kalkulasi Stay-Point Clustering untuk sesi saat ini secara manual.
     */
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
                        latitude = monasLat + (Math.random() - 0.5) * 0.0001, // Jitter mikro ~10 meter
                        longitude = monasLng + (Math.random() - 0.5) * 0.0001,
                        accuracy = 4.0f,
                        speed = 0.2f,
                        timestamp = timeCursor
                    )
                )
                timeCursor += 60 * 1000L // tiap 1 menit
            }

            // 2. Perjalanan Bergerak Monas -> Bundaran HI (~2.8 km) - 20 menit
            val hiLat = -6.195000
            val hiLng = 106.823000
            val steps = 20
            for (step in 1..steps) {
                val ratio = step.toDouble() / steps
                val currentLat = monasLat + (hiLat - monasLat) * ratio
                val currentLng = monasLng + (hiLng - monasLng) * ratio
                points.add(
                    LocationPointEntity(
                        sessionId = sessionId,
                        latitude = currentLat,
                        longitude = currentLng,
                        accuracy = 5.0f,
                        speed = 8.5f, // ~30 km/h
                        timestamp = timeCursor
                    )
                )
                timeCursor += 60 * 1000L
            }

            // 3. Cluster Singgah #2 (Bundaran HI) - 35 menit
            for (i in 0 until 35) {
                points.add(
                    LocationPointEntity(
                        sessionId = sessionId,
                        latitude = hiLat + (Math.random() - 0.5) * 0.00012,
                        longitude = hiLng + (Math.random() - 0.5) * 0.00012,
                        accuracy = 4.5f,
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

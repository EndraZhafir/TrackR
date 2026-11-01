package com.endo.trackr.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.data.local.entity.LocationPointEntity
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
 * status gerak Adaptive Polling, dan GPS Jitter Filtering.
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

    val uiState: StateFlow<HomeUiState> = combine(
        combine(activeSessionFlow, allSessionsFlow, activePointsFlow) { activeSession, sessions, activePoints ->
            Triple(activeSession, sessions, activePoints)
        },
        combine(
            LocationTrackingService.isTrackingRunning,
            LocationTrackingService.isTrackingPaused,
            LocationTrackingService.currentMotionState,
            LocationTrackingService.jitterCount
        ) { isRunning, isPaused, motionState, jitterCount ->
            Quad(isRunning, isPaused, motionState, jitterCount)
        }
    ) { (activeSession, sessions, activePoints), (isRunning, isPaused, motionState, jitterCount) ->
        HomeUiState(
            activeSession = activeSession,
            sessions = sessions,
            activePoints = activePoints,
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

    /**
     * Beralih secara manual antara status BERGERAK dan DIAM (STATIONARY)
     * untuk memverifikasi adaptive polling interval di Android Studio Emulator.
     */
    fun toggleSimulatedMotionState(context: Context) {
        val nextState = if (uiState.value.motionState == MotionState.MOVING) {
            MotionState.STATIONARY
        } else {
            MotionState.MOVING
        }
        LocationTrackingService.setSimulatedMotionState(context, nextState)
    }

    /**
     * Memasukkan titik loncatan sinyal liar (akurasi buruk 65m) untuk menguji
     * penolakan filter jitter GPS secara visual.
     */
    fun insertSimulatedJitterPoint() {
        val currentSession = uiState.value.activeSession ?: return
        viewModelScope.launch {
            val jitterPoint = LocationPointEntity(
                sessionId = currentSession.id,
                latitude = -6.175392 + 0.05, // Loncatan ekstrem ~5.5 km
                longitude = 106.827153 + 0.05,
                accuracy = 65.0f, // Akurasi buruk > 30m
                speed = 80.0f,
                timestamp = System.currentTimeMillis(),
                isJitter = true
            )
            locationRepository.insertLocationPoint(jitterPoint)
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

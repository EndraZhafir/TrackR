package com.endo.trackr.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.TrackingSessionEntity
import com.endo.trackr.data.repository.LocationRepository
import com.endo.trackr.service.LocationTrackingService
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
    val isLoading: Boolean = false
)

/**
 * ViewModel untuk Dashboard utama dan pengontrol Foreground Service pelacakan.
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
        activeSessionFlow,
        allSessionsFlow,
        activePointsFlow,
        LocationTrackingService.isTrackingRunning,
        LocationTrackingService.isTrackingPaused
    ) { activeSession, sessions, activePoints, isRunning, isPaused ->
        HomeUiState(
            activeSession = activeSession,
            sessions = sessions,
            activePoints = activePoints,
            latestPoint = activePoints.lastOrNull(),
            totalPointCount = activePoints.size,
            isServiceRunning = isRunning,
            isServicePaused = isPaused,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true)
    )

    /**
     * Memulai Foreground Location Service dan menghubungkan ke Room Database.
     */
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
     * Helper simulasi titik koordinat jika pengguna mengetes tanpa berpindah tempat secara fisik.
     */
    fun insertTestLocationPoint(
        latitude: Double,
        longitude: Double,
        accuracy: Float = 5.0f,
        speed: Float = 1.4f
    ) {
        val currentSession = uiState.value.activeSession ?: return
        viewModelScope.launch {
            val point = LocationPointEntity(
                sessionId = currentSession.id,
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                speed = speed,
                timestamp = System.currentTimeMillis()
            )
            locationRepository.insertLocationPoint(point)

            val updatedPointCount = currentSession.pointCount + 1
            locationRepository.updateSession(
                currentSession.copy(pointCount = updatedPointCount)
            )
        }
    }
}

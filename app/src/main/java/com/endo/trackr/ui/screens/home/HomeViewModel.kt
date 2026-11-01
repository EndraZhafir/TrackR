package com.endo.trackr.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.TrackingSessionEntity
import com.endo.trackr.data.repository.LocationRepository
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
 * State UI untuk layar utama (Home / Setup verification).
 */
data class HomeUiState(
    val activeSession: TrackingSessionEntity? = null,
    val sessions: List<TrackingSessionEntity> = emptyList(),
    val activePoints: List<LocationPointEntity> = emptyList(),
    val totalPointCount: Int = 0,
    val isLoading: Boolean = false
)

/**
 * ViewModel untuk Dashboard utama dan pengujian state Room Database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val locationRepository: LocationRepository
) : ViewModel() {

    // Aliran sesi aktif
    private val activeSessionFlow = locationRepository.getActiveSession()

    // Aliran seluruh sesi riwayat
    private val allSessionsFlow = locationRepository.getAllSessions()

    // Aliran titik koordinat yang terkait dengan sesi aktif saat ini
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
        activePointsFlow
    ) { activeSession, sessions, activePoints ->
        HomeUiState(
            activeSession = activeSession,
            sessions = sessions,
            activePoints = activePoints,
            totalPointCount = activePoints.size,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun startNewSession(title: String? = null) {
        viewModelScope.launch {
            locationRepository.startNewSession(title)
        }
    }

    fun pauseActiveSession() {
        val currentSession = uiState.value.activeSession ?: return
        viewModelScope.launch {
            locationRepository.pauseSession(currentSession.id)
        }
    }

    fun resumeActiveSession() {
        val currentSession = uiState.value.activeSession ?: return
        viewModelScope.launch {
            locationRepository.resumeSession(currentSession.id)
        }
    }

    fun completeActiveSession() {
        val currentSession = uiState.value.activeSession ?: return
        viewModelScope.launch {
            locationRepository.completeSession(currentSession.id)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            locationRepository.deleteSession(sessionId)
        }
    }

    /**
     * Memasukkan titik GPS uji coba untuk memverifikasi Room Database Inspector
     * sebelum Foreground Service nyata diintegrasikan pada Modul 2.
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

            // Update counter pada sesi
            val updatedPointCount = currentSession.pointCount + 1
            locationRepository.updateSession(
                currentSession.copy(pointCount = updatedPointCount)
            )
        }
    }
}

package com.endo.trackr.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.endo.trackr.MainActivity
import com.endo.trackr.TrackRApplication
import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.core.util.GpsJitterFilter
import com.endo.trackr.core.util.LocationUtils
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.repository.LocationRepository
import com.endo.trackr.service.motion.MotionDetector
import com.endo.trackr.service.motion.MotionState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service untuk mencatat pergerakan lokasi GPS di latar belakang.
 * Memenuhi kepatuhan Android 14+ (targetSdk = 37) dan mendukung Adaptive Polling
 * (5s interval saat bergerak, 60s idle saat diam) serta GPS Jitter Filtering.
 * Dilengkapi dengan pemulihan sesi persisten (kebal process death & swipe recent apps),
 * WakeLock manajemen daya, dan Auto-Day-Rollover (pergantian hari otomatis).
 */
class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRepository: LocationRepository
    private lateinit var notificationManager: NotificationManager
    private lateinit var motionDetector: MotionDetector

    private var activeSessionId: Long? = null
    private var lastRecordedLocation: Location? = null
    private var accumulatedDistanceMeters: Double = 0.0
    private var recordedPointCount: Int = 0
    private var filteredJitterCount: Int = 0
    private var wakeLock: PowerManager.WakeLock? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleNewLocation(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LocationTrackingService created")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRepository = (application as TrackRApplication).container.locationRepository
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        motionDetector = MotionDetector(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Android me-restart service dengan intent null setelah memori pulih (START_STICKY recovery)
            Log.d(TAG, "onStartCommand: Service restarted by system with null intent (START_STICKY recovery)")
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isTracking = prefs.getBoolean(KEY_PERSISTED_IS_TRACKING, false)
            val isPaused = prefs.getBoolean(KEY_PERSISTED_IS_PAUSED, false)
            val savedSessionId = prefs.getLong(KEY_PERSISTED_SESSION_ID, -1L)

            if (isTracking && savedSessionId != -1L) {
                Log.d(TAG, "Memulihkan sesi pelacakan aktif dari penyimpanan persisten: $savedSessionId (paused=$isPaused)")
                startTracking(savedSessionId)
                if (isPaused) {
                    pauseTracking()
                }
            } else {
                Log.d(TAG, "Tidak ada sesi pelacakan aktif di penyimpanan persisten, menghentikan service")
                stopSelf()
            }
            return START_STICKY
        }

        val action = intent.action ?: return START_STICKY
        Log.d(TAG, "onStartCommand received action: $action")

        when (action) {
            ACTION_START -> {
                val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                startTracking(if (sessionId != -1L) sessionId else null)
            }
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
            ACTION_SIMULATE_MOTION_STATE -> {
                val targetStateName = intent.getStringExtra(EXTRA_MOTION_STATE_NAME)
                if (targetStateName != null) {
                    val targetState = MotionState.valueOf(targetStateName)
                    motionDetector.forceState(targetState)
                    handleMotionStateChanged(targetState)
                }
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved: Aplikasi di-swipe dari Recent Apps / Task Manager")
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isTracking = prefs.getBoolean(KEY_PERSISTED_IS_TRACKING, false)

        if (isTracking && !_isTrackingPaused.value) {
            Log.d(TAG, "Service sedang aktif melacak rute. Menjaga notifikasi dan proses tetap berjalan di background.")
            val formattedDistance = LocationUtils.formatDistance(accumulatedDistanceMeters)
            val notification = buildNotification(
                title = "TrackR — Merekam Rute (Latar Belakang)",
                contentText = "$recordedPointCount titik • $formattedDistance",
                isPaused = false
            )
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TrackR:LocationTrackingServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L) // 2 jam auto-release safety timeout
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }

    private fun saveSessionState(sessionId: Long, isTracking: Boolean, isPaused: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PERSISTED_SESSION_ID, sessionId)
            .putBoolean(KEY_PERSISTED_IS_TRACKING, isTracking)
            .putBoolean(KEY_PERSISTED_IS_PAUSED, isPaused)
            .apply()
    }

    private fun clearSessionState() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun startTracking(sessionId: Long?) {
        serviceScope.launch {
            acquireWakeLock()
            val now = System.currentTimeMillis()

            val actualSessionId = sessionId ?: run {
                val existingActive = locationRepository.getActiveSessionSync()
                if (existingActive != null) {
                    if (DateTimeUtils.isSameDay(existingActive.startTime, now)) {
                        existingActive.id
                    } else {
                        Log.i(TAG, "Sesi aktif (${existingActive.id}) berasal dari hari kemarin. Menutup dan membuat sesi baru untuk hari ini...")
                        locationRepository.completeSession(existingActive.id)
                        locationRepository.startNewSession("Jejak ${DateTimeUtils.formatFullDateTime(now)}")
                    }
                } else {
                    locationRepository.startNewSession("Jejak ${DateTimeUtils.formatFullDateTime(now)}")
                }
            }

            activeSessionId = actualSessionId
            _currentSessionId.value = actualSessionId
            _isTrackingRunning.value = true
            _isTrackingPaused.value = false
            filteredJitterCount = 0
            _jitterCount.value = 0

            // Simpan status persisten ke SharedPreferences
            saveSessionState(actualSessionId, isTracking = true, isPaused = false)

            // Muat data akumulasi awal jika sesi sudah ada di database Room
            val existingSession = locationRepository.getSessionByIdSync(actualSessionId)
            if (existingSession != null) {
                accumulatedDistanceMeters = existingSession.totalDistanceMeters
                recordedPointCount = existingSession.pointCount
            }

            // Mulai pendeteksi gerak accelerometer
            motionDetector.startListening { newState ->
                handleMotionStateChanged(newState)
            }
            _currentMotionState.value = motionDetector.currentState

            // Tampilkan Foreground Service Notification (Android 14+ compliant)
            val notification = buildNotification(
                title = "TrackR — Merekam Rute (Bergerak)",
                contentText = "Menunggu sinyal GPS...",
                isPaused = false
            )
            startForegroundWithCompliance(notification)

            // Mulai request lokasi
            requestLocationUpdates(motionDetector.currentState)
        }
    }

    private fun handleMotionStateChanged(newState: MotionState) {
        Log.d(TAG, "Motion state changed to: $newState. Adjusting GPS polling interval...")
        _currentMotionState.value = newState

        if (_isTrackingRunning.value && !_isTrackingPaused.value) {
            // Pasang ulang request lokasi dengan parameter interval baru
            requestLocationUpdates(newState)

            // Perbarui notifikasi foreground
            val notifTitle = if (newState == MotionState.MOVING) {
                "TrackR — Merekam Rute (Bergerak)"
            } else {
                "TrackR — Diam (Idle / Hemat Baterai)"
            }
            val formattedDistance = LocationUtils.formatDistance(accumulatedDistanceMeters)
            val notifContent = if (newState == MotionState.MOVING) {
                "$recordedPointCount titik • $formattedDistance"
            } else {
                "$recordedPointCount titik • $formattedDistance (Polling Melambat 60s)"
            }
            val notification = buildNotification(
                title = notifTitle,
                contentText = notifContent,
                isPaused = false
            )
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun pauseTracking() {
        Log.d(TAG, "Pausing location updates")
        releaseWakeLock()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        motionDetector.stopListening()
        _isTrackingPaused.value = true

        val sessionId = activeSessionId
            ?: getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_PERSISTED_SESSION_ID, -1L).takeIf { it != -1L }

        if (sessionId != null) {
            saveSessionState(sessionId, isTracking = true, isPaused = true)
            serviceScope.launch {
                locationRepository.pauseSession(sessionId)
            }
        }

        val notification = buildNotification(
            title = "TrackR — Pelacakan Dijeda",
            contentText = "$recordedPointCount titik • ${LocationUtils.formatDistance(accumulatedDistanceMeters)}",
            isPaused = true
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun resumeTracking() {
        Log.d(TAG, "Resuming location updates")
        acquireWakeLock()
        _isTrackingPaused.value = false

        val sessionId = activeSessionId
            ?: getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_PERSISTED_SESSION_ID, -1L).takeIf { it != -1L }

        if (sessionId != null) {
            saveSessionState(sessionId, isTracking = true, isPaused = false)
            serviceScope.launch {
                locationRepository.resumeSession(sessionId)
            }
        }

        motionDetector.startListening { newState ->
            handleMotionStateChanged(newState)
        }
        _currentMotionState.value = motionDetector.currentState

        val notification = buildNotification(
            title = "TrackR — Merekam Rute",
            contentText = "$recordedPointCount titik • ${LocationUtils.formatDistance(accumulatedDistanceMeters)}",
            isPaused = false
        )
        notificationManager.notify(NOTIFICATION_ID, notification)

        requestLocationUpdates(motionDetector.currentState)
    }

    private fun stopTracking() {
        Log.d(TAG, "Stopping location service")
        releaseWakeLock()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        motionDetector.stopListening()

        val sessionId = activeSessionId
            ?: getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_PERSISTED_SESSION_ID, -1L).takeIf { it != -1L }

        // Bersihkan preferensi persisten
        clearSessionState()

        _isTrackingRunning.value = false
        _isTrackingPaused.value = false
        _currentSessionId.value = null
        _currentMotionState.value = MotionState.MOVING
        _jitterCount.value = 0
        activeSessionId = null
        lastRecordedLocation = null
        accumulatedDistanceMeters = 0.0
        recordedPointCount = 0
        filteredJitterCount = 0

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        // Pastikan completeSession selesai dieksekusi di Room sebelum stopSelf mematikan service
        if (sessionId != null) {
            serviceScope.launch(Dispatchers.IO + NonCancellable) {
                try {
                    locationRepository.completeSession(sessionId)
                    Log.d(TAG, "Sesi $sessionId berhasil ditandai COMPLETED di Room")
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal menandai sesi $sessionId selesai", e)
                } finally {
                    stopSelf()
                }
            }
        } else {
            stopSelf()
        }
    }

    private fun requestLocationUpdates(state: MotionState) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permissions not granted, cannot request updates")
            stopTracking()
            return
        }

        // Hapus callback aktif sebelum mendaftarkan interval baru
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Polling Adaptif:
        // Saat MOVING: Interval 5 detik (5000ms), min 3 detik, PRIORITY_HIGH_ACCURACY
        // Saat STATIONARY: Interval 60 detik (60000ms), min 30 detik, PRIORITY_BALANCED_POWER_ACCURACY
        val (intervalMs, minIntervalMs, priority) = if (state == MotionState.MOVING) {
            Triple(5000L, 3000L, Priority.PRIORITY_HIGH_ACCURACY)
        } else {
            Triple(60000L, 30000L, Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        }

        val locationRequest = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(minIntervalMs)
            .setMinUpdateDistanceMeters(0f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Requested location updates with interval: ${intervalMs}ms, priority: $priority")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while requesting location updates", e)
        }
    }

    private fun handleNewLocation(location: Location) {
        val now = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()

        serviceScope.launch {
            // Pulihkan sessionId dari memory atau preferensi persisten
            var currentSessionId = activeSessionId
                ?: getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getLong(KEY_PERSISTED_SESSION_ID, -1L).takeIf { it != -1L }

            if (currentSessionId == null) {
                val existingActive = locationRepository.getActiveSessionSync()
                currentSessionId = existingActive?.id ?: locationRepository.startNewSession("Jejak ${DateTimeUtils.formatFullDateTime(now)}")
                activeSessionId = currentSessionId
                _currentSessionId.value = currentSessionId
                saveSessionState(currentSessionId, isTracking = true, isPaused = false)
            }

            val validSessionId = currentSessionId

            // Auto-Day-Rollover (Midnight boundary):
            // Jika tanggal sesi berbeda dengan tanggal titik koordinat saat ini, otomatis selesaikan sesi lama
            val currentSession = locationRepository.getSessionByIdSync(validSessionId)
            val sessionToUseId: Long = if (currentSession != null && !DateTimeUtils.isSameDay(currentSession.startTime, now)) {
                Log.i(TAG, "Tanggal berganti (tengah malam). Menyelesaikan sesi ${currentSession.id} dan membuat sesi baru untuk hari ini...")
                locationRepository.completeSession(currentSession.id)
                val newSessionId = locationRepository.startNewSession("Jejak ${DateTimeUtils.formatFullDateTime(now)}")
                activeSessionId = newSessionId
                _currentSessionId.value = newSessionId
                saveSessionState(newSessionId, isTracking = true, isPaused = false)
                accumulatedDistanceMeters = 0.0
                recordedPointCount = 0
                lastRecordedLocation = null
                newSessionId
            } else {
                validSessionId
            }

            // 1. Evaluasi Jitter GPS
            val isJitter = GpsJitterFilter.isJitter(location, lastRecordedLocation)

            if (isJitter) {
                filteredJitterCount += 1
                _jitterCount.value = filteredJitterCount
                Log.w(
                    TAG,
                    "GPS Jitter detected (Acc: ±${location.accuracy}m). Marked as jitter, skipped from distance."
                )

                // Simpan titik dengan penanda isJitter = true (tidak dihitung dalam akumulasi jarak)
                val jitterPoint = LocationPointEntity(
                    sessionId = sessionToUseId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    accuracy = location.accuracy,
                    speed = location.speed,
                    timestamp = now,
                    isJitter = true
                )
                locationRepository.insertLocationPoint(jitterPoint)
                return@launch
            }

            // 2. Titik Valid — Hitung delta jarak Haversine
            var deltaDistance = 0.0
            val lastLoc = lastRecordedLocation
            if (lastLoc != null) {
                deltaDistance = LocationUtils.calculateHaversineDistance(
                    lat1 = lastLoc.latitude,
                    lon1 = lastLoc.longitude,
                    lat2 = location.latitude,
                    lon2 = location.longitude
                )
            }
            lastRecordedLocation = location
            accumulatedDistanceMeters += deltaDistance
            recordedPointCount += 1

            // Laporkan perpindahan ke MotionDetector
            motionDetector.reportGpsDisplacement(deltaDistance)
            motionDetector.checkStationaryTimeout()

            // Simpan titik valid ke Room Database
            val validPoint = LocationPointEntity(
                sessionId = sessionToUseId,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy = location.accuracy,
                speed = location.speed,
                timestamp = now,
                isJitter = false
            )
            locationRepository.insertLocationPoint(validPoint)

            // Update sesi di basis data
            val sessionInDb = locationRepository.getSessionByIdSync(sessionToUseId)
            if (sessionInDb != null) {
                val duration = ((now - sessionInDb.startTime) / 1000).coerceAtLeast(0)
                locationRepository.updateSession(
                    sessionInDb.copy(
                        pointCount = recordedPointCount,
                        totalDistanceMeters = accumulatedDistanceMeters,
                        totalDurationSeconds = duration
                    )
                )
            }

            // Perbarui teks notifikasi
            val formattedDistance = LocationUtils.formatDistance(accumulatedDistanceMeters)
            val notifTitle = if (motionDetector.currentState == MotionState.MOVING) {
                "TrackR — Merekam Rute (Bergerak)"
            } else {
                "TrackR — Diam (Idle / Hemat Baterai)"
            }
            val updatedNotification = buildNotification(
                title = notifTitle,
                contentText = "$recordedPointCount titik • $formattedDistance",
                isPaused = false
            )
            notificationManager.notify(NOTIFICATION_ID, updatedNotification)
        }
    }

    private fun startForegroundWithCompliance(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        title: String,
        contentText: String,
        isPaused: Boolean
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tombol Pause atau Resume
        val toggleActionIntent = if (isPaused) {
            Intent(this, LocationTrackingService::class.java).apply { action = ACTION_RESUME }
        } else {
            Intent(this, LocationTrackingService::class.java).apply { action = ACTION_PAUSE }
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleActionTitle = if (isPaused) "Lanjutkan" else "Jeda"

        // Tombol Selesai (Stop)
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, toggleActionTitle, togglePendingIntent)
            .addAction(0, "Selesai", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status foreground service perekam jejak GPS"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "LocationTrackingService destroyed")
        releaseWakeLock()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        motionDetector.stopListening()
        serviceScope.cancel()
        _isTrackingRunning.value = false
        _isTrackingPaused.value = false
        _currentSessionId.value = null
        _currentMotionState.value = MotionState.MOVING
        _jitterCount.value = 0
    }

    companion object {
        const val TAG = "TrackRService"
        const val CHANNEL_ID = "trackr_location_channel"
        const val CHANNEL_NAME = "Pelacakan Lokasi Aktif"
        const val NOTIFICATION_ID = 1001

        private const val PREFS_NAME = "trackr_service_prefs"
        private const val KEY_PERSISTED_SESSION_ID = "persisted_session_id"
        private const val KEY_PERSISTED_IS_TRACKING = "persisted_is_tracking"
        private const val KEY_PERSISTED_IS_PAUSED = "persisted_is_paused"

        const val ACTION_START = "com.endo.trackr.action.START"
        const val ACTION_PAUSE = "com.endo.trackr.action.PAUSE"
        const val ACTION_RESUME = "com.endo.trackr.action.RESUME"
        const val ACTION_STOP = "com.endo.trackr.action.STOP"
        const val ACTION_SIMULATE_MOTION_STATE = "com.endo.trackr.action.SIMULATE_MOTION_STATE"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_MOTION_STATE_NAME = "extra_motion_state_name"

        private val _isTrackingRunning = MutableStateFlow(false)
        val isTrackingRunning: StateFlow<Boolean> = _isTrackingRunning.asStateFlow()

        private val _isTrackingPaused = MutableStateFlow(false)
        val isTrackingPaused: StateFlow<Boolean> = _isTrackingPaused.asStateFlow()

        private val _currentSessionId = MutableStateFlow<Long?>(null)
        val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

        private val _currentMotionState = MutableStateFlow(MotionState.MOVING)
        val currentMotionState: StateFlow<MotionState> = _currentMotionState.asStateFlow()

        private val _jitterCount = MutableStateFlow(0)
        val jitterCount: StateFlow<Int> = _jitterCount.asStateFlow()

        fun startService(context: Context, sessionId: Long? = null) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun setSimulatedMotionState(context: Context, state: MotionState) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_SIMULATE_MOTION_STATE
                putExtra(EXTRA_MOTION_STATE_NAME, state.name)
            }
            context.startService(intent)
        }
    }
}

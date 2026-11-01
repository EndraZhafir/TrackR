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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.endo.trackr.MainActivity
import com.endo.trackr.R
import com.endo.trackr.TrackRApplication
import com.endo.trackr.core.util.DateTimeUtils
import com.endo.trackr.core.util.LocationUtils
import com.endo.trackr.data.local.entity.LocationPointEntity
import com.endo.trackr.data.local.entity.TrackingStatus
import com.endo.trackr.data.repository.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service untuk mencatat pergerakan lokasi GPS di latar belakang.
 * Memenuhi kepatuhan Android 14+ (targetSdk = 37) dengan tipe `location`.
 */
class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRepository: LocationRepository
    private lateinit var notificationManager: NotificationManager

    private var activeSessionId: Long? = null
    private var lastRecordedLocation: Location? = null
    private var accumulatedDistanceMeters: Double = 0.0
    private var recordedPointCount: Int = 0

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
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        Log.d(TAG, "onStartCommand received action: $action")

        when (action) {
            ACTION_START -> {
                val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                startTracking(if (sessionId != -1L) sessionId else null)
            }
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
        }

        return START_STICKY
    }

    private fun startTracking(sessionId: Long?) {
        serviceScope.launch {
            val actualSessionId = sessionId ?: run {
                val existingActive = locationRepository.getActiveSessionSync()
                existingActive?.id ?: locationRepository.startNewSession("Sesi Pelacakan Otomatis")
            }

            activeSessionId = actualSessionId
            _currentSessionId.value = actualSessionId
            _isTrackingRunning.value = true
            _isTrackingPaused.value = false

            // Muat data akumulasi awal jika sesi sudah ada
            val existingSession = locationRepository.getSessionByIdSync(actualSessionId)
            if (existingSession != null) {
                accumulatedDistanceMeters = existingSession.totalDistanceMeters
                recordedPointCount = existingSession.pointCount
            }

            // Tampilkan Foreground Service Notification (Android 14+ compliant)
            val notification = buildNotification(
                title = "TrackR — Merekam Rute",
                contentText = "Menunggu sinyal GPS...",
                isPaused = false
            )
            startForegroundWithCompliance(notification)

            // Mulai request lokasi
            requestLocationUpdates()
        }
    }

    private fun pauseTracking() {
        Log.d(TAG, "Pausing location updates")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isTrackingPaused.value = true

        val sessionId = activeSessionId
        if (sessionId != null) {
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
        _isTrackingPaused.value = false

        val sessionId = activeSessionId
        if (sessionId != null) {
            serviceScope.launch {
                locationRepository.resumeSession(sessionId)
            }
        }

        val notification = buildNotification(
            title = "TrackR — Merekam Rute",
            contentText = "$recordedPointCount titik • ${LocationUtils.formatDistance(accumulatedDistanceMeters)}",
            isPaused = false
        )
        notificationManager.notify(NOTIFICATION_ID, notification)

        requestLocationUpdates()
    }

    private fun stopTracking() {
        Log.d(TAG, "Stopping location service")
        fusedLocationClient.removeLocationUpdates(locationCallback)

        val sessionId = activeSessionId
        if (sessionId != null) {
            serviceScope.launch {
                locationRepository.completeSession(sessionId)
            }
        }

        _isTrackingRunning.value = false
        _isTrackingPaused.value = false
        _currentSessionId.value = null
        activeSessionId = null
        lastRecordedLocation = null
        accumulatedDistanceMeters = 0.0
        recordedPointCount = 0

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestLocationUpdates() {
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

        // Interval 5 detik (5000ms), min interval 3 detik
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .setMinUpdateDistanceMeters(0f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates requested successfully (5s interval)")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location updates", e)
        }
    }

    private fun handleNewLocation(location: Location) {
        val sessionId = activeSessionId ?: return

        serviceScope.launch {
            // Hitung delta jarak jika ada titik sebelumnya
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

            // Simpan titik ke Room Database
            val pointEntity = LocationPointEntity(
                sessionId = sessionId,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy = location.accuracy,
                speed = location.speed,
                timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
            )
            locationRepository.insertLocationPoint(pointEntity)

            // Update sesi
            val currentSession = locationRepository.getSessionByIdSync(sessionId)
            if (currentSession != null) {
                val now = System.currentTimeMillis()
                val duration = ((now - currentSession.startTime) / 1000).coerceAtLeast(0)
                locationRepository.updateSession(
                    currentSession.copy(
                        pointCount = recordedPointCount,
                        totalDistanceMeters = accumulatedDistanceMeters,
                        totalDurationSeconds = duration
                    )
                )
            }

            // Perbarui teks notifikasi
            val formattedDistance = LocationUtils.formatDistance(accumulatedDistanceMeters)
            val updatedNotification = buildNotification(
                title = "TrackR — Merekam Rute",
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
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        _isTrackingRunning.value = false
        _isTrackingPaused.value = false
        _currentSessionId.value = null
    }

    companion object {
        const val TAG = "TrackRService"
        const val CHANNEL_ID = "trackr_location_channel"
        const val CHANNEL_NAME = "Pelacakan Lokasi Aktif"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.endo.trackr.action.START"
        const val ACTION_PAUSE = "com.endo.trackr.action.PAUSE"
        const val ACTION_RESUME = "com.endo.trackr.action.RESUME"
        const val ACTION_STOP = "com.endo.trackr.action.STOP"

        const val EXTRA_SESSION_ID = "extra_session_id"

        private val _isTrackingRunning = MutableStateFlow(false)
        val isTrackingRunning: StateFlow<Boolean> = _isTrackingRunning.asStateFlow()

        private val _isTrackingPaused = MutableStateFlow(false)
        val isTrackingPaused: StateFlow<Boolean> = _isTrackingPaused.asStateFlow()

        private val _currentSessionId = MutableStateFlow<Long?>(null)
        val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

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
    }
}

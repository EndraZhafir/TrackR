package com.endo.trackr.service.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Representasi status pergerakan perangkat.
 */
enum class MotionState {
    MOVING,       // Perangkat sedang bergerak (berjalan, bersepeda, naik kendaraan)
    STATIONARY    // Perangkat diam (misal diletakkan di atas meja atau diam di satu tempat)
}

/**
 * Detektor gerak cerdas yang memadukan sensor Accelerometer internal dan
 * data pergeseran GPS untuk menentukan apakah perangkat sedang bergerak atau diam (stationary).
 *
 * Digunakan oleh [LocationTrackingService] untuk beralih antara interval cepat (5 detik)
 * dan interval hemat daya (idle polling) secara otomatis.
 */
class MotionDetector(
    context: Context,
    private val stationaryThresholdMillis: Long = 30 * 1000L // 30 detik (diperbarui dari 5 menit untuk responsivitas)
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var onStateChangedListener: ((MotionState) -> Unit)? = null

    var currentState: MotionState = MotionState.MOVING
        private set

    private var lastMotionTimestamp: Long = System.currentTimeMillis()
    private var lastAccelerationMagnitude: Float = 9.8f
    private val motionThreshold = 1.4f // Ambang deviasi akselerasi dinamis (kebal terhadap getaran tangan ringan saat memegang HP)
    private val recentGpsDisplacements = ArrayDeque<Pair<Long, Double>>()

    fun startListening(listener: (MotionState) -> Unit) {
        onStateChangedListener = listener
        lastMotionTimestamp = System.currentTimeMillis()
        recentGpsDisplacements.clear()
        currentState = MotionState.MOVING

        if (accelerometer != null) {
            // SENSOR_DELAY_NORMAL hemat daya baterai (~200ms per sampel)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Accelerometer motion detector registered")
        } else {
            Log.w(TAG, "Device does not have an Accelerometer sensor, relying solely on GPS displacement")
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        recentGpsDisplacements.clear()
        onStateChangedListener = null
        Log.d(TAG, "Motion detector unregistered")
    }

    /**
     * Dilaporkan oleh Location Service saat koordinat GPS baru diterima.
     * Jika ada perpindahan nyata (> 15 meter), perbarui timestamp pergerakan terakhir.
     * Jika 5-6 titik terakhir berturut-turut berada dalam radius sempit (<= 15m) selama >= 25s,
     * otomatis alihkan ke status STATIONARY bahkan bila accelerometer mendeteksi getaran tangan mikro.
     */
    fun reportGpsDisplacement(displacementMeters: Double) {
        val now = System.currentTimeMillis()
        recentGpsDisplacements.addLast(now to displacementMeters)
        while (recentGpsDisplacements.size > 6) {
            recentGpsDisplacements.removeFirst()
        }

        if (displacementMeters > 15.0) {
            recordActiveMotion("GPS displacement: ${String.format("%.1f", displacementMeters)}m")
        } else if (recentGpsDisplacements.size >= 5) {
            val oldestTime = recentGpsDisplacements.first().first
            val duration = now - oldestTime
            val allClustered = recentGpsDisplacements.all { it.second <= 15.0 }

            if (allClustered && duration >= 25 * 1000L && currentState == MotionState.MOVING) {
                Log.d(TAG, "GPS cluster stationary detected (${recentGpsDisplacements.size} points <= 15m over ${duration / 1000}s). Transitioning to STATIONARY mode.")
                currentState = MotionState.STATIONARY
                onStateChangedListener?.invoke(MotionState.STATIONARY)
            }
        }
    }

    /**
     * Memeriksa apakah perangkat sudah diam melampaui ambang batas waktu (misal 5 menit).
     * Dipanggil secara berkala saat polling GPS berjalan.
     */
    fun checkStationaryTimeout() {
        val now = System.currentTimeMillis()
        val idleDuration = now - lastMotionTimestamp

        if (idleDuration >= stationaryThresholdMillis && currentState == MotionState.MOVING) {
            Log.d(TAG, "Device stationary detected (idle for ${idleDuration / 1000}s). Transitioning to STATIONARY mode.")
            currentState = MotionState.STATIONARY
            onStateChangedListener?.invoke(MotionState.STATIONARY)
        }
    }

    /**
     * Simulasi atau trigger manual untuk pengujian perubahan status gerak di Android Studio.
     */
    fun forceState(state: MotionState) {
        if (currentState != state) {
            currentState = state
            lastMotionTimestamp = if (state == MotionState.MOVING) System.currentTimeMillis() else 0L
            onStateChangedListener?.invoke(state)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val currentMagnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = abs(currentMagnitude - lastAccelerationMagnitude)
        lastAccelerationMagnitude = currentMagnitude

        // Jika terdeteksi gaya akselerasi dinamis melebihi ambang batas
        if (delta > motionThreshold) {
            recordActiveMotion("Accelerometer dynamic delta: $delta")
        }
    }

    private fun recordActiveMotion(reason: String) {
        lastMotionTimestamp = System.currentTimeMillis()
        if (currentState == MotionState.STATIONARY) {
            Log.d(TAG, "Motion resumed ($reason). Transitioning to MOVING mode.")
            currentState = MotionState.MOVING
            onStateChangedListener?.invoke(MotionState.MOVING)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    companion object {
        private const val TAG = "TrackRMotionDetector"
    }
}

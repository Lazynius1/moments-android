package com.moments.android.utilities

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Equivalente de UIDeviceOrientation para tracking por acelerómetro. */
enum class DeviceOrientation {
    PORTRAIT,
    PORTRAIT_UPSIDE_DOWN,
    LANDSCAPE_LEFT,
    LANDSCAPE_RIGHT,
    ;

    val isValidInterfaceOrientation: Boolean
        get() = when (this) {
            PORTRAIT, PORTRAIT_UPSIDE_DOWN, LANDSCAPE_LEFT, LANDSCAPE_RIGHT -> true
        }
}

/**
 * Tracking de orientación vía acelerómetro (port de CMMotionManager / OrientationManager.swift).
 */
class OrientationManager private constructor() : SensorEventListener {
    private val _orientation = MutableStateFlow(DeviceOrientation.PORTRAIT)
    val orientation: StateFlow<DeviceOrientation> = _orientation.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastValidOrientation = DeviceOrientation.PORTRAIT
    private var activeConsumers = 0

    companion object {
        val shared = OrientationManager()

        fun initialize(context: Context) {
            shared.ensureInitialized(context.applicationContext)
        }
    }

    private fun ensureInitialized(context: Context) {
        if (sensorManager != null) return
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    fun startTracking() {
        activeConsumers += 1
        if (activeConsumers != 1) return
        val manager = sensorManager ?: return
        val sensor = accelerometer ?: return
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stopTracking() {
        activeConsumers = maxOf(activeConsumers - 1, 0)
        if (activeConsumers != 0) return
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val acceleration = event?.values ?: return
        if (acceleration.size < 3) return

        var newOrientation = lastValidOrientation
        val threshold = 0.6f
        val x = acceleration[0]
        val y = acceleration[1]
        val z = acceleration[2]

        if (z < -0.85f) {
            // Teléfono casi plano hacia arriba: mantener última orientación válida.
            return
        }

        newOrientation = when {
            kotlin.math.abs(x) > threshold ->
                if (x > 0) DeviceOrientation.LANDSCAPE_RIGHT else DeviceOrientation.LANDSCAPE_LEFT
            kotlin.math.abs(y) > threshold ->
                if (y > 0) DeviceOrientation.PORTRAIT_UPSIDE_DOWN else DeviceOrientation.PORTRAIT
            else -> lastValidOrientation
        }

        if (newOrientation != _orientation.value && newOrientation.isValidInterfaceOrientation) {
            lastValidOrientation = newOrientation
            _orientation.value = newOrientation
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

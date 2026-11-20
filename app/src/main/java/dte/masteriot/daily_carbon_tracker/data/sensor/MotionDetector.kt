package dte.masteriot.daily_carbon_tracker.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dte.masteriot.daily_carbon_tracker.data.model.TransportMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs
import kotlin.math.sqrt

enum class DetectedMotion(
    val mode: TransportMode?,
    val factorId: String?,
    val label: String,
) {
    STATIC(null, null, "Statique"),
    WALKING(TransportMode.WALK, "walk", "Marche"),
    VEHICLE(TransportMode.CAR, "car_thermal", "Voiture"),
}

/**
 * Écoute l'accéléromètre via l'Android Sensor Framework et classifie le mouvement
 * sur une fenêtre glissante (statique, marche ou véhicule).
 */
class MotionDetector(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun observeMotion(): Flow<DetectedMotion> = callbackFlow {
        val sensor = accelerometer
        if (sensor == null) {
            trySend(DetectedMotion.STATIC)
            close()
            return@callbackFlow
        }

        val windowSize = 40
        val netAccelerations = ArrayDeque<Float>(windowSize)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val totalMagnitude = sqrt(x * x + y * y + z * z)
                val dynamicAcc = abs(totalMagnitude - SensorManager.GRAVITY_EARTH)

                if (netAccelerations.size >= windowSize) {
                    netAccelerations.removeFirst()
                }
                netAccelerations.addLast(dynamicAcc)

                if (netAccelerations.size >= 35) {
                    val mean = netAccelerations.average().toFloat()
                    val variance = netAccelerations
                        .map { (it - mean) * (it - mean) }
                        .average()
                        .toFloat()

                    val classification = when {
                        variance >= 4.5f || mean >= 3.5f -> DetectedMotion.VEHICLE
                        variance >= 0.7f || mean >= 1.0f -> DetectedMotion.WALKING
                        else -> DetectedMotion.STATIC
                    }
                    trySend(classification)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
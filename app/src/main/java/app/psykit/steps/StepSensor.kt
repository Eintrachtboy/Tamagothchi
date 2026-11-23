package app.psykit.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Zugriff auf den eingebauten Schrittzähler des Handys (TYPE_STEP_COUNTER). */
class StepSensor(context: Context) {
    private val ctx = context.applicationContext
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val available: Boolean get() = sensor != null

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Dauerhaft zuhören, solange die App offen ist. Gibt eine Funktion zum Abmelden zurück. */
    fun listen(onCounter: (Long) -> Unit): () -> Unit {
        val s = sensor ?: return {}
        if (!hasPermission()) return {}
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { onCounter(e.values[0].toLong()) }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL)
        return { sm.unregisterListener(l) }
    }

    /** Einmal den aktuellen Zählerstand lesen (für den Hintergrund-Worker). */
    suspend fun readOnce(timeoutMs: Long = 10_000): Long? {
        val s = sensor ?: return null
        if (!hasPermission()) return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0].toLong())
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(l) }
            }
        }
    }
}

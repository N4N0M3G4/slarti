package com.georecon.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class Orientation(val headingDeg: Float, val pitchDeg: Float, val rollDeg: Float)

/** One reading of the rotation-vector sensor, as azimuth/pitch/roll in degrees. */
suspend fun readOrientation(ctx: Context): Orientation? = withTimeoutOrNull(1500) {
    val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return@withTimeoutOrNull null
    suspendCancellableCoroutine { cont ->
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                sm.unregisterListener(this)
                val r = FloatArray(9); val o = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(r, e.values)
                SensorManager.getOrientation(r, o)
                val deg = o.map { Math.toDegrees(it.toDouble()).toFloat() }
                if (cont.isActive) cont.resume(Orientation((deg[0] + 360f) % 360f, deg[1], deg[2]))
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        cont.invokeOnCancellation { sm.unregisterListener(l) }
    }
}

/** Current high-accuracy fix. Caller must hold ACCESS_FINE_LOCATION. */
@SuppressLint("MissingPermission")
suspend fun readLocation(ctx: Context): Location? = withTimeoutOrNull(10_000) {
    suspendCancellableCoroutine { cont ->
        LocationServices.getFusedLocationProviderClient(ctx)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }
}

package com.georecon.app.pin

import android.content.Context

/** The one "current pin": set by address search or GPS, kept across restarts. */
data class Pin(val lat: Double, val lon: Double, val label: String, val source: String, val locked: Boolean = true) {
    companion object {
        /** Null for out-of-range or non-finite coordinates. */
        fun of(lat: Double, lon: Double, label: String?, source: String, locked: Boolean = true): Pin? {
            if (!lat.isFinite() || !lon.isFinite() || kotlin.math.abs(lat) > 90 || kotlin.math.abs(lon) > 180) return null
            val l = label?.trim()?.takeIf { it.isNotEmpty() }?.take(120) ?: "%.5f, %.5f".format(lat, lon)
            val s = if (source in setOf("search", "gps", "manual")) source else "manual"
            return Pin(lat, lon, l, s, locked)
        }
    }
}

/** Zoom-lock math for MapLibre zoom levels: one step per tap, clamped. */
object PinZoom {
    const val MIN = 2.0
    const val MAX = 20.0
    fun step(zoom: Double, direction: Int, step: Double = 1.0): Double =
        (if (zoom.isFinite()) zoom + direction.coerceIn(-1, 1) * step else 14.0).coerceIn(MIN, MAX)
}

/** Key-value storage seam so the store is testable without Android. */
interface KeyValue {
    fun get(key: String): String?
    fun put(values: Map<String, String?>)
}

class PinStore(private val kv: KeyValue) {
    fun load(): Pin? = runCatching {
        Pin.of(kv.get("lat")!!.toDouble(), kv.get("lon")!!.toDouble(), kv.get("label"), kv.get("source") ?: "manual", kv.get("locked") != "false")
    }.getOrNull()

    fun save(pin: Pin?) = kv.put(
        if (pin == null) mapOf("lat" to null, "lon" to null, "label" to null, "source" to null, "locked" to null)
        else mapOf("lat" to pin.lat.toString(), "lon" to pin.lon.toString(), "label" to pin.label, "source" to pin.source, "locked" to pin.locked.toString())
    )

    companion object {
        fun prefs(ctx: Context): PinStore {
            val sp = ctx.getSharedPreferences("pin", Context.MODE_PRIVATE)
            return PinStore(object : KeyValue {
                override fun get(key: String) = sp.getString(key, null)
                override fun put(values: Map<String, String?>) {
                    val e = sp.edit()
                    values.forEach { (k, v) -> if (v == null) e.remove(k) else e.putString(k, v) }
                    e.apply()
                }
            })
        }
    }
}

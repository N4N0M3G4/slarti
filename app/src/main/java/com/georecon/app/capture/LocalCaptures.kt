package com.georecon.app.capture

import android.content.Context
import com.georecon.app.data.Capture
import org.json.JSONObject

/** Read this phone's own captures back from their sidecar JSON files. */
object LocalCaptures {
    fun parse(json: String): Capture? = runCatching {
        val o = JSONObject(json)
        fun d(k: String) = if (o.isNull(k)) null else o.optDouble(k).takeIf { !it.isNaN() }
        Capture(
            id = o.getString("id"), type = o.optString("type", "photo"), path = o.optString("file"),
            timestampMs = o.optLong("timestampMs"), lat = d("lat") ?: Double.NaN, lon = d("lon") ?: Double.NaN,
            altM = d("altM"), hAccM = d("hAccM")?.toFloat(), headingDeg = d("headingDeg")?.toFloat(),
            pitchDeg = d("pitchDeg")?.toFloat(), rollDeg = d("rollDeg")?.toFloat(),
        )
    }.getOrNull()

    fun list(ctx: Context): List<Capture> =
        CaptureStore.dir(ctx).listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { parse(it.readText()) }.sortedBy { it.timestampMs }
}

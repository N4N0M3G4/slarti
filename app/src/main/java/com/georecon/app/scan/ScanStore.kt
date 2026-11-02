package com.georecon.app.scan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Per-frame record, matching fossil3d/CAPTURE-SPEC.md section 5. Raw ARCore pose; no conversion. */
data class FrameMeta(
    val frame: Int, val side: Int, val timestampNs: Long, val imageW: Int, val imageH: Int,
    val fx: Float, val fy: Float, val cx: Float, val cy: Float, val sourceW: Int, val sourceH: Int,
    val t: FloatArray, val q: FloatArray, val tracking: String, val sharpness: Double,
    val focusLocked: Boolean = false, val exposureLocked: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("frame", frame).put("side", side).put("timestampNs", timestampNs)
        .put("imageW", imageW).put("imageH", imageH)
        .put("intrinsics", JSONObject().put("fx", fx.toDouble()).put("fy", fy.toDouble()).put("cx", cx.toDouble()).put("cy", cy.toDouble())
            .put("w", imageW).put("h", imageH).put("sourceW", sourceW).put("sourceH", sourceH))
        .put("pose", JSONObject().put("frame", "arcore_world_from_camera")
            .put("t", JSONArray(t.map { it.toDouble() })).put("q", JSONArray(q.map { it.toDouble() })))
        .put("tracking", tracking).put("sharpness", sharpness)
        .put("focusLocked", focusLocked).put("exposureLocked", exposureLocked)
}

data class SideRange(val side: Int, val firstFrame: Int, val lastFrame: Int)

data class SessionMeta(
    val id: String, val createdMs: Long, val device: String, val appVersion: String,
    val lat: Double?, val lon: Double?, val hAccM: Float?, val findId: String?, val label: String,
    val scaleRef: String, val sides: List<SideRange>, val cellsDone: Int, val cellsTotal: Int, val notes: String = "",
) {
    fun toJson(): JSONObject {
        val scale = when (scaleRef) {
            "charuco" -> JSONObject().put("type", "charuco").put("dict", "DICT_4X4_50").put("squaresX", 5).put("squaresY", 7)
                .put("squareMm", 30.0).put("markerMm", 22.0)
            "quarter" -> JSONObject().put("type", "coin").put("name", "US quarter").put("diameterMm", 24.26)
            else -> JSONObject().put("type", "ruler")
        }
        return JSONObject().put("id", id).put("type", "object-scan").put("schema", 1).put("createdMs", createdMs)
            .put("device", device).put("appVersion", appVersion)
            .put("lat", lat ?: JSONObject.NULL).put("lon", lon ?: JSONObject.NULL).put("hAccM", hAccM?.toDouble() ?: JSONObject.NULL)
            .put("findId", findId ?: JSONObject.NULL).put("taxonGuess", label).put("scaleRef", scale)
            .put("sides", JSONArray(sides.map { JSONObject().put("side", it.side).put("firstFrame", it.firstFrame).put("lastFrame", it.lastFrame) }))
            .put("coverage", JSONObject().put("rings", JSONArray(ScanSpec.RINGS_DEG.toList())).put("sectorsDeg", ScanSpec.SECTOR_DEG)
                .put("cellsDone", cellsDone).put("cellsTotal", cellsTotal))
            .put("hints", JSONArray()).put("notes", notes)
    }
}

/** On-disk session: filesDir/scans/<id>/{session.json, frames/NNNNNN.jpg|json, complete}. Frames are written as they arrive. */
class ScanStore(root: File, val id: String) {
    val dir = File(root, id).apply { File(this, "frames").mkdirs() }
    fun frameName(n: Int) = "%06d".format(n)
    fun writeFrame(meta: FrameMeta, jpeg: ByteArray) {
        val base = File(dir, "frames/${frameName(meta.frame)}")
        File(base.path + ".jpg").writeBytes(jpeg)
        File(base.path + ".json").writeText(meta.toJson().toString())
    }
    fun writeSession(s: SessionMeta) = File(dir, "session.json").writeText(s.toJson().toString(1))
    fun markComplete() = File(dir, "complete").writeText(System.currentTimeMillis().toString())

    companion object {
        fun root(ctx: Context) = File(ctx.filesDir, "scans").apply { mkdirs() }
    }
}

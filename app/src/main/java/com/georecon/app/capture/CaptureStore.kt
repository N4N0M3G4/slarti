package com.georecon.app.capture

import android.content.Context
import android.location.Location
import androidx.exifinterface.media.ExifInterface
import com.georecon.app.data.Capture
import java.io.File

/** Local, offline capture storage: filesDir/captures/<id>.jpg + <id>.json */
object CaptureStore {
    fun dir(ctx: Context) = File(ctx.filesDir, "captures").apply { mkdirs() }
    fun newPhotoFile(ctx: Context, id: String) = File(dir(ctx), "$id.jpg")

    /** Stamp EXIF GPS/heading into the JPEG and write the sidecar JSON. */
    fun finalize(file: File, id: String, ts: Long, loc: Location?, o: Orientation?): Capture {
        val c = Capture(
            id = id, type = "photo", path = file.name, timestampMs = ts,
            lat = loc?.latitude ?: Double.NaN, lon = loc?.longitude ?: Double.NaN,
            altM = loc?.takeIf { it.hasAltitude() }?.altitude, hAccM = loc?.accuracy,
            headingDeg = o?.headingDeg, pitchDeg = o?.pitchDeg, rollDeg = o?.rollDeg,
        )
        runCatching {
            val exif = ExifInterface(file)
            loc?.let { exif.setGpsInfo(it) }
            o?.let {
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, "${(it.headingDeg * 100).toInt()}/100")
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M")
            }
            exif.saveAttributes()
        }
        File(file.parentFile, "$id.json").writeText(Sidecar.toJson(c))
        return c
    }
}

package com.georecon.app.capture

import com.georecon.app.data.Capture

/** Sidecar JSON written next to each photo. Pure Kotlin so it is unit-testable. */
object Sidecar {
    private fun n(v: Number?) = v?.toString() ?: "null"
    private fun s(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun toJson(c: Capture): String = buildString {
        append("{")
        append("\"id\":${s(c.id)},\"type\":${s(c.type)},\"file\":${s(c.path)},\"timestampMs\":${c.timestampMs},")
        append("\"lat\":${c.lat},\"lon\":${c.lon},\"altM\":${n(c.altM)},\"hAccM\":${n(c.hAccM)},")
        append("\"headingDeg\":${n(c.headingDeg)},\"pitchDeg\":${n(c.pitchDeg)},\"rollDeg\":${n(c.rollDeg)}")
        append("}")
    }
}

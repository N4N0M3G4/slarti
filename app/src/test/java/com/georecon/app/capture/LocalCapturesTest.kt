package com.georecon.app.capture

import com.georecon.app.data.Capture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCapturesTest {
    @Test fun sidecarRoundTrips() {
        val c = Capture("9ffe9c12", "photo", "9ffe9c12.jpg", 5L, 37.0562967, -122.0008593, null, 26.6f, 60.8f, -30f, 3.9f)
        val back = LocalCaptures.parse(Sidecar.toJson(c))!!
        assertEquals(c.lat, back.lat, 1e-9)
        assertNull(back.altM)
        assertEquals(60.8f, back.headingDeg!!, 1e-4f)
    }

    @Test fun garbageIsSkipped() = assertNull(LocalCaptures.parse("{nope"))
}

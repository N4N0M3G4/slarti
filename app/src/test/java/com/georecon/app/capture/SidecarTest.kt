package com.georecon.app.capture

import com.georecon.app.data.Capture
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SidecarTest {
    @Test fun roundTripsFieldsAndNulls() {
        val c = Capture("a\"1", "photo", "a.jpg", 42L, 37.0566, -122.0013, null, 4.5f, 312.5f, -10f, 2f)
        val j = JSONObject(Sidecar.toJson(c))
        assertEquals("a\"1", j.getString("id"))
        assertEquals(37.0566, j.getDouble("lat"), 1e-9)
        assertTrue(j.isNull("altM"))
        assertEquals(312.5, j.getDouble("headingDeg"), 1e-6)
    }
}

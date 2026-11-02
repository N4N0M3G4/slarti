package com.georecon.app.scan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ScanStoreTest {
    @Test fun frameJsonMatchesSpec() {
        val j = FrameMeta(12, 1, 99L, 1920, 1080, 1500f, 1500f, 960f, 540f, 1920, 1080,
            floatArrayOf(0.1f, 0.2f, 0.3f), floatArrayOf(0f, 0f, 0f, 1f), "TRACKING", 123.4).toJson()
        assertEquals("arcore_world_from_camera", j.getJSONObject("pose").getString("frame"))
        assertEquals(4, j.getJSONObject("pose").getJSONArray("q").length())
        assertEquals(1920, j.getJSONObject("intrinsics").getInt("w"))
        assertEquals("TRACKING", j.getString("tracking"))
    }

    @Test fun sessionJsonAndFolderLayout() {
        val root = Files.createTempDirectory("scan").toFile()
        val store = ScanStore(root, "abc")
        store.writeFrame(FrameMeta(1, 1, 0, 2, 2, 1f, 1f, 1f, 1f, 2, 2, FloatArray(3), floatArrayOf(0f, 0f, 0f, 1f), "TRACKING", 1.0), byteArrayOf(1, 2))
        store.writeSession(SessionMeta("abc", 1, "Pixel 7", "0.1", 37.0, -122.0, 5f, null, "Crassostrea titan", "quarter",
            listOf(SideRange(1, 1, 1)), 3, 36))
        assertTrue(File(root, "abc/frames/000001.jpg").exists())
        assertTrue(File(root, "abc/frames/000001.json").exists())
        val s = JSONObject(File(root, "abc/session.json").readText())
        assertEquals("object-scan", s.getString("type"))
        assertEquals(24.26, s.getJSONObject("scaleRef").getDouble("diameterMm"), 0.0)
        assertTrue(s.isNull("findId"))
    }
}

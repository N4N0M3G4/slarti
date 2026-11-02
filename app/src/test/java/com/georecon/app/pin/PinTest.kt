package com.georecon.app.pin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinTest {
    private class MemKV : KeyValue {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(values: Map<String, String?>) = values.forEach { (k, v) -> if (v == null) m.remove(k) else m[k] = v }
    }

    @Test fun rejectsBadCoordinatesAndFillsLabel() {
        assertNull(Pin.of(91.0, 0.0, "x", "gps"))
        assertNull(Pin.of(Double.NaN, 0.0, "x", "gps"))
        assertEquals("37.05660, -122.00130", Pin.of(37.0566, -122.0013, " ", "search")!!.label)
        assertEquals("manual", Pin.of(1.0, 1.0, "a", "weird")!!.source)
    }

    @Test fun storeRoundTripsAndClears() {
        val kv = MemKV(); val store = PinStore(kv)
        store.save(Pin.of(37.0566, -122.0013, "Belair Dr", "search", locked = false))
        val p = PinStore(kv).load()!!
        assertEquals("Belair Dr", p.label)
        assertEquals(false, p.locked)
        store.save(null)
        assertNull(store.load())
    }

    @Test fun zoomStepsAndClamps() {
        assertEquals(15.0, PinZoom.step(14.0, 1), 0.0)
        assertEquals(13.0, PinZoom.step(14.0, -1), 0.0)
        assertEquals(PinZoom.MAX, PinZoom.step(20.0, 1), 0.0)
        assertEquals(PinZoom.MIN, PinZoom.step(2.0, -5), 0.0)
        assertEquals(14.0, PinZoom.step(Double.NaN, 1), 0.0)
    }
}

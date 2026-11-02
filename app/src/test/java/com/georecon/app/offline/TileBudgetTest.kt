package com.georecon.app.offline

import com.georecon.app.map.Basemaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileBudgetTest {
    @Test fun boundsAreAboutThreeKmEachWay() {
        val b = TileBudget.bounds(37.0566, -122.0013)
        assertEquals(6.0, (b[2] - b[0]) * 110.574, 0.01)
        assertTrue(b[1] < -122.0013 && b[3] > -122.0013)
    }

    @Test fun savedAreaStaysSmall() {
        val n = TileBudget.estimateTiles(TileBudget.bounds(37.0566, -122.0013), 10, 16)
        assertTrue("tiles=$n", n in 100..3000)
    }

    @Test fun satelliteNeedsKeyAndPickFallsBack() {
        val noKey = Basemaps.all("")
        assertEquals(false, noKey.first { it.id == "satellite" }.available)
        assertEquals("streets", Basemaps.pick(noKey, "satellite").id)
        assertEquals("satellite", Basemaps.pick(Basemaps.all("k"), "satellite").id)
    }
}

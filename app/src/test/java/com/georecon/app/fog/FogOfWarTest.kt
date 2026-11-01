package com.georecon.app.fog

import org.junit.Assert.assertEquals
import org.junit.Test

class FogOfWarTest {
    @Test fun revealRaisesCellAndNeighbors() {
        val f = FogOfWar()
        assertEquals(0, f.level(37.0505, -122.01))
        f.reveal(37.0505, -122.01)
        assertEquals(3, f.level(37.0505, -122.01))
        assertEquals(1, f.level(37.0505 + f.cellSizeDeg, -122.01))
    }
}

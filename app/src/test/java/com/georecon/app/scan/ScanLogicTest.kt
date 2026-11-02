package com.georecon.app.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ScanLogicTest {
    private fun at(az: Double, el: Double, d: Double = 0.35): DoubleArray {
        val a = Math.toRadians(az); val e = Math.toRadians(el)
        return doubleArrayOf(d * cos(e) * sin(a), d * sin(e), d * cos(e) * cos(a))
    }
    private val origin = doubleArrayOf(0.0, 0.0, 0.0)
    private fun check(az: Double, el: Double, sharp: Double = 100.0, rate: Double = 5.0) =
        FrameCheck(true, viewOf(at(az, el), origin), rate, sharp, 120.0, 3.0)

    @Test fun viewMathRoundTrips() {
        val v = viewOf(at(90.0, 45.0), origin)
        assertEquals(90.0, v.azimuthDeg, 1e-6); assertEquals(45.0, v.elevationDeg, 1e-6); assertEquals(0.35, v.distanceM, 1e-9)
        assertEquals(20.0, azDelta(350.0, 10.0), 1e-9)
    }

    @Test fun cellsMapToRingsAndSectors() {
        val c = Coverage()
        assertEquals(36, c.total)
        assertEquals(-1, c.ringOf(2.0))
        assertEquals(0, c.ringOf(18.0)); assertEquals(2, c.ringOf(80.0))
        assertEquals(11, c.sectorOf(359.0))
    }

    @Test fun keepsFramesOnlyAfterEnoughMovementAndSharpness() {
        val b = ScanBrain()
        assertTrue(b.offer(check(0.0, 20.0)))
        assertFalse("too close to last", b.offer(check(3.0, 20.0)))
        assertTrue(b.offer(check(10.0, 20.0)))
        assertTrue(b.offer(check(20.0, 20.0)))           // 3 calibration frames, threshold = 60
        assertFalse("blurry", b.offer(check(30.0, 20.0, sharp = 10.0)))
        assertFalse("too fast", b.offer(check(40.0, 20.0, rate = 40.0)))
        assertTrue(b.offer(check(40.0, 20.0)))
        assertEquals(4, b.kept)
        assertEquals(1, b.coverage.doneCount) // sector 0 of ring 0 has 2 frames (0 and 10 deg)
    }

    @Test fun coachingPriorities() {
        val b = ScanBrain()
        assertEquals(Coach.LOST, b.coach(check(0.0, 20.0).copy(tracking = false)))
        assertEquals(Coach.FAST, b.coach(check(0.0, 20.0, rate = 50.0)))
        assertEquals(Coach.DARK, b.coach(check(0.0, 20.0).copy(meanLuma = 10.0)))
        assertEquals(Coach.CENTER, b.coach(check(0.0, 20.0).copy(offCenterDeg = 30.0)))
        assertEquals(Coach.RAISE, b.coach(check(15.0, 0.0)))
        assertEquals(Coach.GOOD, b.coach(check(15.0, 20.0)))
    }

    @Test fun flipWhenSideComplete() {
        val b = ScanBrain()
        for (r in b.coverage.rings.indices) for (s in 0 until b.coverage.sectors) repeat(2) { b.coverage.add(r * b.coverage.sectors + s) }
        assertEquals(Coach.FLIP, b.coach(check(0.0, 45.0)))
        assertEquals(1.0, b.progress(), 0.0)
        b.newSide(); assertEquals(0, b.coverage.doneCount)
    }

    @Test fun laplacianSeesEdges() {
        val w = 32; val h = 32
        val flat = ByteArray(w * h) { 100 }
        val checker = ByteArray(w * h) { i -> if (((i % w) / 4 + (i / w) / 4) % 2 == 0) 0 else 200.toByte() }
        assertEquals(0.0, laplacianVariance(flat, w, h, w), 0.0)
        assertTrue(laplacianVariance(checker, w, h, w) > 1000)
        assertEquals(100.0, meanLuma(flat, w, h, w), 0.0)
    }
}

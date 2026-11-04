package com.georecon.app.scan

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Guided object scan logic (pure Kotlin, unit-tested). Follows
 * fossil3d/CAPTURE-SPEC.md: 3 rings (20/45/70 deg) x 12 azimuth sectors of 30 deg
 * per side; a cell is done with >= 2 sharp frames.
 */
object ScanSpec {
    val RINGS_DEG = intArrayOf(20, 45, 70)
    const val SECTOR_DEG = 30
    const val SECTORS = 360 / SECTOR_DEG
    const val FRAMES_PER_CELL = 2
    const val MIN_STEP_DEG = 8.0
    const val MAX_RATE_DEG_S = 20.0
    const val TARGET_FRAMES_PER_SIDE = 60
    const val MIN_FRAMES_TOTAL = 80
    const val MIN_CELLS_PER_SIDE = 30
    const val MAX_DISTANCE_M = 0.6
    const val MAX_OFF_CENTER_DEG = 15.0
    const val DARK_LUMA = 40.0
}

/** Camera position relative to the object centre, in degrees (ARCore world: Y up). */
data class View(val azimuthDeg: Double, val elevationDeg: Double, val distanceM: Double)

fun viewOf(cam: DoubleArray, center: DoubleArray): View {
    val dx = cam[0] - center[0]; val dy = cam[1] - center[1]; val dz = cam[2] - center[2]
    val horiz = sqrt(dx * dx + dz * dz)
    val az = (Math.toDegrees(atan2(dx, dz)) + 360) % 360
    return View(az, Math.toDegrees(atan2(dy, horiz)), sqrt(dx * dx + dy * dy + dz * dz))
}

/** Smallest angle between two azimuths. */
fun azDelta(a: Double, b: Double): Double { val d = abs(a - b) % 360; return if (d > 180) 360 - d else d }

class Coverage(val rings: IntArray = ScanSpec.RINGS_DEG, val sectors: Int = ScanSpec.SECTORS) {
    private val counts = IntArray(rings.size * sectors)

    /** Ring index nearest the elevation, or -1 if below 5 deg or more than 20 deg from every ring. */
    fun ringOf(elev: Double): Int {
        if (elev < 5) return -1
        val i = rings.indices.minBy { abs(rings[it] - elev) }
        return if (abs(rings[i] - elev) <= 20) i else -1
    }
    fun sectorOf(az: Double): Int = ((az % 360 + 360) % 360 / (360.0 / sectors)).toInt().coerceIn(0, sectors - 1)
    fun cellOf(v: View): Int = ringOf(v.elevationDeg).let { r -> if (r < 0) -1 else r * sectors + sectorOf(v.azimuthDeg) }

    fun add(cell: Int) { if (cell >= 0) counts[cell]++ }
    fun count(cell: Int) = counts[cell]
    fun done(cell: Int) = counts[cell] >= ScanSpec.FRAMES_PER_CELL
    val total get() = counts.size
    val doneCount get() = counts.count { it >= ScanSpec.FRAMES_PER_CELL }
    fun reset() = counts.fill(0)

    /** Nearest unfinished cell to the current view, as (ring, sector), or null when all done. */
    fun nextTarget(v: View): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null; var bestCost = Double.MAX_VALUE
        for (r in rings.indices) for (s in 0 until sectors) {
            if (done(r * sectors + s)) continue
            val centerAz = (s + 0.5) * 360.0 / sectors
            val cost = azDelta(centerAz, v.azimuthDeg) + 1.5 * abs(rings[r] - v.elevationDeg)
            if (cost < bestCost) { bestCost = cost; best = r to s }
        }
        return best
    }
}

/** Inputs for one candidate frame. */
data class FrameCheck(
    val tracking: Boolean, val view: View, val rateDegS: Double, val sharpness: Double,
    val meanLuma: Double, val offCenterDeg: Double,
)

enum class Coach(val text: String) {
    LOST("WHERE DID IT GO? · HOLD STEADY"), FAST("WHEE, TOO FAST · SLOW DOWN"), DARK("A BIT DARK · ADD LIGHT"),
    BLUR("OOPS, BLURRY · HOLD STEADY"), CENTER("KEEP OBJECT CENTERED"), CLOSER("MOVE CLOSER"),
    LOWER("LOWER THE PHONE"), RAISE("RAISE THE PHONE"), LEFT("MOVE LEFT"), RIGHT("MOVE RIGHT"),
    FLIP("SIDE DONE, SPLENDID! · FLIP THE PIECE"), GOOD("LOVELY · KEEP ORBITING"),
}

class ScanBrain(val coverage: Coverage = Coverage()) {
    private val calib = mutableListOf<Double>()
    var sharpThreshold = 0.0; private set
    private var last: View? = null
    var kept = 0; private set

    /** Calibrate blur threshold on the first 3 frames: 60% of their median. */
    private fun isSharp(s: Double): Boolean {
        if (calib.size < 3) { calib += s; if (calib.size == 3) sharpThreshold = calib.sorted()[1] * 0.6; return true }
        return s >= sharpThreshold
    }

    fun coach(c: FrameCheck): Coach {
        if (!c.tracking) return Coach.LOST
        if (c.rateDegS > ScanSpec.MAX_RATE_DEG_S) return Coach.FAST
        if (c.meanLuma < ScanSpec.DARK_LUMA) return Coach.DARK
        if (calib.size >= 3 && c.sharpness < sharpThreshold) return Coach.BLUR
        if (c.offCenterDeg > ScanSpec.MAX_OFF_CENTER_DEG) return Coach.CENTER
        if (c.view.distanceM > ScanSpec.MAX_DISTANCE_M) return Coach.CLOSER
        val t = coverage.nextTarget(c.view) ?: return Coach.FLIP
        val ringElev = coverage.rings[t.first]
        if (abs(ringElev - c.view.elevationDeg) > 12) return if (ringElev < c.view.elevationDeg) Coach.LOWER else Coach.RAISE
        val targetAz = (t.second + 0.5) * 360.0 / coverage.sectors
        if (azDelta(targetAz, c.view.azimuthDeg) > 20) {
            val cw = ((targetAz - c.view.azimuthDeg) % 360 + 360) % 360
            return if (cw < 180) Coach.LEFT else Coach.RIGHT
        }
        return Coach.GOOD
    }

    /** True if this frame should be kept (and records it). */
    fun offer(c: FrameCheck): Boolean {
        if (!c.tracking || c.rateDegS > ScanSpec.MAX_RATE_DEG_S || c.meanLuma < ScanSpec.DARK_LUMA) return false
        if (c.offCenterDeg > ScanSpec.MAX_OFF_CENTER_DEG) return false
        val cell = coverage.cellOf(c.view)
        if (cell < 0) return false
        val prev = last
        if (prev != null && azDelta(prev.azimuthDeg, c.view.azimuthDeg) < ScanSpec.MIN_STEP_DEG &&
            abs(prev.elevationDeg - c.view.elevationDeg) < ScanSpec.MIN_STEP_DEG) return false
        if (!isSharp(c.sharpness)) return false
        last = c.view; kept++
        coverage.add(cell)
        return true
    }

    /** Fraction toward "enough for a mesh" on this side. */
    fun progress(): Double = max(coverage.doneCount.toDouble() / ScanSpec.MIN_CELLS_PER_SIDE, kept.toDouble() / ScanSpec.TARGET_FRAMES_PER_SIDE).coerceAtMost(1.0)

    fun newSide() { coverage.reset(); last = null; kept = 0 }
}

/** Variance of the Laplacian on a luma plane, sampled every `step` pixels. Higher = sharper. */
fun laplacianVariance(y: ByteArray, w: Int, h: Int, rowStride: Int, step: Int = 1): Double {
    var n = 0; var sum = 0.0; var sq = 0.0
    fun px(x: Int, yy: Int) = (y[yy * rowStride + x].toInt() and 0xFF)
    var yy = step
    while (yy < h - step) {
        var x = step
        while (x < w - step) {
            val l = (px(x - step, yy) + px(x + step, yy) + px(x, yy - step) + px(x, yy + step) - 4 * px(x, yy)).toDouble()
            sum += l; sq += l * l; n++
            x += step
        }
        yy += step
    }
    if (n == 0) return 0.0
    val mean = sum / n
    return sq / n - mean * mean
}

fun meanLuma(y: ByteArray, w: Int, h: Int, rowStride: Int, step: Int = 8): Double {
    var s = 0L; var n = 0
    var yy = 0
    while (yy < h) { var x = 0; while (x < w) { s += y[yy * rowStride + x].toInt() and 0xFF; n++; x += step }; yy += step }
    return if (n == 0) 0.0 else s.toDouble() / n
}

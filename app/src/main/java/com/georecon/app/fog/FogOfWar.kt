package com.georecon.app.fog

import kotlin.math.floor

/**
 * Fog-of-war state on a simple lat/lon grid (H3 later).
 * Level 0 = dark, 3 = fully revealed.
 */
class FogOfWar(private val cellDeg: Double = 0.001) {
    private val levels = HashMap<Pair<Int, Int>, Int>()

    fun cellOf(lat: Double, lon: Double) =
        floor(lat / cellDeg).toInt() to floor(lon / cellDeg).toInt()

    fun level(lat: Double, lon: Double): Int = levels[cellOf(lat, lon)] ?: 0

    /** Raise a cell and dimly reveal its neighbors. */
    fun reveal(lat: Double, lon: Double, to: Int = 3) {
        val (r, c) = cellOf(lat, lon)
        for (dr in -1..1) for (dc in -1..1) {
            val key = (r + dr) to (c + dc)
            val target = if (dr == 0 && dc == 0) to else (to - 2).coerceAtLeast(1)
            levels[key] = maxOf(levels[key] ?: 0, target.coerceIn(0, 3))
        }
    }

    fun revealedCells(): Map<Pair<Int, Int>, Int> = levels
    val cellSizeDeg get() = cellDeg
}

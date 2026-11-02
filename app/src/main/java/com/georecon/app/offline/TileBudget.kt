package com.georecon.app.offline

import kotlin.math.cos

/** Memory/disk limits for map tiles. Only local tiles are kept; nothing is preloaded globally. */
object TileBudget {
    /** MapLibre ambient (browse) cache cap. Older tiles are evicted past this. */
    const val AMBIENT_CACHE_BYTES: Long = 64L * 1024 * 1024
    /** "Save area" downloads only this radius around the current pin. */
    const val AREA_RADIUS_KM = 3.0
    const val AREA_MIN_ZOOM = 10.0
    const val AREA_MAX_ZOOM = 16.0

    /** South, west, north, east around a point, `radiusKm` each way. */
    fun bounds(lat: Double, lon: Double, radiusKm: Double = AREA_RADIUS_KM): DoubleArray {
        val dLat = radiusKm / 110.574
        val dLon = radiusKm / (111.320 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        return doubleArrayOf(
            (lat - dLat).coerceAtLeast(-85.0), lon - dLon, (lat + dLat).coerceAtMost(85.0), lon + dLon,
        )
    }

    /** Upper bound on tile count for a bounds and zoom range (for a sanity check before download). */
    fun estimateTiles(b: DoubleArray, minZoom: Int, maxZoom: Int): Long {
        var total = 0L
        for (z in minZoom..maxZoom) {
            val n = 1 shl z
            fun x(lon: Double) = ((lon + 180) / 360 * n).toInt()
            fun y(lat: Double): Int {
                val r = Math.toRadians(lat)
                return ((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * n).toInt()
            }
            total += (x(b[3]) - x(b[1]) + 1).toLong() * (y(b[0]) - y(b[2]) + 1)
        }
        return total
    }
}

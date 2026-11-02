package com.georecon.app.data

/** One photo/pano/video with the sensor state at capture time. */
data class Capture(
    val id: String, val type: String, val path: String, val timestampMs: Long,
    val lat: Double, val lon: Double, val altM: Double?, val hAccM: Float?,
    val headingDeg: Float?, val pitchDeg: Float?, val rollDeg: Float?,
)

/** A known or user-logged fossil site. */
data class Site(
    val id: String, val name: String, val lat: Double, val lon: Double,
    val source: String, val formation: String?, val ageMa: Double?,
    val collectionId: String? = null,
)

/** A hint at a subsurface feature, backed by captures. */
data class Signal(
    val id: String, val kind: String, val lat: Double, val lon: Double,
    val confidence: Float, val evidence: List<String>, val note: String,
)

/** Layer endpoints. Macrostrat and PBDB are CC-BY 4.0. */
object Sources {
    const val MACROSTRAT_CARTO = "https://tiles.macrostrat.org/carto/{z}/{x}/{y}.png"
    fun pbdbOccurrences(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
        "https://paleobiodb.org/data1.2/occs/list.json?lngmin=$minLon&lngmax=$maxLon&latmin=$minLat&latmax=$maxLat&show=coords,strat"
}

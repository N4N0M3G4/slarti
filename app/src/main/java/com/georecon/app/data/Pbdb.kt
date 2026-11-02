package com.georecon.app.data

import org.json.JSONObject
import java.net.HttpURLConnection

/** Paleobiology Database occurrences (CC-BY 4.0). */
object Pbdb {
    /** Parse PBDB data1.2 compact JSON (fields oid, tna, lng, lat, sfm). */
    fun parse(json: String): List<Site> {
        val recs = JSONObject(json).optJSONArray("records") ?: return emptyList()
        return (0 until recs.length()).mapNotNull { i ->
            val r = recs.getJSONObject(i)
            val lat = r.optDouble("lat", Double.NaN); val lon = r.optDouble("lng", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) null else Site(
                id = r.optString("oid"), name = r.optString("tna"), lat = lat, lon = lon,
                source = "pbdb", formation = r.optString("sfm").ifEmpty { null }, ageMa = null,
                collectionId = r.optString("cid").ifEmpty { null },
            )
        }
    }

    /** Blocking fetch; call off the main thread. */
    fun fetch(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double): List<Site> {
        val conn = com.georecon.app.sync.NetGuard.check(Sources.pbdbOccurrences(minLon, minLat, maxLon, maxLat)).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 15_000
        return conn.inputStream.bufferedReader().use { parse(it.readText()) }
    }

    fun toGeoJson(sites: List<Site>): String = sites.joinToString(",", "{\"type\":\"FeatureCollection\",\"features\":[", "]}") {
        val name = it.name.replace("\"", "'")
        "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${it.lon},${it.lat}]},\"properties\":{\"name\":\"$name\",\"oid\":\"${it.id}\",\"cid\":\"${it.collectionId ?: ""}\",\"formation\":\"${it.formation ?: ""}\"}}"
    }
}

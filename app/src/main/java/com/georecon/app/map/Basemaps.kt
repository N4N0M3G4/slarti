package com.georecon.app.map

/** Basemap choices. Esri satellite needs an ArcGIS key (local.properties: arcgis.apiKey). */
data class Basemap(val id: String, val name: String, val styleUrl: String?, val styleJson: String?, val available: Boolean, val note: String = "")

object Basemaps {
    fun all(arcgisKey: String): List<Basemap> {
        val key = arcgisKey.trim()
        val esriSat = if (key.isEmpty()) null else """
            {"version":8,"sources":{"esri":{"type":"raster","tileSize":256,
            "tiles":["https://ibasemaps-api.arcgis.com/arcgis/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}?token=$key"],
            "attribution":"Esri, Maxar, Earthstar Geographics"}},
            "layers":[{"id":"esri","type":"raster","source":"esri"}]}
        """.trimIndent()
        return listOf(
            Basemap("streets", "STREETS", "https://tiles.openfreemap.org/styles/liberty", null, true),
            Basemap("dark", "DARK", "https://tiles.openfreemap.org/styles/dark", null, true),
            Basemap("satellite", "SAT", null, esriSat, esriSat != null, if (esriSat == null) "needs ArcGIS key" else ""),
        )
    }

    fun pick(all: List<Basemap>, id: String?): Basemap = all.firstOrNull { it.id == id && it.available } ?: all.first()
}

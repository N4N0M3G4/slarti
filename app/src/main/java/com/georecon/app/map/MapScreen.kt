package com.georecon.app.map

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.georecon.app.data.Pbdb
import com.georecon.app.data.Site
import com.georecon.app.data.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.PointF
import com.georecon.app.pin.Pin
import com.georecon.app.pin.PinZoom
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/** Belair Dr / Belair Ct, Scotts Valley, near the find site. */
val HOME = LatLng(37.0566, -122.0013)

/** Imperative handle for HUD buttons (recenter, zoom around the pin). */
class MapController {
    internal var map: MapLibreMap? = null
    internal var pin: Pin? = null

    /** Gestures zoom around the focal point; point it at the pin while locked. */
    internal fun updateFocal() {
        val m = map ?: return
        val p = pin
        m.uiSettings.focalPoint = if (p?.locked == true)
            m.projection.toScreenLocation(LatLng(p.lat, p.lon)).let { PointF(it.x, it.y) } else null
    }
    fun flyTo(pin: Pin, zoom: Double? = null) {
        val m = map ?: return
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pin.lat, pin.lon), zoom ?: maxOf(m.cameraPosition.zoom, 15.0)), 900)
    }
    /** Zoom one step; when locked, around the pin (pin stays centered). */
    fun zoom(direction: Int, pin: Pin?) {
        val m = map ?: return
        val z = PinZoom.step(m.cameraPosition.zoom, direction)
        if (pin?.locked == true) m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pin.lat, pin.lon), z), 300)
        else m.animateCamera(CameraUpdateFactory.zoomTo(z), 300)
    }
}

@Composable
fun MapScreen(
    showGeology: Boolean,
    showFossils: Boolean,
    pin: Pin?,
    controller: MapController,
    basemap: Basemap,
    geologyOpacity: Float,
    fossilOpacity: Float,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val mapView = remember { MapView(ctx).apply { onCreate(null); onStart(); onResume() } }
    var style by remember { mutableStateOf<Style?>(null) }
    var sites by remember { mutableStateOf<List<Site>>(emptyList()) }

    AndroidView(factory = { mapView }, modifier = modifier)
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            controller.map = map
            map.addOnCameraIdleListener { controller.updateFocal() }
            map.cameraPosition = CameraPosition.Builder().target(pin?.let { LatLng(it.lat, it.lon) } ?: HOME).zoom(13.0).build()
        }
        sites = withContext(Dispatchers.IO) {
            runCatching { Pbdb.fetch(HOME.longitude - 0.05, HOME.latitude - 0.05, HOME.longitude + 0.05, HOME.latitude + 0.05) }
                .getOrDefault(emptyList())
        }
    }
    // Basemap: a new style drops our sources, so the overlay effects below re-add them.
    LaunchedEffect(basemap.id) {
        style = null
        mapView.getMapAsync { map ->
            val b = if (basemap.styleJson != null) Style.Builder().fromJson(basemap.styleJson) else Style.Builder().fromUri(basemap.styleUrl!!)
            map.setStyle(b) { style = it }
        }
    }
    LaunchedEffect(style, showGeology, geologyOpacity) {
        val s = style ?: return@LaunchedEffect
        if (s.getSource("geo") == null) {
            s.addSource(RasterSource("geo", TileSet("2.2.0", Sources.MACROSTRAT_CARTO).apply { attribution = "Macrostrat (CC-BY 4.0)" }, 512))
            s.addLayer(RasterLayer("geo-layer", "geo"))
        }
        s.getLayer("geo-layer")?.setProperties(visibility(if (showGeology) "visible" else "none"), rasterOpacity(geologyOpacity))
    }
    LaunchedEffect(style, sites, showFossils, fossilOpacity) {
        val s = style ?: return@LaunchedEffect
        val json = Pbdb.toGeoJson(sites)
        (s.getSourceAs<GeoJsonSource>("pbdb") ?: GeoJsonSource("pbdb").also { s.addSource(it) }).setGeoJson(json)
        if (s.getLayer("pbdb-layer") == null) s.addLayer(
            CircleLayer("pbdb-layer", "pbdb").withProperties(
                circleRadius(6f), circleColor("#00000000"), circleStrokeColor("#3FE0FF"), circleStrokeWidth(2f),
            )
        )
        s.getLayer("pbdb-layer")?.setProperties(visibility(if (showFossils) "visible" else "none"), circleStrokeOpacity(fossilOpacity))
    }
    // Current pin: amber dot. While locked, pinch/double-tap zoom pivots on the pin.
    LaunchedEffect(style, pin) {
        val s = style ?: return@LaunchedEffect
        val json = pin?.let {
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[${it.lon},${it.lat}]},"properties":{}}"""
        } ?: """{"type":"FeatureCollection","features":[]}"""
        (s.getSourceAs<GeoJsonSource>("pin") ?: GeoJsonSource("pin").also { s.addSource(it) }).setGeoJson(json)
        if (s.getLayer("pin-layer") == null) s.addLayer(
            CircleLayer("pin-layer", "pin").withProperties(
                circleRadius(9f), circleColor("#FFB238"), circleStrokeColor("#000000"), circleStrokeWidth(3f),
            )
        )
        controller.pin = pin
        controller.updateFocal()
    }
}

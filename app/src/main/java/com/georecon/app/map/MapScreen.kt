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
import org.maplibre.android.camera.CameraPosition
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

@Composable
fun MapScreen(showGeology: Boolean, showFossils: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val mapView = remember { MapView(ctx).apply { onCreate(null); onStart(); onResume() } }
    var style by remember { mutableStateOf<Style?>(null) }
    var sites by remember { mutableStateOf<List<Site>>(emptyList()) }

    AndroidView(factory = { mapView }, modifier = modifier)
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder().target(HOME).zoom(13.0).build()
            map.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) { style = it }
        }
        sites = withContext(Dispatchers.IO) {
            runCatching { Pbdb.fetch(HOME.longitude - 0.05, HOME.latitude - 0.05, HOME.longitude + 0.05, HOME.latitude + 0.05) }
                .getOrDefault(emptyList())
        }
    }
    LaunchedEffect(style, showGeology) {
        val s = style ?: return@LaunchedEffect
        if (s.getSource("geo") == null) {
            s.addSource(RasterSource("geo", TileSet("2.2.0", Sources.MACROSTRAT_CARTO).apply { attribution = "Macrostrat (CC-BY 4.0)" }, 256))
            s.addLayer(RasterLayer("geo-layer", "geo").withProperties(rasterOpacity(0.6f)))
        }
        s.getLayer("geo-layer")?.setProperties(visibility(if (showGeology) "visible" else "none"))
    }
    LaunchedEffect(style, sites, showFossils) {
        val s = style ?: return@LaunchedEffect
        val json = Pbdb.toGeoJson(sites)
        (s.getSourceAs<GeoJsonSource>("pbdb") ?: GeoJsonSource("pbdb").also { s.addSource(it) }).setGeoJson(json)
        if (s.getLayer("pbdb-layer") == null) s.addLayer(
            CircleLayer("pbdb-layer", "pbdb").withProperties(
                circleRadius(6f), circleColor("#00000000"), circleStrokeColor("#3FE0FF"), circleStrokeWidth(2f),
            )
        )
        s.getLayer("pbdb-layer")?.setProperties(visibility(if (showFossils) "visible" else "none"))
    }
}

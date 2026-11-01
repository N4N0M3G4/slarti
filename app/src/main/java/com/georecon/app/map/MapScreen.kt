package com.georecon.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.georecon.app.data.Sources
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/** Scotts Valley, near the find site. */
val HOME = LatLng(37.0505, -122.0147)

@Composable
fun MapScreen(showGeology: Boolean, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val mapView = remember { MapView(ctx).apply { onCreate(null); onStart(); onResume() } }
    AndroidView(factory = { mapView }, modifier = modifier)
    LaunchedEffect(showGeology) {
        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder().target(HOME).zoom(13.0).build()
            map.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) { style ->
                if (showGeology && style.getSource("geo") == null) {
                    val tiles = TileSet("2.2.0", Sources.MACROSTRAT_CARTO).apply {
                        attribution = "Macrostrat (CC-BY 4.0)"
                    }
                    style.addSource(RasterSource("geo", tiles, 256))
                    style.addLayer(RasterLayer("geo-layer", "geo").withProperties(PropertyFactory.rasterOpacity(0.6f)))
                }
            }
        }
    }
}

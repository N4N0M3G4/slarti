package com.georecon.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georecon.app.map.MapScreen

@Composable
fun GeoReconApp() = SpyTheme {
    var geology by remember { mutableStateOf(true) }
    var fog by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Spy.Black)) {
        MapScreen(showGeology = geology, modifier = Modifier.fillMaxSize())
        if (fog) FogOverlay()
        Hud(geology, { geology = it }, fog, { fog = it })
    }
}

/** Placeholder fog: dark vignette with a clear disc at center (user position). */
@Composable
fun FogOverlay() = Canvas(Modifier.fillMaxSize()) {
    drawRect(
        Brush.radialGradient(
            0f to Color.Transparent, 0.35f to Color.Transparent, 1f to Color(0xE6000000),
            center = center, radius = size.minDimension,
        )
    )
}

@Composable
fun Hud(geology: Boolean, onGeology: (Boolean) -> Unit, fog: Boolean, onFog: (Boolean) -> Unit) {
    val mono = FontFamily.Monospace
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text("GEO-RECON // SCOTTS VALLEY // 37.0505N 122.0147W", color = Spy.Cyan, fontFamily = mono, fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth().border(1.dp, Spy.CyanDim).background(Color(0xCC0B1014)).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Toggle("GEOLOGY", geology, onGeology)
            Toggle("FOG", fog, onFog)
            OutlinedButton(onClick = { /* TODO CameraX capture + GPS/IMU sidecar */ }) {
                Text("CAPTURE", fontFamily = mono, color = Spy.Amber)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, set: (Boolean) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, color = Spy.Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    Spacer(Modifier.width(6.dp))
    Switch(checked = on, onCheckedChange = set)
}

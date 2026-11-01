package com.georecon.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.georecon.app.capture.CaptureStore
import com.georecon.app.capture.readLocation
import com.georecon.app.capture.readOrientation
import com.georecon.app.map.MapScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun GeoReconApp() = SpyTheme {
    var geology by remember { mutableStateOf(true) }
    var fossils by remember { mutableStateOf(true) }
    var fog by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("READY") }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<String, File>?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val (id, file) = pending ?: return@rememberLauncherForActivityResult
        if (!ok) { status = "CAPTURE CANCELLED"; file.delete(); return@rememberLauncherForActivityResult }
        scope.launch {
            val hasLoc = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val loc = if (hasLoc) readLocation(ctx) else null
            val o = readOrientation(ctx)
            val c = withContext(Dispatchers.IO) { CaptureStore.finalize(file, id, System.currentTimeMillis(), loc, o) }
            status = if (loc == null) "SAVED ${c.id.take(8)} (NO GPS)" else "SAVED ${c.id.take(8)} ±${c.hAccM?.toInt()}m"
        }
    }
    fun launchCamera() {
        val id = UUID.randomUUID().toString()
        val f = CaptureStore.newPhotoFile(ctx, id)
        pending = id to f
        takePicture.launch(FileProvider.getUriForFile(ctx, "com.georecon.app.files", f))
    }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { launchCamera() }

    Box(Modifier.fillMaxSize().background(Spy.Black)) {
        MapScreen(showGeology = geology, showFossils = fossils, modifier = Modifier.fillMaxSize())
        if (fog) FogOverlay()
        Hud(status, geology, { geology = it }, fossils, { fossils = it }, fog, { fog = it }) {
            perms.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA))
        }
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
fun Hud(
    status: String,
    geology: Boolean, onGeology: (Boolean) -> Unit,
    fossils: Boolean, onFossils: (Boolean) -> Unit,
    fog: Boolean, onFog: (Boolean) -> Unit,
    onCapture: () -> Unit,
) {
    val mono = FontFamily.Monospace
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("GEO-RECON // SCOTTS VALLEY", color = Spy.Cyan, fontFamily = mono, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, color = Spy.Amber, fontFamily = mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(
            Modifier.fillMaxWidth().border(1.dp, Spy.CyanDim).background(Color(0xCC0B1014)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Toggle("GEO", geology, onGeology); Toggle("FOSSIL", fossils, onFossils); Toggle("FOG", fog, onFog)
            }
            OutlinedButton(onClick = onCapture, modifier = Modifier.fillMaxWidth()) {
                Text("CAPTURE", fontFamily = mono, color = Spy.Amber)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, set: (Boolean) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, color = Spy.Cyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
    Spacer(Modifier.width(4.dp))
    Switch(checked = on, onCheckedChange = set)
}

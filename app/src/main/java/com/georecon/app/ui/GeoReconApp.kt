package com.georecon.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.georecon.app.capture.CaptureStore
import com.georecon.app.capture.readLocation
import com.georecon.app.capture.readOrientation
import com.georecon.app.BuildConfig
import com.georecon.app.map.Basemaps
import com.georecon.app.map.MapController
import com.georecon.app.offline.AreaCache
import com.georecon.app.map.MapScreen
import com.georecon.app.pin.Pin
import com.georecon.app.pin.PinStore
import com.georecon.app.sync.UploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val Mono = FontFamily.Monospace

@Composable
fun GeoReconApp() = SpyTheme {
    var geology by remember { mutableStateOf(true) }
    var fossils by remember { mutableStateOf(true) }
    var fog by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("READY") }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pinStore = remember { PinStore.prefs(ctx) }
    var pin by remember { mutableStateOf(pinStore.load()) }
    val mapController = remember { MapController() }
    var pending by remember { mutableStateOf<Pair<String, File>?>(null) }
    val ui = remember { ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    val basemaps = remember { Basemaps.all(BuildConfig.ARCGIS_API_KEY) }
    var basemap by remember { mutableStateOf(Basemaps.pick(basemaps, ui.getString("basemap", null))) }
    var geoOpacity by remember { mutableStateOf(ui.getFloat("op.geo", 0.4f)) }
    var fossilOpacity by remember { mutableStateOf(ui.getFloat("op.fossil", 0.25f)) }
    var showLayers by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { AreaCache.applyAmbientCap(ctx) }

    fun setPin(p: Pin?) {
        pin = p
        pinStore.save(p)
        p?.let { mapController.flyTo(it) }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val (id, file) = pending ?: return@rememberLauncherForActivityResult
        if (!ok) { status = "CAPTURE CANCELLED"; file.delete(); return@rememberLauncherForActivityResult }
        scope.launch {
            val hasLoc = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val loc = if (hasLoc) readLocation(ctx) else null
            val o = readOrientation(ctx)
            val c = withContext(Dispatchers.IO) { CaptureStore.finalize(file, id, System.currentTimeMillis(), loc, o) }
            UploadWorker.enqueue(ctx)
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

    fun useGps() = scope.launch {
        status = "LOCATING…"
        val loc = readLocation(ctx)
        if (loc == null) { status = "NO GPS FIX"; return@launch }
        setPin(Pin.of(loc.latitude, loc.longitude, "MY LOCATION ±${loc.accuracy.toInt()}m", "gps", pin?.locked ?: true))
        status = "PIN SET FROM GPS"
    }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useGps() else status = "LOCATION PERMISSION DENIED"
    }

    fun search(query: String) = scope.launch {
        if (query.isBlank()) return@launch
        status = "SEARCHING…"
        val hit = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(ctx).getFromLocationName(query, 1)?.firstOrNull()
            }.getOrNull()
        }
        if (hit == null) { status = "NO MATCH FOR \"${query.take(30)}\""; return@launch }
        val label = hit.getAddressLine(0) ?: query
        setPin(Pin.of(hit.latitude, hit.longitude, label, "search", pin?.locked ?: true))
        status = "PIN SET"
    }

    Box(Modifier.fillMaxSize().background(Spy.Black)) {
        MapScreen(
            showGeology = geology, showFossils = fossils, pin = pin, controller = mapController,
            basemap = basemap, geologyOpacity = geoOpacity, fossilOpacity = fossilOpacity, modifier = Modifier.fillMaxSize(),
        )
        if (fog) FogOverlay()
        Column(
            // Edge-to-edge: keep the HUD clear of the status bar, cutouts and nav bar.
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SearchBar(onSearch = ::search, onGps = {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) useGps()
                    else locPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                })
                Text(status, color = Spy.Amber, fontFamily = Mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                PinBar(
                    pin = pin,
                    onLock = { pin?.let { setPinLocked(it, pinStore) { p -> pin = p } } },
                    onRecenter = { pin?.let { mapController.flyTo(it) } },
                    onZoom = { d -> mapController.zoom(d, pin) },
                    onClear = { setPin(null) },
                    onSaveArea = {
                        pin?.let { p ->
                            val url = basemap.styleUrl ?: Basemaps.all("").first().styleUrl!!
                            AreaCache.saveAreaAround(ctx, p, url) { msg -> status = msg }
                        }
                    },
                )
            }
            Column(
                Modifier.fillMaxWidth().border(1.dp, Spy.CyanDim).background(Color(0xCC0B1014)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    basemaps.forEach { b ->
                        HudButton(b.name, active = b.id == basemap.id, enabled = b.available) {
                            basemap = b; ui.edit().putString("basemap", b.id).apply()
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    HudButton(if (showLayers) "LAYERS ▾" else "LAYERS ▸", active = showLayers) { showLayers = !showLayers }
                }
                if (showLayers) {
                    LayerRow("GEO", geology, { geology = it }, geoOpacity) { geoOpacity = it; ui.edit().putFloat("op.geo", it).apply() }
                    LayerRow("FOSSIL", fossils, { fossils = it }, fossilOpacity) { fossilOpacity = it; ui.edit().putFloat("op.fossil", it).apply() }
                    Row(verticalAlignment = Alignment.CenterVertically) { Toggle("FOG", fog) { fog = it } }
                }
                OutlinedButton(onClick = { perms.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("CAPTURE", fontFamily = Mono, color = Spy.Amber)
                }
            }
        }
    }
}

private fun setPinLocked(p: Pin, store: PinStore, set: (Pin) -> Unit) {
    val next = p.copy(locked = !p.locked)
    store.save(next)
    set(next)
}

@Composable
private fun SearchBar(onSearch: (String) -> Unit, onGps: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().border(1.dp, Spy.CyanDim).background(Color(0xE6040709)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("SEARCH ADDRESS", fontFamily = Mono, fontSize = 12.sp, color = Spy.CyanDim) },
            textStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Spy.Cyan),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); onSearch(q) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Spy.Cyan,
            ),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onGps, modifier = Modifier.semantics { contentDescription = "Use my current location as the pin" }) {
            Text("◎", color = Spy.Amber, fontSize = 20.sp)
        }
    }
}

@Composable
private fun PinBar(pin: Pin?, onLock: () -> Unit, onRecenter: () -> Unit, onZoom: (Int) -> Unit, onClear: () -> Unit, onSaveArea: () -> Unit) {
    if (pin == null) return
    Column(
        Modifier.fillMaxWidth().border(1.dp, Spy.CyanDim).background(Color(0xCC0B1014)).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("PIN · ${pin.label}", color = Spy.Amber, fontFamily = Mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HudButton(if (pin.locked) "LOCKED" else "LOCK", active = pin.locked, onClick = onLock)
            HudButton("RECENTER", onClick = onRecenter)
            HudButton("+", onClick = { onZoom(1) })
            HudButton("−", onClick = { onZoom(-1) })
            HudButton("CLEAR", onClick = onClear)
        }
        HudButton("SAVE 3 KM AREA OFFLINE", onClick = onSaveArea)
    }
}

@Composable
private fun HudButton(text: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) =
    OutlinedButton(
        onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (active) Spy.Cyan else Color.Transparent),
        modifier = Modifier.heightIn(min = 36.dp),
    ) { Text(text, fontFamily = Mono, fontSize = 11.sp, color = if (active) Spy.Black else Spy.Cyan, maxLines = 1) }

@Composable
private fun LayerRow(label: String, on: Boolean, setOn: (Boolean) -> Unit, opacity: Float, setOpacity: (Float) -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Toggle(label, on, setOn)
        Slider(
            value = opacity, onValueChange = setOpacity, valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Spy.Cyan, activeTrackColor = Spy.Cyan, inactiveTrackColor = Spy.CyanDim),
            modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { contentDescription = "$label opacity" },
        )
        Text("${(opacity * 100).toInt()}%", color = Spy.Cyan, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.width(40.dp))
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
private fun Toggle(label: String, on: Boolean, set: (Boolean) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, color = Spy.Cyan, fontFamily = Mono, fontSize = 11.sp, maxLines = 1)
    Spacer(Modifier.width(4.dp))
    Switch(checked = on, onCheckedChange = set)
}

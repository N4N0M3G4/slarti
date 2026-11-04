package com.georecon.app.scan

import android.app.Activity
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.georecon.app.BuildConfig
import com.georecon.app.capture.readLocation
import com.georecon.app.sync.UploadWorker
import com.georecon.app.ui.Spy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

private val Mono = FontFamily.Monospace

/** Full-screen guided object scan: orbit the fragment, the dome fills as good frames land. */
@Composable
fun ScanScreen(onExit: (String) -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val scope = rememberCoroutineScope()
    val sessionId = remember { UUID.randomUUID().toString() }
    val store = remember { ScanStore(ScanStore.root(ctx), sessionId) }
    var state by remember { mutableStateOf<ScanState?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var scaleRef by remember { mutableStateOf("charuco") }
    val engine = remember {
        ArScanEngine(activity, store,
            onState = { s -> activity.runOnUiThread { state = s } },
            onCapture = {
                activity.runOnUiThread {
                    activity.window.decorView.performHapticFeedback(
                        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
                }
            })
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) error = engine.resume()
            if (e == Lifecycle.Event.ON_PAUSE) engine.pause()
        }
        lifecycle.addObserver(obs)
        error = engine.resume()
        onDispose { lifecycle.removeObserver(obs); engine.pause(); engine.close() }
    }

    fun finish() = scope.launch {
        val sides = engine.finishSides()
        val loc = runCatching { readLocation(ctx) }.getOrNull()
        val s = state
        withContext(Dispatchers.IO) {
            store.writeSession(SessionMeta(sessionId, System.currentTimeMillis(), "${Build.MANUFACTURER} ${Build.MODEL}", BuildConfig.VERSION_NAME,
                loc?.latitude, loc?.longitude, loc?.accuracy, null, label.trim(), scaleRef, sides, s?.cellsDone ?: 0, s?.cellsTotal ?: 36))
            if (engine.frameNo > 0) store.markComplete()
        }
        UploadWorker.enqueue(ctx)
        onExit(if (engine.frameNo > 0) "SCAN SAVED · ${engine.frameNo} FRAMES · ${sides.size} SIDE(S)" else "SCAN CANCELLED · NO HARM DONE")
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { engine.view }, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.fillMaxWidth().border(1.dp, Spy.CyanDim).background(Color(0xCC040709)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val s = state
                Text(error ?: if (!started) "PLACE PIECE ON THE SCALE SHEET · AIM AT IT FROM ~30 CM · TAP START" else s?.coach?.text ?: "STARTING…",
                    color = if (error != null) Spy.Amber else Spy.Cyan, fontFamily = Mono, fontSize = 14.sp)
                if (s != null) {
                    Text("SIDE ${s.side} · FRAMES ${s.kept} (TOTAL ${s.totalFrames}) · CELLS ${s.cellsDone}/${s.cellsTotal} · ${s.resolution}",
                        color = Spy.CyanDim, fontFamily = Mono, fontSize = 10.sp)
                    LinearProgressIndicator(progress = { s.progress.toFloat() }, color = Spy.Cyan, trackColor = Spy.CyanDim,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Side progress" })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                state?.let { Dome(it, Modifier.size(150.dp)) }
            }
            Column(Modifier.fillMaxWidth().border(1.dp, Spy.CyanDim).background(Color(0xCC040709)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!started) {
                    OutlinedTextField(value = label, onValueChange = { label = it.take(80) }, singleLine = true,
                        label = { Text("OBJECT LABEL (e.g. oyster valve A)", fontFamily = Mono, fontSize = 10.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, color = Spy.Cyan), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("charuco" to "CHARUCO", "ruler" to "RULER", "quarter" to "QUARTER").forEach { (id, t) ->
                            ScanButton(t, active = scaleRef == id) { scaleRef = id }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!started) ScanButton("START", primary = true, enabled = error == null) { engine.start(); started = true }
                    else {
                        ScanButton("FLIP SIDE") { engine.flip(); engine.start() }
                        ScanButton("DONE", primary = true) { finish() }
                    }
                    ScanButton("EXIT") { finish() }
                }
            }
        }
    }
}

/** Top-down coverage dome: rings outward = lower elevation; filled sectors are done; dot = phone. */
@Composable
private fun Dome(s: ScanState, modifier: Modifier) = Canvas(modifier.semantics { contentDescription = "Coverage ${s.cellsDone} of ${s.cellsTotal}" }) {
    val c = Offset(size.width / 2, size.height / 2)
    val maxR = size.minDimension / 2
    val rings = ScanSpec.RINGS_DEG
    val sectors = ScanSpec.SECTORS
    drawCircle(Color(0xCC040709), maxR, c)
    for (r in rings.indices) {
        val outer = maxR * (1f - r / rings.size.toFloat()); val inner = maxR * (1f - (r + 1) / rings.size.toFloat())
        for (k in 0 until sectors) {
            val start = k * 360f / sectors - 90f
            val done = s.filled[r * sectors + k]
            val w = outer - inner
            val rr = inner + w / 2
            drawArc(if (done) Spy.Cyan.copy(alpha = 0.75f) else Color(0x331A6F80), start + 1, 360f / sectors - 2, false,
                topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2, rr * 2), style = Stroke(width = w - 3))
        }
    }
    val el = s.elevationDeg.coerceIn(0.0, 90.0)
    val pr = maxR * (1 - el / 90.0).toFloat()
    val a = Math.toRadians(s.azimuthDeg - 90)
    drawCircle(Spy.Amber, 6f, Offset(c.x + pr * cos(a).toFloat(), c.y + pr * sin(a).toFloat()))
    drawCircle(Spy.CyanDim, maxR, c, style = Stroke(2f))
}

@Composable
private fun ScanButton(text: String, active: Boolean = false, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (active || primary) Spy.Cyan else Color.Transparent)) {
        Text(text, fontFamily = Mono, fontSize = 11.sp, color = if (active || primary) Spy.Black else Spy.Cyan)
    }

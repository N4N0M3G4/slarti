package com.georecon.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.georecon.app.data.Capture
import com.georecon.app.data.FossilDetails
import com.georecon.app.data.FossilInfo
import com.georecon.app.data.GeoLookups
import com.georecon.app.data.GeoUnit
import com.georecon.app.data.WikiSummary
import com.georecon.app.data.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private val Mono = FontFamily.Monospace

@Composable
private fun SpyDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).border(1.dp, Spy.Cyan).background(Color(0xF5040709)).padding(12.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text(title, color = Spy.Amber, fontFamily = Mono, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("CLOSE", color = Spy.Cyan, fontFamily = Mono, fontSize = 11.sp) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }

@Composable
private fun Field(k: String, v: String) {
    if (v.isBlank()) return
    Row { Text(k, color = Spy.CyanDim, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.width(112.dp)); Text(v, color = Color(0xFFBFEFFF), fontFamily = Mono, fontSize = 11.sp) }
}

@Composable
private fun Link(text: String, url: String) {
    val ctx = LocalContext.current
    Text(text, color = Spy.Cyan, fontFamily = Mono, fontSize = 11.sp,
        modifier = Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.padding(vertical = 4.dp))
}

@Composable
fun CaptureDialog(c: Capture, photo: File, onDismiss: () -> Unit) = SpyDialog("FIELD PHOTO ${c.id.take(8)}", onDismiss) {
    AsyncImage(model = photo, contentDescription = "Field photo", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), contentScale = ContentScale.Fit)
    Field("Taken", DateFormat.getDateTimeInstance().format(Date(c.timestampMs)))
    Field("Position", "%.6f, %.6f".format(c.lat, c.lon))
    Field("Accuracy", c.hAccM?.let { "±${it.toInt()} m" } ?: "unknown")
    Field("Heading", c.headingDeg?.let { "${it.toInt()}°" } ?: "unknown")
    Field("Pitch / roll", if (c.pitchDeg != null) "${c.pitchDeg.toInt()}° / ${(c.rollDeg ?: 0f).toInt()}°" else "")
}

@Composable
fun FossilDialog(oid: String, name: String, siblings: List<Site>, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf(oid) }
    var info by remember { mutableStateOf<FossilInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current) {
        info = null; error = null
        runCatching { withContext(Dispatchers.IO) { FossilDetails.load(current) } }
            .onSuccess { info = it }.onFailure { error = it.message ?: "network error" }
    }
    SpyDialog("FOSSIL · ${info?.taxon ?: name}", onDismiss) {
        val d = info
        when {
            error != null -> Text("Could not load details: $error", color = Spy.Amber, fontFamily = Mono, fontSize = 11.sp)
            d == null -> Text("Loading PBDB record…", color = Spy.CyanDim, fontFamily = Mono, fontSize = 11.sp)
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    d.silhouette?.let {
                        AsyncImage(model = it.src, contentDescription = "${d.taxon} silhouette", colorFilter = ColorFilter.tint(Spy.Cyan),
                            modifier = Modifier.size(84.dp, 64.dp), contentScale = ContentScale.Fit)
                    }
                    Column {
                        Text(d.taxon, color = Spy.Amber, fontFamily = Mono, fontSize = 14.sp)
                        Text(listOf(d.rank, d.group).filter { it.isNotBlank() }.joinToString(" · "), color = Color(0xFFBFEFFF), fontFamily = Mono, fontSize = 11.sp)
                        Text(d.lineage, color = Spy.CyanDim, fontFamily = Mono, fontSize = 10.sp)
                    }
                }
                Field("Identified as", d.identifiedAs); Field("Age", d.age); Field("Formation", d.formation)
                Field("Lithology", d.lithology); Field("Environment", d.environment); Field("Collection", d.collection)
                Field("Place", d.place); Field("Precision", d.precision); Field("Reference", d.reference)
                Link("PBDB occurrence ↗", d.occUrl); Link("PBDB collection ↗", d.collUrl)
                var wiki by remember(d.taxon) { mutableStateOf<WikiSummary?>(null) }
                LaunchedEffect(d.taxon) { wiki = withContext(Dispatchers.IO) { GeoLookups.wikiSummary(d.taxon) } }
                wiki?.let { w ->
                    Text(w.extract, color = Color(0xFFBFEFFF), fontFamily = Mono, fontSize = 11.sp)
                    Link("Wikipedia: ${w.title} · CC BY-SA 4.0", w.url)
                }
                d.silhouette?.let { Text("Silhouette: PhyloPic · ${it.credit} · ${it.license}", color = Spy.CyanDim, fontFamily = Mono, fontSize = 10.sp) }
                Fossil3dSlot.content(d)
                Pictures("SPECIMEN PHOTOS (GBIF)", d.images, d.taxon)
                Pictures("MUSEUM SPECIMENS (iDigBio)", d.museum, d.taxon)
                Pictures("SMITHSONIAN OPEN ACCESS", d.smithsonian, d.taxon)
            }
        }
        if (siblings.size > 1) {
            Text("${siblings.size} OCCURRENCES IN THIS COLLECTION", color = Spy.Amber, fontFamily = Mono, fontSize = 11.sp)
            siblings.forEach { s ->
                Text("· ${s.name}", color = if (s.id.removePrefix("occ:") == current.removePrefix("occ:")) Spy.Amber else Spy.Cyan,
                    fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.clickable { current = s.id }.padding(vertical = 3.dp))
            }
        }
    }
}

@Composable
fun GeoUnitDialog(lat: Double, lon: Double, onDismiss: () -> Unit) {
    var unit by remember { mutableStateOf<GeoUnit?>(null) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(lat, lon) { unit = withContext(Dispatchers.IO) { GeoLookups.unitAt(lat, lon) }; done = true }
    SpyDialog("GEOLOGIC UNIT", onDismiss) {
        val u = unit
        when {
            !done -> Text("Asking Macrostrat…", color = Spy.CyanDim, fontFamily = Mono, fontSize = 11.sp)
            u == null -> Text("No mapped unit here (or offline).", color = Spy.Amber, fontFamily = Mono, fontSize = 11.sp)
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(18.dp).background(Color(u.color)).border(1.dp, Color(0xFFBFEFFF)))
                    Text(u.name, color = Spy.Amber, fontFamily = Mono, fontSize = 14.sp)
                }
                Field("Strat. name", u.strat); Field("Age", u.age); Field("Lithology", u.lith)
                Field("Description", u.description); Field("Point", "%.5f, %.5f".format(lat, lon))
                Link("Macrostrat ↗ · source ${u.sourceId} · CC BY 4.0", "https://macrostrat.org/map/loc/%.4f/%.4f".format(lon, lat))
                var lex by remember(u) { mutableStateOf<List<com.georecon.app.data.Lexicon>>(emptyList()) }
                LaunchedEffect(u) { lex = withContext(Dispatchers.IO) { u.stratNameIds.take(2).mapNotNull { GeoLookups.lexicon(it) } } }
                lex.forEach { x ->
                    Text("LEXICON · ${x.longName}", color = Spy.Amber, fontFamily = Mono, fontSize = 11.sp)
                    Field("Rank", x.rank); Field("Age range", x.range); Field("Rock mix", x.lithology)
                    Field("Environments", x.environments); Field("Max thickness", x.maxThicknessM?.let { "$it m" } ?: "")
                    Field("Columns", if (x.columns > 0) "${x.columns} Macrostrat column units" else "")
                    Link("Macrostrat lexicon ↗ · CC BY 4.0", x.url)
                }
            }
        }
    }
}

/** Collapsible legend content: what each symbol means. */
@Composable
fun Legend() = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    @Composable
    fun Item(swatch: @Composable () -> Unit, text: String) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(16.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { swatch() }
        Text(text, color = Color(0xFFBFEFFF), fontFamily = Mono, fontSize = 10.sp)
    }
    val dot = androidx.compose.foundation.shape.CircleShape
    Item({ Box(Modifier.size(14.dp).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFFFFF00), Color(0xFFF4A460), Color(0xFF9ACD32))))) },
        "Geology: each color is one rock unit. Tap the map for name, age, rock type.")
    Item({ Box(Modifier.size(12.dp).border(2.dp, Spy.Cyan, dot).background(Color.Black, dot)) }, "Fossil occurrence (PBDB). Tap for details + pictures.")
    Item({ Box(Modifier.size(12.dp).background(Spy.Amber, dot).border(2.dp, Color.Black, dot)) }, "Your photo. Tap to view.")
    Item({ Box(Modifier.size(16.dp).background(Spy.Amber, dot).border(3.dp, Color.Black, dot)) }, "Current pin (search or ◎ GPS).")
}

@Composable
private fun Pictures(title: String, pics: List<com.georecon.app.data.Picture>, taxon: String) {
    if (pics.isEmpty()) return
    Text(title, color = Spy.Amber, fontFamily = Mono, fontSize = 11.sp)
    pics.forEach { img ->
        AsyncImage(model = img.src, contentDescription = "$taxon specimen", modifier = Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Crop)
        Link("${img.credit} · ${img.license}${if (img.where.isNotBlank()) " · ${img.where}" else ""}", img.link)
    }
}

/**
 * Hook for the fossil3d module (~/personal/geo-recon/fossil3d/): set [content]
 * to a composable that renders a 3D model for the occurrence. Empty by default.
 */
object Fossil3dSlot {
    var content: @Composable (FossilInfo) -> Unit = {}
}

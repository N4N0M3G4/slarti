package com.georecon.app.data

import com.georecon.app.sync.NetGuard
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class GeoUnit(val name: String, val strat: String, val age: String, val lith: String, val description: String, val color: Int, val sourceId: String, val stratNameIds: List<Int> = emptyList())
data class Lexicon(val longName: String, val rank: String, val range: String, val lithology: String, val environments: String, val maxThicknessM: Int?, val columns: Int, val url: String)
data class WikiSummary(val title: String, val extract: String, val url: String)

/** Macrostrat unit at a point and Wikipedia taxon summaries (no keys). Blocking: call off main. */
object GeoLookups {
    private val units = ConcurrentHashMap<String, GeoUnit?>()
    private val wiki = ConcurrentHashMap<String, WikiSummary?>()

    private fun JSONObject.s(k: String) = optString(k).takeIf { it.isNotEmpty() && it != "null" }

    fun summarizeUnit(u: JSONObject): GeoUnit {
        fun age(v: Double) = if (v.isNaN()) "?" else "%.1f".format(v)
        val b = u.s("b_int_name"); val t = u.s("t_int_name")
        val interval = if (t == null || t == b) b else "$b – $t"
        val name = u.s("name") ?: u.s("strat_name") ?: "Unnamed unit"
        val color = u.s("color")?.takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) } ?: "#888888"
        return GeoUnit(
            name = name, strat = u.s("strat_name")?.takeIf { it != name } ?: "",
            age = "${interval ?: "unknown"} (${age(u.optDouble("b_age"))}–${age(u.optDouble("t_age"))} Ma)",
            lith = u.s("lith") ?: "", description = u.s("descrip") ?: "",
            color = (0xFF000000 or color.substring(1).toLong(16)).toInt(), sourceId = u.s("source_id") ?: "",
            stratNameIds = u.optJSONArray("strat_names")?.let { a -> (0 until a.length()).map { a.optInt(it) }.filter { it > 0 } } ?: emptyList(),
        )
    }

    /** Strat-name definition + its column units -> one summary. */
    fun summarizeLexicon(def: JSONObject?, units: org.json.JSONArray?): Lexicon? {
        def ?: return null
        val n = units?.length() ?: 0
        val lith = LinkedHashMap<String, Double>(); val env = LinkedHashSet<String>(); var maxT = 0
        for (i in 0 until n) {
            val u = units!!.getJSONObject(i)
            maxT = maxOf(maxT, u.optInt("max_thick"))
            u.optJSONArray("lith")?.let { a -> for (j in 0 until a.length()) a.getJSONObject(j).let { l -> lith[l.getString("name")] = (lith[l.getString("name")] ?: 0.0) + l.optDouble("prop", 0.0) / n } }
            u.optJSONArray("environ")?.let { a -> for (j in 0 until a.length()) a.getJSONObject(j).s("name")?.let(env::add) }
        }
        fun ma(v: Double) = if (v.isNaN()) "?" else "%.1f".format(v)
        val bp = def.s("b_period"); val tp = def.s("t_period")
        return Lexicon(
            longName = def.s("strat_name_long") ?: def.s("strat_name") ?: "?",
            rank = mapOf("Fm" to "Formation", "Mbr" to "Member", "Gp" to "Group", "SGp" to "Supergroup", "Bed" to "Bed")[def.s("rank")] ?: def.s("rank") ?: "",
            range = "${ma(def.optDouble("b_age"))}–${ma(def.optDouble("t_age"))} Ma (${bp ?: "?"}${if (tp != null && tp != bp) "–$tp" else ""})",
            lithology = lith.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${Math.round(it.value * 100)}%" },
            environments = env.joinToString(", "), maxThicknessM = maxT.takeIf { it > 0 }, columns = n,
            url = def.s("concept_id")?.let { "https://macrostrat.org/lex/strat-name-concepts/$it" } ?: "https://macrostrat.org/lex/strat-names/${def.optInt("strat_name_id")}",
        )
    }

    private val lexicons = ConcurrentHashMap<Int, Lexicon?>()
    fun lexicon(id: Int): Lexicon? = lexicons.getOrPut(id) {
        summarizeLexicon(
            get("https://macrostrat.org/api/v2/defs/strat_names?strat_name_id=$id")?.optJSONObject("success")?.optJSONArray("data")?.optJSONObject(0),
            get("https://macrostrat.org/api/v2/units?strat_name_id=$id&response=long")?.optJSONObject("success")?.optJSONArray("data"),
        )
    }

    fun wikiTitles(taxon: String): List<String> = listOf(taxon.trim(), taxon.trim().split(Regex("\\s+")).first()).filter { it.isNotEmpty() }.distinct()

    private fun get(url: String): JSONObject? = runCatching {
        val c = NetGuard.check(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000; c.readTimeout = 12_000
        c.setRequestProperty("User-Agent", "GEO-RECON (personal)")
        if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    }.getOrNull()

    fun unitAt(lat: Double, lon: Double): GeoUnit? {
        val key = "%.4f,%.4f".format(lat, lon)
        if (units.containsKey(key)) return units[key]
        for (scale in listOf("large", "medium", "small")) {
            val d = get("https://macrostrat.org/api/v2/geologic_units/map?lat=$lat&lng=$lon&scale=$scale")
                ?.optJSONObject("success")?.optJSONArray("data")
            if (d != null && d.length() > 0) return summarizeUnit(d.getJSONObject(0)).also { units[key] = it }
        }
        return null
    }

    fun wikiSummary(taxon: String): WikiSummary? {
        if (wiki.containsKey(taxon)) return wiki[taxon]
        for (title in wikiTitles(taxon)) {
            val d = get("https://en.wikipedia.org/api/rest_v1/page/summary/${URLEncoder.encode(title.replace(' ', '_'), "UTF-8")}") ?: continue
            if (d.optString("type") != "standard" || d.s("extract") == null) continue
            return WikiSummary(d.getString("title"), d.getString("extract"),
                d.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page") ?: "").also { wiki[taxon] = it }
        }
        wiki[taxon] = null
        return null
    }
}

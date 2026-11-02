package com.georecon.app.data

import com.georecon.app.sync.NetGuard
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class Picture(val src: String, val credit: String, val license: String, val where: String, val link: String)

data class FossilInfo(
    val oid: String, val cid: String, val taxon: String, val rank: String, val group: String, val lineage: String,
    val identifiedAs: String, val age: String, val formation: String, val lithology: String, val environment: String,
    val collection: String, val place: String, val precision: String, val reference: String,
    val occUrl: String, val collUrl: String, val taxonNo: String,
    val silhouette: Picture? = null, val images: List<Picture> = emptyList(),
)

/** PBDB occurrence details + PhyloPic silhouette + GBIF specimen photos, fetched lazily and cached. */
object FossilDetails {
    private val GROUPS = mapOf(
        "Mammalia" to "mammal", "Aves" to "bird", "Reptilia" to "reptile", "Chondrichthyes" to "shark or ray",
        "Actinopterygii" to "ray-finned fish", "Bivalvia" to "clam, oyster or scallop", "Gastropoda" to "snail",
        "Echinoidea" to "sea urchin or sand dollar", "Anthozoa" to "coral", "Cephalopoda" to "cephalopod",
        "Foraminifera" to "foraminifer", "Brachiopoda" to "brachiopod", "Malacostraca" to "crab or shrimp",
    )
    private val cache = ConcurrentHashMap<String, FossilInfo>()

    private fun JSONObject.s(k: String) = optString(k).takeIf { it.isNotEmpty() && it != "null" }
    private fun ma(v: Double) = if (v.isNaN()) "?" else "%.1f".format(v)

    fun shortLicense(l: String?): String {
        val s = l.orEmpty()
        if (Regex("publicdomain/zero", RegexOption.IGNORE_CASE).containsMatchIn(s)) return "CC0"
        Regex("licenses/([a-z-]+)/([\\d.]+)", RegexOption.IGNORE_CASE).find(s)?.let { return "CC ${it.groupValues[1].uppercase()} ${it.groupValues[2]}" }
        return s.removePrefix("https://").removePrefix("http://").take(40).ifEmpty { "license unknown" }
    }

    /** Flatten a PBDB occs/single record (vocab=pbdb, show=full). */
    fun summarize(r: JSONObject): FossilInfo {
        val interval = listOfNotNull(r.s("early_interval"), r.s("late_interval")).joinToString(" – ").ifEmpty { "unknown interval" }
        val accepted = r.s("accepted_name"); val identified = r.s("identified_name")
        val cid = r.s("collection_no") ?: ""; val oid = r.s("occurrence_no") ?: ""
        return FossilInfo(
            oid = oid, cid = cid,
            taxon = accepted ?: identified ?: "unidentified",
            rank = r.s("accepted_rank") ?: r.s("identified_rank") ?: "",
            group = GROUPS[r.s("class")] ?: GROUPS[r.s("phylum")] ?: r.s("class") ?: r.s("phylum") ?: "unknown group",
            lineage = listOfNotNull(r.s("phylum"), r.s("class"), r.s("order"), r.s("family")).joinToString(" › "),
            identifiedAs = if (identified != null && identified != accepted) identified else "",
            age = "$interval (${ma(r.optDouble("max_ma"))}–${ma(r.optDouble("min_ma"))} Ma)",
            formation = listOfNotNull(r.s("formation"), r.s("member")?.let { "$it Mbr" }).joinToString(", ").ifEmpty { "not recorded" },
            lithology = listOfNotNull(r.s("lithology1"), r.s("lithology2")).joinToString(", ").ifEmpty { "not recorded" },
            environment = r.s("environment") ?: "not recorded",
            collection = "${r.s("collection_name") ?: "collection"} (PBDB $cid)",
            place = listOfNotNull(r.s("county")?.let { "$it Co." }, r.s("state")).joinToString(", "),
            precision = listOfNotNull(r.s("latlng_precision"), r.s("geogscale")).joinToString(", ").ifEmpty { "unknown" },
            reference = r.s("ref_author")?.let { "$it ${r.s("ref_pubyr") ?: ""}".trim() } ?: "",
            occUrl = "https://paleobiodb.org/classic/displayOccurrence?occurrence_no=$oid",
            collUrl = "https://paleobiodb.org/classic/basicCollectionSearch?collection_no=$cid",
            taxonNo = r.s("accepted_no") ?: "",
        )
    }

    /** Up to n GBIF images, California specimens first, https only, one per specimen. */
    fun gbifImages(results: org.json.JSONArray?, n: Int = 4): List<Picture> {
        val occs = (0 until (results?.length() ?: 0)).map { results!!.getJSONObject(it) }
            .sortedByDescending { it.optString("stateProvince") == "California" }
        val out = mutableListOf<Picture>()
        for (o in occs) {
            val media = o.optJSONArray("media") ?: continue
            val m = (0 until media.length()).map { media.getJSONObject(it) }
                .firstOrNull { it.optString("type") == "StillImage" && it.optString("identifier").startsWith("https://") } ?: continue
            out += Picture(
                src = m.getString("identifier"),
                credit = m.s("rightsHolder") ?: m.s("creator") ?: o.s("institutionCode") ?: o.s("datasetName") ?: "GBIF publisher",
                license = shortLicense(m.s("license") ?: o.s("license")),
                where = listOfNotNull(o.s("stateProvince"), o.s("country")).joinToString(", "),
                link = "https://www.gbif.org/occurrence/${o.optString("key")}",
            )
            if (out.size >= n) break
        }
        return out
    }

    private fun get(url: String): JSONObject {
        val c = NetGuard.check(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "GEO-RECON (personal)")
        return c.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    }

    /** Blocking; call off the main thread. */
    fun load(oid: String): FossilInfo {
        val id = oid.removePrefix("occ:")
        cache[id]?.let { return it }
        val rec = get("https://paleobiodb.org/data1.2/occs/single.json?id=occ:$id&show=full&vocab=pbdb")
            .getJSONArray("records").getJSONObject(0)
        var info = summarize(rec)
        val sil = runCatching {
            val pi = get("https://api.phylopic.org/resolve/paleobiodb.org/txn/${info.taxonNo}?embed_primaryImage=true")
                .getJSONObject("_embedded").getJSONObject("primaryImage")
            val links = pi.getJSONObject("_links")
            val raster = links.getJSONArray("rasterFiles").let { it.getJSONObject(it.length() - 1) }.getString("href")
            Picture(raster, pi.s("attribution") ?: "PhyloPic contributor", shortLicense(links.optJSONObject("license")?.optString("href")), "",
                "https://www.phylopic.org/images/${pi.optString("uuid")}")
        }.getOrNull()
        val imgs = runCatching {
            val q = URLEncoder.encode(info.taxon, "UTF-8")
            gbifImages(get("https://api.gbif.org/v1/occurrence/search?scientificName=$q&mediaType=StillImage&basisOfRecord=FOSSIL_SPECIMEN&limit=20").optJSONArray("results"))
        }.getOrDefault(emptyList())
        info = info.copy(silhouette = sil, images = imgs)
        cache[id] = info
        return info
    }
}

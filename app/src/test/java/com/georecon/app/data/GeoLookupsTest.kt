package com.georecon.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoLookupsTest {
    @Test fun unitSummary() {
        val u = GeoLookups.summarizeUnit(JSONObject("""{"name":"Purisima Formation","strat_name":"Purisima Formation",
            "b_int_name":"Late Miocene","t_int_name":"Pliocene","b_age":8.3395,"t_age":2.588,"color":"#FFFF00","source_id":39}"""))
        assertEquals("Late Miocene – Pliocene (8.3–2.6 Ma)", u.age)
        assertEquals("", u.strat)
        assertEquals(0xFFFFFF00.toInt(), u.color)
        assertEquals("39", u.sourceId)
    }

    @Test fun badColorFallsBack() = assertEquals(0xFF888888.toInt(), GeoLookups.summarizeUnit(JSONObject("""{"color":"red"}""")).color)

    @Test fun wikiTitlesSpeciesThenGenus() {
        assertEquals(listOf("Imagotaria downsi", "Imagotaria"), GeoLookups.wikiTitles("Imagotaria downsi"))
        assertEquals(listOf("Naticidae"), GeoLookups.wikiTitles("Naticidae"))
    }
}

class LexiconAndMuseumTest {
    @Test fun lexiconMergesColumns() {
        val x = GeoLookups.summarizeLexicon(
            JSONObject("""{"strat_name":"Purisima","strat_name_long":"Purisima Formation","rank":"Fm","strat_name_id":1667,"concept_id":14197,"b_age":"8.342","t_age":"0.6538","b_period":"Neogene","t_period":"Neogene"}"""),
            org.json.JSONArray("""[{"max_thick":1725,"lith":[{"name":"sandstone","prop":0.75},{"name":"siltstone","prop":0.25}],"environ":[{"name":"marine"}]},
                {"max_thick":300,"lith":[{"name":"sandstone","prop":0.5},{"name":"mudstone","prop":0.5}],"environ":[{"name":"shoreface"}]}]"""))!!
        assertEquals("Formation", x.rank)
        assertEquals("8.3–0.7 Ma (Neogene)", x.range)
        assertEquals("sandstone 63%, mudstone 25%, siltstone 13%", x.lithology)
        assertEquals(1725, x.maxThicknessM)
    }

    @Test fun idigbioAndSmithsonianParse() {
        val idb = FossilDetails.idigbioImages(org.json.JSONArray("""[
            {"indexTerms":{"accessuri":"https://si/1.jpg","records":["r1"],"rights":"CC0"},"data":{"dc:creator":"NMNH"}},
            {"indexTerms":{"accessuri":"https://si/2.jpg","records":["r1"]},"data":{}},
            {"indexTerms":{"accessuri":"http://x/3.jpg","records":["r3"]},"data":{}}]"""))
        assertEquals(listOf("NMNH"), idb.map { it.credit })
        val si = FossilDetails.smithsonianImages(org.json.JSONArray("""[{"title":"T","content":{"descriptiveNonRepeating":{"data_source":"NMNH - Paleobiology","record_link":"https://n2t.net/x",
            "online_media":{"media":[{"type":"Images","thumbnail":"https://ids.si.edu/t.jpg","usage":{"access":"CC0"}}]}}}}]"""))
        assertEquals("CC0", si.single().license)
    }
}

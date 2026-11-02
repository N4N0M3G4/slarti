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

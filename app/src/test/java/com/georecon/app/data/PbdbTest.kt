package com.georecon.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PbdbTest {
    private val sample = """{"records":[
      {"oid":"occ:1","tna":"Patinopecten healeyi","lng":"-122.01","lat":"37.05","sfm":"Santa Margarita"},
      {"oid":"occ:2","tna":"No coords"}]}"""

    @Test fun parsesAndSkipsRecordsWithoutCoords() {
        val s = Pbdb.parse(sample)
        assertEquals(1, s.size)
        assertEquals("Santa Margarita", s[0].formation)
        assertEquals(-122.01, s[0].lon, 1e-9)
    }

    @Test fun geoJsonIsValid() {
        val fc = JSONObject(Pbdb.toGeoJson(Pbdb.parse(sample)))
        assertEquals(1, fc.getJSONArray("features").length())
    }
}

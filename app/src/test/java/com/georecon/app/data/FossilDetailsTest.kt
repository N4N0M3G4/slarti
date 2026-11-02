package com.georecon.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FossilDetailsTest {
    @Test fun summarizesPbdbRecord() {
        val r = JSONObject("""{"occurrence_no":"297784","collection_no":"13062","identified_name":"Imagotaria downsi",
            "accepted_name":"Imagotaria downsi","accepted_rank":"species","accepted_no":"71875","early_interval":"Tortonian",
            "max_ma":11.63,"min_ma":7.246,"phylum":"Chordata","class":"Mammalia","formation":"Santa Margarita",
            "lithology1":"sandstone","collection_name":"UCMP V-70184","county":"Santa Cruz","state":"California",
            "ref_author":"Repenning and Tedford","ref_pubyr":"1977"}""")
        val s = FossilDetails.summarize(r)
        assertEquals("mammal", s.group)
        assertEquals("Tortonian (11.6–7.2 Ma)", s.age)
        assertEquals("Repenning and Tedford 1977", s.reference)
        assertEquals("", s.identifiedAs)
        assertEquals("not recorded", s.environment)
    }

    @Test fun gbifPrefersCaliforniaAndHttps() {
        val res = JSONArray("""[
          {"key":1,"stateProvince":"Oregon","media":[{"type":"StillImage","identifier":"https://a/1.jpg","rightsHolder":"X"}]},
          {"key":2,"stateProvince":"California","institutionCode":"UCMP","license":"http://creativecommons.org/publicdomain/zero/1.0/","media":[{"type":"StillImage","identifier":"https://a/2.jpg"}]},
          {"key":3,"media":[{"type":"StillImage","identifier":"http://x/3.jpg"}]}]""")
        val imgs = FossilDetails.gbifImages(res)
        assertEquals(listOf("2", "1"), imgs.map { it.link.substringAfterLast('/') })
        assertEquals("CC0", imgs[0].license)
    }

    @Test fun shortensLicenses() {
        assertEquals("CC BY-SA 4.0", FossilDetails.shortLicense("http://creativecommons.org/licenses/by-sa/4.0/"))
        assertEquals("license unknown", FossilDetails.shortLicense(null))
    }

    @Test fun geoJsonCarriesIdsForTaps() {
        val fc = JSONObject(Pbdb.toGeoJson(listOf(Site("occ:1", "A", 37.0, -122.0, "pbdb", null, null, "col:9"))))
        val props = fc.getJSONArray("features").getJSONObject(0).getJSONObject("properties")
        assertEquals("occ:1", props.getString("oid"))
        assertEquals("col:9", props.getString("cid"))
    }
}

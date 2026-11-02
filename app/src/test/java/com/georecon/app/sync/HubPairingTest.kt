package com.georecon.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HubPairingTest {
    private val token = "abcdefghijklmnopqrstuvwx"

    @Test fun parsesLanPairingUri() {
        val p = HubPairing.parse("georecon://pair?url=http%3A%2F%2F192.168.86.28%3A4180&token=$token")
        assertEquals("http://192.168.86.28:4180", p?.baseUrl)
        assertEquals(token, p?.token)
    }

    @Test fun rejectsPublicHostsWrongSchemeAndShortTokens() {
        assertNull(HubPairing.parse("georecon://pair?url=http%3A%2F%2F8.8.8.8%3A4180&token=$token"))
        assertNull(HubPairing.parse("https://pair?url=http%3A%2F%2F192.168.1.2&token=$token"))
        assertNull(HubPairing.parse("georecon://pair?url=http%3A%2F%2F192.168.1.2&token=short"))
        assertNull(HubPairing.parse("georecon://pair?url=ftp%3A%2F%2F10.0.0.2&token=$token"))
    }
}

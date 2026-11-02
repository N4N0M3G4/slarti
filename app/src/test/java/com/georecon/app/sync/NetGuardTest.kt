package com.georecon.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class NetGuardTest {
    private fun blocked(url: String, hub: String? = null) =
        runCatching { NetGuard.check(url, hub) }.exceptionOrNull() is SecurityException

    @Test fun httpsAnywhere() = assertEquals(false, blocked("https://paleobiodb.org/data1.2/x"))

    @Test fun httpOnlyToPrivateHosts() {
        assertEquals(false, blocked("http://192.168.86.28:4180/api/ping"))
        assertEquals(true, blocked("http://example.com/x"))
        assertEquals(true, blocked("http://8.8.8.8/x"))
        assertEquals(true, blocked("http://172.32.0.1/x"))
    }

    @Test fun httpOnlyToThePairedHub() {
        val hub = "http://192.168.86.28:4180"
        assertEquals(false, blocked("$hub/api/captures/a/photo.jpg", hub))
        assertEquals(true, blocked("http://192.168.86.99:4180/x", hub))
        assertEquals(true, blocked("http://192.168.86.28:9999/x", hub))
    }

    @Test fun otherProtocolsBlocked() = assertEquals(true, blocked("ftp://192.168.1.2/x"))
}

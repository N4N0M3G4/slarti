package com.georecon.app.sync

import java.net.URL

/**
 * Every network call goes through [check]. HTTPS may go anywhere; plain HTTP
 * only to a private-LAN host, and only to the paired hub when one is set.
 * (Android's network security config cannot express IP ranges, so the
 * platform allows cleartext and this guard narrows it.)
 */
object NetGuard {
    private val PRIVATE_HOST = Regex("""^(127\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.)""")

    fun isPrivateHost(host: String?): Boolean = host != null && PRIVATE_HOST.containsMatchIn(host)

    /** Returns the URL if allowed, otherwise throws SecurityException. */
    fun check(url: String, pairedHub: String? = null): URL {
        val u = URL(url)
        when (u.protocol) {
            "https" -> return u
            "http" -> {
                if (!isPrivateHost(u.host)) throw SecurityException("Cleartext HTTP blocked for ${u.host}")
                if (pairedHub != null) {
                    val hub = URL(pairedHub)
                    if (hub.host != u.host || hub.port != u.port) throw SecurityException("HTTP only to the paired hub")
                }
                return u
            }
            else -> throw SecurityException("Protocol ${u.protocol} not allowed")
        }
    }
}

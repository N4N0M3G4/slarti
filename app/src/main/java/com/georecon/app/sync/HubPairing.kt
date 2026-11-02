package com.georecon.app.sync

import java.net.URI
import java.net.URLDecoder

/** Hub address + token, parsed from the pairing QR (georecon://pair?url=...&token=...). */
data class HubPairing(val baseUrl: String, val token: String) {
    companion object {
        private val PRIVATE_HOST = Regex(
            """^(127\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.)"""
        )

        /** Returns null for anything that is not a LAN http(s) hub. */
        fun parse(uri: String): HubPairing? = runCatching {
            val u = URI(uri)
            if (u.scheme != "georecon" || u.host != "pair") return null
            val q = u.rawQuery.split("&").associate {
                val (k, v) = it.split("=", limit = 2)
                k to URLDecoder.decode(v, "UTF-8")
            }
            val base = URI(q["url"] ?: return null)
            val token = q["token"]?.takeIf { it.length >= 16 } ?: return null
            if (base.scheme !in setOf("http", "https")) return null
            if (!PRIVATE_HOST.containsMatchIn(base.host ?: "")) return null
            HubPairing(q["url"]!!.trimEnd('/'), token)
        }.getOrNull()
    }
}

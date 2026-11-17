package com.benji.partybell

import android.net.Uri
import java.util.Base64

class Pairing(val hosts: List<String>, val port: Int, val id: String, val key: String) {
    companion object {
        private const val SECRET_SIZE = 32

        fun parse(text: String): Pairing? {
            val uri = Uri.parse(text)
            if (uri.scheme != "partybell" || uri.host != "pair") return null
            val hosts = uri.getQueryParameter("hosts")?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
            val port = uri.getQueryParameter("port")?.toIntOrNull()?.takeIf { it in 1..65535 }
            val id = uri.getQueryParameter("id").orEmpty()
            val key = uri.getQueryParameter("key").orEmpty()
            if (hosts.isEmpty() || port == null || id.isEmpty() || secret(key)?.size != SECRET_SIZE) return null
            return Pairing(hosts, port, id, key)
        }

        fun secret(key: String): ByteArray? = try {
            Base64.getUrlDecoder().decode(key)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

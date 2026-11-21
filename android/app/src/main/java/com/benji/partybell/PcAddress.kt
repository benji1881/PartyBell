package com.benji.partybell

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

// Puts the PC's IP inside a made-up host name, the only one allowed cleartext by res/xml/network_security_config.xml.
object PcAddress : Dns {
    private const val SUFFIX = ".partybell.invalid"

    fun name(ip: String): String? =
        if (parse(ip) == null) null else ip.replace('.', '-') + SUFFIX

    override fun lookup(hostname: String): List<InetAddress> {
        val prefix = hostname.removeSuffix(SUFFIX)
        // Names we didn't make are refused, so this can never reach anything but the IP it carries.
        val bytes = if (hostname.endsWith(SUFFIX) && '.' !in prefix) parse(prefix.replace('-', '.')) else null
        // getByAddress builds the address directly, so no DNS lookup ever happens.
        return listOf(InetAddress.getByAddress(bytes ?: throw UnknownHostException(hostname)))
    }

    // Tailscale hands out addresses from 100.64.0.0/10.
    fun isTunnel(ip: String): Boolean {
        val bytes = parse(ip) ?: return false
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        return first == 100 && second in 64..127
    }

    // Only plain dotted IPv4 is accepted, so nothing a resolver would treat as a name gets through.
    private fun parse(ip: String): ByteArray? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for ((i, part) in parts.withIndex()) {
            if (part.length !in 1..3 || part.any { it !in '0'..'9' }) return null
            val value = part.toInt()
            if (value > 255) return null
            bytes[i] = value.toByte()
        }
        return bytes
    }
}

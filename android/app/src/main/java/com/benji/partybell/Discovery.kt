package com.benji.partybell

import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer

// Finds the PC on the home network after its LAN address has changed since pairing.
object Discovery {
    // Keep these in step with plugin/Beacon.cs.
    private const val PROBE = "PartyBell?"
    private const val REPLY = "PartyBell!"
    private const val WAIT_MS = 1_500L
    private val everyone: InetAddress = InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1))

    // Blocks for up to WAIT_MS, so call it off the main thread.
    fun find(connectivity: ConnectivityManager, port: Int, phoneId: String): String? {
        val network = connectivity.activeNetwork ?: return null
        val probe = (PROBE + phoneId).toByteArray(Charsets.US_ASCII)
        return try {
            DatagramSocket().use { socket ->
                network.bindSocket(socket)
                socket.broadcast = true
                for (target in broadcasts(connectivity, network) + everyone) {
                    // One unreachable subnet shouldn't stop the probe reaching the others.
                    try {
                        socket.send(DatagramPacket(probe, probe.size, target, port))
                    } catch (_: IOException) {
                    }
                }
                listen(socket)
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun broadcasts(connectivity: ConnectivityManager, network: Network): List<InetAddress> =
        connectivity.getLinkProperties(network)?.linkAddresses.orEmpty().mapNotNull { link ->
            val address = link.address as? Inet4Address ?: return@mapNotNull null
            // Shifting a Long keeps a /32 at zero host bits, where an Int shift by 32 would wrap.
            val hostBits = ((1L shl (32 - link.prefixLength)) - 1).toInt()
            val bits = ByteBuffer.wrap(address.address).int or hostBits
            InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(bits).array())
        }

    // Waits until the deadline, skipping stray packets, for the PC's answer from a private address.
    private fun listen(socket: DatagramSocket): String? {
        val reply = REPLY.toByteArray(Charsets.US_ASCII)
        val deadline = SystemClock.elapsedRealtime() + WAIT_MS
        while (true) {
            val left = deadline - SystemClock.elapsedRealtime()
            if (left <= 0) return null
            socket.soTimeout = left.toInt()
            // One spare byte so a longer payload can't pass as an exact match.
            val packet = DatagramPacket(ByteArray(reply.size + 1), reply.size + 1)
            socket.receive(packet)
            val from = packet.address
            val exact = packet.data.copyOf(packet.length).contentEquals(reply)
            if (exact && from is Inet4Address && (from.isSiteLocalAddress || from.isLinkLocalAddress)) return from.hostAddress
        }
    }
}

package com.remotetouchpad.app

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

data class FoundDevice(val name: String, val ip: String, val port: Int)

private const val DISCOVERY_PORT = 8766
private const val DISCOVERY_MESSAGE = "REMOTE_CONTROL_DISCOVER"
private const val SCAN_DURATION_MS = 1500

/**
 * Broadcasts a UDP discovery packet on the LAN and collects replies from any
 * running RemoteControlServer for SCAN_DURATION_MS. Runs on the calling thread
 * (call from a background thread).
 */
object DeviceDiscovery {

    fun scan(): List<FoundDevice> {
        val found = linkedMapOf<String, FoundDevice>()
        val socket = DatagramSocket().apply {
            broadcast = true
            soTimeout = 300
        }

        try {
            val requestBytes = DISCOVERY_MESSAGE.toByteArray()
            val broadcastPacket = DatagramPacket(
                requestBytes, requestBytes.size,
                InetSocketAddress("255.255.255.255", DISCOVERY_PORT)
            )

            val deadline = System.currentTimeMillis() + SCAN_DURATION_MS
            socket.send(broadcastPacket)

            val buffer = ByteArray(1024)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(responsePacket)
                    val text = String(responsePacket.data, 0, responsePacket.length)
                    val json = JSONObject(text)
                    val device = FoundDevice(
                        name = json.optString("name", "Windows PC"),
                        ip = json.getString("ip"),
                        port = json.optInt("port", 8765)
                    )
                    found[device.ip] = device
                } catch (_: SocketTimeoutException) {
                    // keep looping until deadline
                } catch (_: Exception) {
                    // ignore malformed packet
                }
            }
        } finally {
            socket.close()
        }

        return found.values.toList()
    }
}

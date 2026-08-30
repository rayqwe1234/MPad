package com.mpad.controller.transport

import android.content.Context
import com.mpad.controller.protocol.Protocol
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

data class LanTarget(val id: String, val name: String, val address: InetAddress, val controlPort: Int, val driverReady: Boolean)

object LanDiscovery {
    fun discover(timeoutMs: Int = 1500): List<LanTarget> {
        val results = linkedMapOf<String, LanTarget>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 200
            val request = Protocol.encode(Protocol.MessageType.DiscoveryRequest)
            socket.send(DatagramPacket(request, request.size,
                InetSocketAddress(InetAddress.getByName("255.255.255.255"), Protocol.DISCOVERY_PORT)))
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    val buffer = ByteArray(2048)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val frame = Protocol.decode(packet.data.copyOf(packet.length))
                    if (frame.header.type != Protocol.MessageType.DiscoveryResponse) continue
                    val json = JSONObject(String(frame.payload))
                    val name = json.getString("name")
                    val id = "lan:$name"
                    results[id] = LanTarget(id, name, packet.address,
                        json.optInt("controlPort", Protocol.CONTROL_PORT), json.optBoolean("driverReady"))
                } catch (_: SocketTimeoutException) { }
            }
        }
        return results.values.toList()
    }
}

class LanConnection(context: Context, private val target: LanTarget) :
    CompanionConnection(context, target.id, target.name) {
    private var socket: Socket? = null
    private var udp: DatagramSocket? = null

    override fun openStreams() = Socket().let { opened ->
        opened.tcpNoDelay = true
        opened.connect(InetSocketAddress(target.address, target.controlPort), 4000)
        socket = opened
        udp = DatagramSocket()
        opened.getInputStream() to opened.getOutputStream()
    }

    override fun transmitInput(frame: ByteArray) {
        udp!!.send(DatagramPacket(frame, frame.size,
            InetSocketAddress(target.address, Protocol.DISCOVERY_PORT)))
    }

    override fun closeTransport() {
        try { udp?.close() } catch (_: Exception) { }
        try { socket?.close() } catch (_: Exception) { }
    }
}


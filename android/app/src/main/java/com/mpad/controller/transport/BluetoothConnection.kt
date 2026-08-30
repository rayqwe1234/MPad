package com.mpad.controller.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.mpad.controller.protocol.Protocol
import java.util.UUID

data class BluetoothTarget(val id: String, val name: String, val device: BluetoothDevice)

object BluetoothTargets {
    @SuppressLint("MissingPermission")
    fun bonded(context: Context): List<BluetoothTarget> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return adapter.bondedDevices.sortedBy { it.name ?: it.address }.map {
            BluetoothTarget("bt:${it.address}", it.name ?: it.address, it)
        }
    }
}

class BluetoothConnection(context: Context, private val target: BluetoothTarget) :
    CompanionConnection(context, target.id, target.name) {
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    override fun openStreams() = target.device
        .createRfcommSocketToServiceRecord(UUID.fromString(Protocol.BLUETOOTH_UUID)).let { opened ->
            socket = opened
            opened.connect()
            opened.inputStream to opened.outputStream
        }

    override fun transmitInput(frame: ByteArray) = Protocol.writeFrame(output!!, frame)

    override fun closeTransport() { try { socket?.close() } catch (_: Exception) { } }
}

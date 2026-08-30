package com.mpad.controller.protocol

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Protocol {
    const val VERSION: Byte = 1
    const val HEADER_SIZE = 20
    const val AUTH_TAG_SIZE = 16
    const val GAMEPAD_PAYLOAD_SIZE = 24
    const val DISCOVERY_PORT = 26760
    const val CONTROL_PORT = 26761
    const val BLUETOOTH_UUID = "79165E10-9A7B-4C8D-A0E1-4D5041440001"
    private val magic = byteArrayOf('M'.code.toByte(), 'P'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte())

    enum class MessageType(val value: Byte) {
        DiscoveryRequest(1), DiscoveryResponse(2), PairRequest(3), PairResponse(4),
        AuthRequest(5), AuthResponse(6), InputState(10), Rumble(11), Ping(12),
        Pong(13), Disconnect(14), Error(15);

        companion object {
            fun from(value: Byte) = entries.firstOrNull { it.value == value }
                ?: throw ProtocolException("Unknown message type $value")
        }
    }

    data class Header(
        val version: Byte,
        val type: MessageType,
        val flags: Int,
        val payloadLength: Int,
        val sessionId: Long,
        val sequence: Long,
    )

    data class Frame(val header: Header, val payload: ByteArray)

    fun encode(
        type: MessageType,
        payload: ByteArray = byteArrayOf(),
        sessionId: Long = 0,
        sequence: Long = 0,
        key: ByteArray? = null,
    ): ByteArray {
        require(payload.size <= 0xffff)
        val authenticated = key != null && key.isNotEmpty()
        val size = HEADER_SIZE + payload.size + if (authenticated) AUTH_TAG_SIZE else 0
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(magic)
        buffer.put(VERSION)
        buffer.put(type.value)
        buffer.putShort(if (authenticated) 1 else 0)
        buffer.putShort(payload.size.toShort())
        buffer.putShort(0)
        buffer.putInt(sessionId.toInt())
        buffer.putInt(sequence.toInt())
        buffer.put(payload)
        if (authenticated) buffer.put(hmac(buffer.array(), HEADER_SIZE + payload.size, key!!))
        return buffer.array()
    }

    fun decode(bytes: ByteArray, key: ByteArray? = null): Frame {
        if (bytes.size < HEADER_SIZE) throw ProtocolException("Frame is shorter than the header")
        val header = readHeader(bytes)
        if (header.version != VERSION) throw ProtocolException("Unsupported protocol version ${header.version}")
        val authenticated = header.flags and 1 != 0
        val expected = HEADER_SIZE + header.payloadLength + if (authenticated) AUTH_TAG_SIZE else 0
        if (bytes.size != expected) throw ProtocolException("Invalid frame length")
        if (authenticated) {
            if (key == null) throw ProtocolException("Missing authentication key")
            val contentLength = HEADER_SIZE + header.payloadLength
            val expectedTag = hmac(bytes, contentLength, key)
            val supplied = bytes.copyOfRange(contentLength, contentLength + AUTH_TAG_SIZE)
            if (!constantTimeEquals(expectedTag, supplied)) throw ProtocolException("Frame authentication failed")
        }
        return Frame(header, bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + header.payloadLength))
    }

    fun readHeader(bytes: ByteArray): Header {
        if (bytes.size < HEADER_SIZE || !bytes.copyOfRange(0, 4).contentEquals(magic))
            throw ProtocolException("Invalid MPad header")
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.position(4)
        val version = b.get()
        val type = MessageType.from(b.get())
        val flags = b.short.toInt() and 0xffff
        val length = b.short.toInt() and 0xffff
        b.short
        val session = b.int.toLong() and 0xffffffffL
        val sequence = b.int.toLong() and 0xffffffffL
        return Header(version, type, flags, length, session, sequence)
    }

    fun readFrame(input: InputStream, keyResolver: (Long) -> ByteArray?): Frame? {
        val headerBytes = ByteArray(HEADER_SIZE)
        if (!readExactlyOrEnd(input, headerBytes)) return null
        val header = readHeader(headerBytes)
        val tailSize = header.payloadLength + if (header.flags and 1 != 0) AUTH_TAG_SIZE else 0
        val bytes = ByteArray(HEADER_SIZE + tailSize)
        headerBytes.copyInto(bytes)
        readExactly(input, bytes, HEADER_SIZE, tailSize)
        return decode(bytes, keyResolver(header.sessionId))
    }

    fun writeFrame(output: OutputStream, frame: ByteArray) {
        output.write(frame)
        output.flush()
    }

    private fun hmac(bytes: ByteArray, length: Int, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(bytes.copyOf(length)).copyOf(AUTH_TAG_SIZE)
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun readExactlyOrEnd(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) {
                if (offset == 0) return false
                throw EOFException("Stream ended inside a frame")
            }
            offset += count
        }
        return true
    }

    private fun readExactly(input: InputStream, buffer: ByteArray, offset: Int, length: Int) {
        var read = 0
        while (read < length) {
            val count = input.read(buffer, offset + read, length - read)
            if (count < 0) throw EOFException("Stream ended inside a frame")
            read += count
        }
    }
}

class ProtocolException(message: String) : Exception(message)


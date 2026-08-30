package com.mpad.controller.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtocolTest {
    @Test
    fun gamepadPayloadMatchesGoldenVector() {
        val state = GamepadState(
            timestampMicros = 0x0102030405060708,
            buttons = Buttons.A or Buttons.LB or Buttons.GUIDE,
            hat = Hat.NORTH_WEST,
            leftX = Short.MIN_VALUE,
            leftY = Short.MAX_VALUE,
            rightX = (-1).toShort(),
            rightY = 1,
            leftTrigger = 12,
            rightTrigger = 250,
            batteryPercent = 87,
        )
        val expected = hex("080706050403020111010000070080ff7fffff01000cfa57")
        assertArrayEquals(expected, state.encode())
        assertEquals(state, GamepadState.decode(expected))
    }

    @Test
    fun authenticatedFrameRejectsTampering() {
        val key = ByteArray(32) { it.toByte() }
        val frame = Protocol.encode(Protocol.MessageType.InputState, byteArrayOf(1, 2, 3), 42, 7, key)
        assertEquals(Protocol.MessageType.InputState, Protocol.decode(frame, key).header.type)
        frame[Protocol.HEADER_SIZE] = (frame[Protocol.HEADER_SIZE].toInt() xor 0x80).toByte()
        assertThrows(ProtocolException::class.java) { Protocol.decode(frame, key) }
    }

    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

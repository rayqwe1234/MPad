package com.mpad.controller.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Buttons {
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val X = 1 shl 2
    const val Y = 1 shl 3
    const val LB = 1 shl 4
    const val RB = 1 shl 5
    const val BACK = 1 shl 6
    const val START = 1 shl 7
    const val GUIDE = 1 shl 8
    const val L3 = 1 shl 9
    const val R3 = 1 shl 10
}

object Hat {
    const val NORTH = 0
    const val NORTH_EAST = 1
    const val EAST = 2
    const val SOUTH_EAST = 3
    const val SOUTH = 4
    const val SOUTH_WEST = 5
    const val WEST = 6
    const val NORTH_WEST = 7
    const val NEUTRAL = 8
}

data class GamepadState(
    val timestampMicros: Long = 0,
    val buttons: Int = 0,
    val hat: Int = Hat.NEUTRAL,
    val leftX: Short = 0,
    val leftY: Short = 0,
    val rightX: Short = 0,
    val rightY: Short = 0,
    val leftTrigger: Int = 0,
    val rightTrigger: Int = 0,
    val batteryPercent: Int = 255,
) {
    fun encode(): ByteArray {
        val b = ByteBuffer.allocate(Protocol.GAMEPAD_PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        b.putLong(timestampMicros)
        b.putInt(buttons)
        b.put(hat.toByte())
        b.putShort(leftX)
        b.putShort(leftY)
        b.putShort(rightX)
        b.putShort(rightY)
        b.put(leftTrigger.coerceIn(0, 255).toByte())
        b.put(rightTrigger.coerceIn(0, 255).toByte())
        b.put(batteryPercent.coerceIn(0, 255).toByte())
        return b.array()
    }

    companion object {
        fun decode(bytes: ByteArray): GamepadState {
            require(bytes.size == Protocol.GAMEPAD_PAYLOAD_SIZE)
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return GamepadState(
                b.long, b.int, b.get().toInt() and 0xff,
                b.short, b.short, b.short, b.short,
                b.get().toInt() and 0xff, b.get().toInt() and 0xff, b.get().toInt() and 0xff,
            )
        }
    }
}

data class RumbleState(val low: Int, val high: Int, val durationMs: Int, val eventId: Long) {
    companion object {
        fun decode(bytes: ByteArray): RumbleState {
            require(bytes.size == 8)
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return RumbleState(b.get().toInt() and 0xff, b.get().toInt() and 0xff,
                b.short.toInt() and 0xffff, b.int.toLong() and 0xffffffffL)
        }
    }
}


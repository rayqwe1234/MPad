package com.mpad.controller.transport

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.mpad.controller.protocol.GamepadState
import com.mpad.controller.protocol.Protocol
import com.mpad.controller.protocol.RumbleState
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

abstract class CompanionConnection(
    context: Context,
    private val peerId: String,
    private val peerName: String,
) : AutoCloseable {
    private val pairings = SecurePairingStore(context)
    private val devicePrefs = context.getSharedPreferences("mpad_device", Context.MODE_PRIVATE)
    private val writer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val latestState = AtomicReference(GamepadState())
    private val outputLock = Any()
    private var lastState: GamepadState? = null
    private var lastSentAt = 0L
    private var sequence = 0L
    private var input: InputStream? = null
    protected var output: OutputStream? = null
    protected var sessionId = 0L
    protected var token: ByteArray? = null
    @Volatile var connected = false
        private set

    var onRumble: ((RumbleState) -> Unit)? = null
    var onStatus: ((String) -> Unit)? = null

    protected abstract fun openStreams(): Pair<InputStream, OutputStream>
    protected abstract fun transmitInput(frame: ByteArray)
    protected open fun closeTransport() {}

    fun connect(pairingCode: String?) {
        onStatus?.invoke("正在连接 $peerName…")
        val (openedInput, openedOutput) = openStreams()
        input = openedInput
        output = openedOutput
        val existingToken = pairings.get(peerId)
        val deviceId = deviceId()
        val request: ByteArray
        val expectedType: Protocol.MessageType
        if (existingToken == null) {
            if (pairingCode.isNullOrBlank()) throw PairingRequiredException()
            request = JSONObject().put("code", pairingCode).put("deviceId", deviceId)
                .put("name", Build.MODEL).toString().toByteArray()
            expectedType = Protocol.MessageType.PairResponse
            Protocol.writeFrame(openedOutput, Protocol.encode(Protocol.MessageType.PairRequest, request))
        } else {
            request = JSONObject().put("deviceId", deviceId).put("name", Build.MODEL)
                .put("token", Base64.encodeToString(existingToken, Base64.NO_WRAP)).toString().toByteArray()
            expectedType = Protocol.MessageType.AuthResponse
            Protocol.writeFrame(openedOutput, Protocol.encode(Protocol.MessageType.AuthRequest, request))
        }

        val response = Protocol.readFrame(openedInput) { null }
            ?: throw IllegalStateException("电脑在认证前断开")
        if (response.header.type != expectedType) throw IllegalStateException("电脑返回了意外响应")
        val json = JSONObject(String(response.payload))
        if (!json.optBoolean("ok")) {
            pairings.remove(peerId)
            throw PairingRequiredException(json.optString("error", "需要重新配对"))
        }
        token = if (existingToken == null) {
            Base64.decode(json.getString("token"), Base64.NO_WRAP).also { pairings.put(peerId, it) }
        } else existingToken
        sessionId = json.getLong("sessionId")
        connected = true
        onStatus?.invoke("已连接 $peerName")
        startReader(openedInput)
        writer.scheduleAtFixedRate(::publishLatest, 0, 8, TimeUnit.MILLISECONDS)
    }

    fun publish(state: GamepadState) = latestState.set(state)

    private fun publishLatest() {
        if (!connected) return
        val state = latestState.get()
        val now = System.nanoTime()
        if (state == lastState && now - lastSentAt < 100_000_000L) return
        try {
            val stamped = state.copy(timestampMicros = System.nanoTime() / 1_000)
            val frame = Protocol.encode(Protocol.MessageType.InputState, stamped.encode(),
                sessionId, ++sequence, token)
            synchronized(outputLock) { transmitInput(frame) }
            lastState = state
            lastSentAt = now
        } catch (e: Exception) {
            fail("发送输入失败：${e.message}")
        }
    }

    private fun startReader(openedInput: InputStream) {
        Thread({
            try {
                while (connected) {
                    val frame = Protocol.readFrame(openedInput) { id -> if (id == sessionId) token else null } ?: break
                    when (frame.header.type) {
                        Protocol.MessageType.Rumble -> onRumble?.invoke(RumbleState.decode(frame.payload))
                        Protocol.MessageType.Ping -> synchronized(outputLock) {
                            Protocol.writeFrame(output!!, Protocol.encode(Protocol.MessageType.Pong,
                                frame.payload, sessionId, ++sequence, token))
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                if (connected) onStatus?.invoke("连接已断开：${e.message ?: "读取结束"}")
            } finally { close() }
        }, "MPad-control-reader").apply { isDaemon = true; start() }
    }

    private fun fail(message: String) {
        onStatus?.invoke(message)
        close()
    }

    override fun close() {
        if (!connected && input == null && output == null) return
        connected = false
        try {
            val key = token
            val out = output
            if (out != null && key != null) synchronized(outputLock) {
                Protocol.writeFrame(out, Protocol.encode(Protocol.MessageType.Disconnect,
                    sessionId = sessionId, sequence = ++sequence, key = key))
            }
        } catch (_: Exception) { }
        writer.shutdownNow()
        try { input?.close() } catch (_: Exception) { }
        try { output?.close() } catch (_: Exception) { }
        closeTransport()
        input = null
        output = null
    }

    private fun deviceId(): String {
        devicePrefs.getString("device_id", null)?.let { return it }
        val value = UUID.randomUUID().toString()
        devicePrefs.edit().putString("device_id", value).apply()
        return value
    }
}

class PairingRequiredException(message: String = "请输入电脑端显示的六位配对码") : Exception(message)


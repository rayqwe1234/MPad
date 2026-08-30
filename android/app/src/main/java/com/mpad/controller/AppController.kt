package com.mpad.controller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpad.controller.data.ControlSpec
import com.mpad.controller.data.ControllerSettings
import com.mpad.controller.data.DefaultLayout
import com.mpad.controller.data.PreferencesRepository
import com.mpad.controller.protocol.GamepadState
import com.mpad.controller.protocol.RumbleState
import com.mpad.controller.transport.BluetoothConnection
import com.mpad.controller.transport.BluetoothTarget
import com.mpad.controller.transport.BluetoothTargets
import com.mpad.controller.transport.CompanionConnection
import com.mpad.controller.transport.HidGamepad
import com.mpad.controller.transport.LanConnection
import com.mpad.controller.transport.LanDiscovery
import com.mpad.controller.transport.LanTarget
import java.net.InetAddress
import java.util.concurrent.Executors

enum class AppScreen { CONNECT, PLAY, EDIT_LAYOUT, SETTINGS }
enum class ConnectionMode { LAN, BLUETOOTH, HID }

class AppController(private val context: Context) : AutoCloseable {
    private val repository = PreferencesRepository(context)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var connection: CompanionConnection? = null
    private var hid: HidGamepad? = null
    private val battery = context.getSystemService(BatteryManager::class.java)

    var screen by mutableStateOf(AppScreen.CONNECT)
    var mode by mutableStateOf(ConnectionMode.LAN)
    var status by mutableStateOf("选择连接方式")
    var busy by mutableStateOf(false)
    var connectedName by mutableStateOf<String?>(null)
    var lanTargets by mutableStateOf<List<LanTarget>>(emptyList())
    var bluetoothTargets by mutableStateOf<List<BluetoothTarget>>(emptyList())
    var layout by mutableStateOf(repository.loadLayout())
    var settings by mutableStateOf(repository.loadSettings())
    private var lastState = GamepadState()

    fun discoverLan() {
        busy = true; status = "正在搜索局域网中的电脑…"
        io.execute {
            runCatching { LanDiscovery.discover() }
                .onSuccess { targets -> post {
                    lanTargets = targets; busy = false
                    status = if (targets.isEmpty()) "未自动发现电脑，可使用手动 IP" else "找到 ${targets.size} 台电脑"
                }}
                .onFailure { error -> post { busy = false; status = "局域网搜索失败：${error.message}" } }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBluetooth() {
        runCatching { BluetoothTargets.bonded(context) }
            .onSuccess { bluetoothTargets = it; status = if (it.isEmpty()) "请先在系统蓝牙设置中配对电脑" else "已配对设备 ${it.size} 个" }
            .onFailure { status = "读取蓝牙设备失败：${it.message}" }
    }

    fun connectLan(target: LanTarget, code: String) = connect(
        LanConnection(context, target), target.name, code)

    fun connectManualIp(ip: String, code: String) {
        val address = runCatching { InetAddress.getByName(ip.trim()) }.getOrElse {
            status = "IP 地址无效"; return
        }
        connectLan(LanTarget("lan:manual:${address.hostAddress}", address.hostAddress ?: ip,
            address, com.mpad.controller.protocol.Protocol.CONTROL_PORT, false), code)
    }

    fun connectBluetooth(target: BluetoothTarget, code: String) = connect(
        BluetoothConnection(context, target), target.name, code)

    @SuppressLint("MissingPermission")
    fun connectHid(target: BluetoothTarget) {
        disconnect()
        busy = true
        connectedName = target.name
        status = "正在启动蓝牙 HID…"
        hid = HidGamepad(context).also { gamepad ->
            gamepad.onStatus = { message -> post {
                status = message; busy = false
                if (message.startsWith("HID 已连接")) screen = AppScreen.PLAY
            }}
            gamepad.connect(target.device)
        }
        screen = AppScreen.PLAY
    }

    private fun connect(newConnection: CompanionConnection, name: String, code: String) {
        disconnect()
        busy = true; status = "正在连接 $name…"
        newConnection.onStatus = { message -> post { status = message } }
        newConnection.onRumble = ::rumble
        connection = newConnection
        io.execute {
            runCatching { newConnection.connect(code.ifBlank { null }) }
                .onSuccess { post { busy = false; connectedName = name; screen = AppScreen.PLAY } }
                .onFailure { error -> post {
                    busy = false; connection = null
                    status = error.message ?: "连接失败"
                    newConnection.close()
                }}
        }
    }

    fun publish(state: GamepadState) {
        val percent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 255
        lastState = state.copy(batteryPercent = percent)
        connection?.publish(lastState)
        hid?.publish(lastState)
    }

    fun pressHaptic() {
        if (settings.hapticStrength <= 0f) return
        vibrate(14, (settings.hapticStrength * 180 + 40).toInt())
    }

    private fun rumble(value: RumbleState) {
        val amplitude = maxOf(value.low, value.high)
        if (amplitude > 0) vibrate(value.durationMs.coerceIn(20, 1000).toLong(), amplitude)
        else vibrator().cancel()
    }

    private fun vibrate(duration: Long, amplitude: Int) {
        vibrator().vibrate(VibrationEffect.createOneShot(duration, amplitude.coerceIn(1, 255)))
    }

    private fun vibrator(): Vibrator = if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)

    fun saveLayout() { repository.saveLayout(layout); status = "布局已保存" }
    fun resetLayout() { layout = DefaultLayout.controls }
    fun saveSettings(value: ControllerSettings) { settings = value; repository.saveSettings(value); status = "设置已保存" }

    fun disconnect() {
        connection?.publish(GamepadState())
        connection?.close(); connection = null
        hid?.publish(GamepadState()); hid?.close(); hid = null
        connectedName = null; busy = false
        if (screen == AppScreen.PLAY) screen = AppScreen.CONNECT
    }

    private fun post(action: () -> Unit) = main.post(action)

    override fun close() {
        disconnect()
        io.shutdownNow()
    }
}


package com.mpad.controller.transport

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.mpad.controller.protocol.GamepadState
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HidGamepad(private val context: Context) : AutoCloseable {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var hid: BluetoothHidDevice? = null
    private var target: BluetoothDevice? = null
    private var requestedTarget: BluetoothDevice? = null
    var onStatus: ((String) -> Unit)? = null

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            onStatus?.invoke(if (registered) "HID 手柄已注册" else "HID 注册已释放")
            if (registered) requestedTarget?.let { connectNow(it) }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    target = device
                    onStatus?.invoke("HID 已连接：${safeName(device)}")
                }
                BluetoothProfile.STATE_CONNECTING -> onStatus?.invoke("正在连接 HID…")
                BluetoothProfile.STATE_DISCONNECTING -> onStatus?.invoke("正在断开 HID…")
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (target == device) target = null
                    onStatus?.invoke("HID 已断开")
                }
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            hid = proxy as? BluetoothHidDevice
            if (Build.VERSION.SDK_INT >= 31 &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                onStatus?.invoke("附近设备权限已被撤销")
                return
            }
            val settings = BluetoothHidDeviceAppSdpSettings(
                "MPad", "Android virtual gamepad", "MPad",
                BluetoothHidDevice.SUBCLASS2_GAMEPAD, REPORT_DESCRIPTOR)
            @SuppressLint("MissingPermission")
            val accepted = hid?.registerApp(settings, null, null, context.mainExecutor, callback) == true
            if (!accepted) onStatus?.invoke("此手机拒绝注册 HID Device Profile")
        }

        override fun onServiceDisconnected(profile: Int) {
            hid = null
            target = null
            onStatus?.invoke("HID 系统服务已断开")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        requestedTarget = device
        onStatus?.invoke("正在注册 HID 手柄…")
        if (hid != null) connectNow(device)
        else if (adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE) != true)
            onStatus?.invoke("此设备不支持蓝牙 HID Device Profile")
    }

    @SuppressLint("MissingPermission")
    private fun connectNow(device: BluetoothDevice) {
        if (hid?.connect(device) != true) onStatus?.invoke("HID 连接请求被系统拒绝，请先在系统蓝牙设置中配对电脑")
    }

    @SuppressLint("MissingPermission")
    fun publish(state: GamepadState) {
        val device = target ?: return
        val b = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort((state.buttons and 0xffff).toShort())
        b.put((state.hat and 0x0f).toByte())
        b.putShort(state.leftX); b.putShort(state.leftY)
        b.putShort(state.rightX); b.putShort(state.rightY)
        b.put(state.leftTrigger.toByte()); b.put(state.rightTrigger.toByte())
        hid?.sendReport(device, 1, b.array())
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        target?.let { try { hid?.disconnect(it) } catch (_: Exception) { } }
        try { hid?.unregisterApp() } catch (_: Exception) { }
        hid?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hid = null
        target = null
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice?) = try { device?.name ?: device?.address ?: "电脑" } catch (_: Exception) { "电脑" }

    companion object {
        private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

        // Generic Desktop / Game Pad: 16 buttons, hat, four signed 16-bit axes and two 8-bit triggers.
        private val REPORT_DESCRIPTOR = bytes(
            0x05,0x01, 0x09,0x05, 0xA1,0x01, 0x85,0x01,
            0x05,0x09, 0x19,0x01, 0x29,0x10, 0x15,0x00, 0x25,0x01,
            0x75,0x01, 0x95,0x10, 0x81,0x02,
            0x05,0x01, 0x09,0x39, 0x15,0x00, 0x25,0x07, 0x35,0x00,
            0x46,0x3B,0x01, 0x65,0x14, 0x75,0x04, 0x95,0x01, 0x81,0x42,
            0x75,0x04, 0x95,0x01, 0x81,0x03,
            0x09,0x30, 0x09,0x31, 0x09,0x33, 0x09,0x34,
            0x16,0x01,0x80, 0x26,0xFF,0x7F, 0x75,0x10, 0x95,0x04, 0x81,0x02,
            0x09,0x32, 0x09,0x35, 0x15,0x00, 0x26,0xFF,0x00,
            0x75,0x08, 0x95,0x02, 0x81,0x02, 0xC0)
    }
}

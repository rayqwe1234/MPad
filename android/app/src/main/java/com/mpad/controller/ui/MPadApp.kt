package com.mpad.controller.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpad.controller.AppController
import com.mpad.controller.AppScreen
import com.mpad.controller.ConnectionMode
import com.mpad.controller.data.ControlType
import com.mpad.controller.data.ControllerSettings
import com.mpad.controller.data.DefaultLayout
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val MPadDarkColors = darkColorScheme(
    primary = Color(0xFF7CFF6B), onPrimary = Color(0xFF0B0D11),
    background = Color(0xFF0B0D11), surface = Color(0xFF191D24),
    surfaceVariant = Color(0xFF252B34), onBackground = Color(0xFFF4F6FA),
    onSurface = Color(0xFFF4F6FA), onSurfaceVariant = Color(0xFFB7C0CD))

private val MPadLightColors = lightColorScheme(
    primary = Color(0xFF247A38), onPrimary = Color.White,
    background = Color(0xFFF5F8F4), surface = Color.White,
    surfaceVariant = Color(0xFFE6ECE5), onBackground = Color(0xFF171B18),
    onSurface = Color(0xFF171B18), onSurfaceVariant = Color(0xFF566159))

@Composable
fun MPadApp(controller: AppController, bluetoothPermission: Boolean, requestBluetoothPermission: () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) MPadDarkColors else MPadLightColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            BackHandler(controller.screen != AppScreen.CONNECT) { controller.screen = AppScreen.CONNECT }
            when (controller.screen) {
                AppScreen.CONNECT -> ConnectionScreen(controller, bluetoothPermission, requestBluetoothPermission)
                AppScreen.PLAY -> ControllerScreen(controller)
                AppScreen.EDIT_LAYOUT -> LayoutEditorScreen(controller)
                AppScreen.SETTINGS -> SettingsScreen(controller)
            }
        }
    }
}

@Composable
private fun ConnectionScreen(controller: AppController, bluetoothPermission: Boolean, requestPermission: () -> Unit) {
    var pairingCode by remember { mutableStateOf("") }
    var manualIp by remember { mutableStateOf("") }
    Scaffold { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val compactHeight = maxHeight < 420.dp
            val outsideHorizontal = if (compactHeight) 12.dp else 24.dp
            val outsideVertical = if (compactHeight) 8.dp else 24.dp
            val gap = if (compactHeight) 12.dp else 20.dp
            Row(Modifier.fillMaxSize().padding(horizontal = outsideHorizontal, vertical = outsideVertical),
                horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.width(if (compactHeight) 205.dp else 230.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(if (compactHeight) 5.dp else 10.dp)) {
                    Text("MPad", fontSize = if (compactHeight) 27.sp else 34.sp, fontWeight = FontWeight.Bold)
                    Text("手机模拟游戏手柄", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = if (compactHeight) 12.sp else 14.sp)
                    Spacer(Modifier.height(if (compactHeight) 2.dp else 12.dp))
                    ModeButton("局域网伴侣", controller.mode == ConnectionMode.LAN, compactHeight) {
                        controller.mode = ConnectionMode.LAN; controller.discoverLan()
                    }
                    ModeButton("蓝牙伴侣", controller.mode == ConnectionMode.BLUETOOTH, compactHeight) {
                        controller.mode = ConnectionMode.BLUETOOTH
                        if (bluetoothPermission) controller.refreshBluetooth() else requestPermission()
                    }
                    ModeButton("蓝牙 HID 直连", controller.mode == ConnectionMode.HID, compactHeight) {
                        controller.mode = ConnectionMode.HID
                        if (bluetoothPermission) controller.refreshBluetooth() else requestPermission()
                    }
                    Spacer(Modifier.weight(1f))
                    if (compactHeight) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { controller.screen = AppScreen.EDIT_LAYOUT },
                                modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(2.dp)) { Text("布局", fontSize = 12.sp) }
                            OutlinedButton(onClick = { controller.screen = AppScreen.SETTINGS },
                                modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(2.dp)) { Text("设置", fontSize = 12.sp) }
                        }
                    } else {
                        OutlinedButton(onClick = { controller.screen = AppScreen.EDIT_LAYOUT }, modifier = Modifier.fillMaxWidth()) { Text("编辑手柄布局") }
                        OutlinedButton(onClick = { controller.screen = AppScreen.SETTINGS }, modifier = Modifier.fillMaxWidth()) { Text("设置") }
                    }
                }

                Column(Modifier.weight(1f).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .padding(if (compactHeight) 12.dp else 20.dp)) {
                Text(when (controller.mode) {
                    ConnectionMode.LAN -> "局域网中的电脑"
                    ConnectionMode.BLUETOOTH -> "蓝牙伴侣设备"
                    ConnectionMode.HID -> "HID 直连设备"
                }, fontSize = if (compactHeight) 18.sp else 22.sp, fontWeight = FontWeight.SemiBold)
                Text(controller.status, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = if (compactHeight) 12.sp else 14.sp,
                    modifier = Modifier.padding(top = 3.dp, bottom = if (compactHeight) 6.dp else 12.dp))

                if (controller.mode != ConnectionMode.HID) {
                    OutlinedTextField(pairingCode, { pairingCode = it.filter(Char::isDigit).take(6) },
                        label = { Text("首次配对码（已配对可留空）") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                }

                if (controller.mode == ConnectionMode.LAN) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = controller::discoverLan, enabled = !controller.busy) { Text("重新搜索") }
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(manualIp, { manualIp = it }, label = { Text("手动 IP") },
                            singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { controller.connectManualIp(manualIp, pairingCode) }, enabled = !controller.busy) { Text("连接") }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(controller.lanTargets, key = { it.id }) { target ->
                            TargetRow(target.name, "${target.address.hostAddress} · ${if (target.driverReady) "XInput 就绪" else "需安装 ViGEm"}") {
                                controller.connectLan(target, pairingCode)
                            }
                        }
                    }
                } else {
                    if (!bluetoothPermission) Button(onClick = requestPermission) { Text("授予附近设备权限") }
                    else {
                        OutlinedButton(onClick = controller::refreshBluetooth) { Text("刷新已配对设备") }
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(controller.bluetoothTargets, key = { it.id }) { target ->
                                TargetRow(target.name, target.device.address) {
                                    if (controller.mode == ConnectionMode.HID) controller.connectHid(target)
                                    else controller.connectBluetooth(target, pairingCode)
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ControllerScreen(controller: AppController) {
    Box(Modifier.fillMaxSize()) {
        GamepadSurface(controller.layout, controller.settings, false,
            onStateChanged = controller::publish, onPressHaptic = controller::pressHaptic)
        Row(Modifier.align(Alignment.TopCenter).padding(10.dp)
            .background(Color(0xCC191D24), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(controller.connectedName ?: "MPad", color = Color(0xFF7CFF6B), fontWeight = FontWeight.Bold)
            Text("  ·  ${controller.status}", color = Color(0xFFB7C0CD), fontSize = 12.sp)
            Spacer(Modifier.width(14.dp))
            OutlinedButton(onClick = { controller.disconnect() }, modifier = Modifier.height(34.dp)) { Text("断开", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun LayoutEditorScreen(controller: AppController) {
    var selected by remember { mutableStateOf<ControlType?>(null) }
    val selectedControl = selected?.let { type -> controller.layout.firstOrNull { it.type == type } }
    val defaultControl = selected?.let { type -> DefaultLayout.controls.firstOrNull { it.type == type } }
    val selectedScale = if (selectedControl != null && defaultControl != null) {
        sqrt((selectedControl.width * selectedControl.height) / (defaultControl.width * defaultControl.height))
            .coerceIn(.5f, 2f)
    } else 1f
    Box(Modifier.fillMaxSize()) {
        GamepadSurface(controller.layout, controller.settings, true, selected,
            onSelected = { selected = it }, onLayoutChanged = { controller.layout = it })
        Row(Modifier.align(Alignment.TopCenter).padding(10.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .92f), RoundedCornerShape(18.dp)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("拖动控件移动；拖动右下绿色圆点缩放", modifier = Modifier.padding(horizontal = 8.dp))
            OutlinedButton(onClick = controller::resetLayout) { Text("恢复默认") }
            Button(onClick = { controller.saveLayout(); controller.screen = AppScreen.CONNECT }) { Text("保存") }
        }
        if (selected != null && selectedControl != null && defaultControl != null) {
            Row(Modifier.align(Alignment.BottomCenter).padding(10.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .94f), RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${selected!!.label} 大小", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = {
                    controller.layout = resizeControl(controller.layout, selected!!, selectedScale - .1f)
                }, modifier = Modifier.width(42.dp).height(36.dp), contentPadding = PaddingValues(0.dp)) { Text("－") }
                Slider(value = selectedScale, onValueChange = {
                    controller.layout = resizeControl(controller.layout, selected!!, it)
                }, valueRange = .5f..2f, modifier = Modifier.width(180.dp))
                OutlinedButton(onClick = {
                    controller.layout = resizeControl(controller.layout, selected!!, selectedScale + .1f)
                }, modifier = Modifier.width(42.dp).height(36.dp), contentPadding = PaddingValues(0.dp)) { Text("＋") }
                Text("${(selectedScale * 100).roundToInt()}%", fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(46.dp))
            }
        }
    }
}

private fun resizeControl(layout: List<com.mpad.controller.data.ControlSpec>, type: ControlType, requestedScale: Float): List<com.mpad.controller.data.ControlSpec> {
    val index = layout.indexOfFirst { it.type == type }
    val baseline = DefaultLayout.controls.firstOrNull { it.type == type }
    if (index < 0 || baseline == null) return layout
    val current = layout[index]
    val scale = requestedScale.coerceIn(.5f, 2f)
    val width = (baseline.width * scale).coerceIn(.045f, 1f)
    val height = (baseline.height * scale).coerceIn(.07f, 1f)
    val centerX = current.x + current.width / 2f
    val centerY = current.y + current.height / 2f
    val resized = current.copy(
        x = (centerX - width / 2f).coerceIn(0f, 1f - width),
        y = (centerY - height / 2f).coerceIn(0f, 1f - height),
        width = width,
        height = height,
    )
    return layout.toMutableList().also { it[index] = resized }
}

@Composable
private fun SettingsScreen(controller: AppController) {
    var value by remember { mutableStateOf(controller.settings) }
    Column(Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("手柄设置", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        SettingSlider("摇杆死区", value.deadzone, 0f..0.3f) { value = value.copy(deadzone = it) }
        SettingSlider("摇杆灵敏度", value.sensitivity, .5f..2f) { value = value.copy(sensitivity = it) }
        SettingSlider("控件透明度", value.opacity, .2f..1f) { value = value.copy(opacity = it) }
        SettingSlider("触键震感", value.hapticStrength, 0f..1f) { value = value.copy(hapticStrength = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { controller.screen = AppScreen.CONNECT }) { Text("取消") }
            Button(onClick = { controller.saveSettings(value); controller.screen = AppScreen.CONNECT }) { Text("保存") }
        }
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(130.dp))
        Slider(value, onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text("%.2f".format(value), fontFamily = FontFamily.Monospace, modifier = Modifier.width(55.dp))
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    Button(onClick, modifier = Modifier.fillMaxWidth().then(if (compact) Modifier.height(38.dp) else Modifier),
        contentPadding = if (compact) PaddingValues(horizontal = 8.dp, vertical = 0.dp) else ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)) {
        Text(text, fontSize = if (compact) 12.sp else 14.sp)
    }
}

@Composable
private fun TargetRow(name: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        Button(onClick) { Text("连接") }
    }
}

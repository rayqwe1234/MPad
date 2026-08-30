package com.mpad.controller.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import com.mpad.controller.data.ControlSpec
import com.mpad.controller.data.ControlType
import com.mpad.controller.data.ControllerSettings
import com.mpad.controller.protocol.Buttons
import com.mpad.controller.protocol.GamepadState
import com.mpad.controller.protocol.Hat
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private data class ActiveTouch(val control: ControlSpec, val start: Offset, val current: Offset)
private data class EditTouch(val index: Int, val start: Offset, val original: ControlSpec, val resizing: Boolean)

@Composable
fun GamepadSurface(
    layout: List<ControlSpec>,
    settings: ControllerSettings,
    editing: Boolean,
    selectedType: ControlType? = null,
    onSelected: (ControlType?) -> Unit = {},
    onLayoutChanged: (List<ControlSpec>) -> Unit = {},
    onStateChanged: (GamepadState) -> Unit = {},
    onPressHaptic: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val active = remember { mutableStateMapOf<PointerId, ActiveTouch>() }
    val latestLayout = rememberUpdatedState(layout)
    val latestSettings = rememberUpdatedState(settings)
    val editModifier = if (editing) {
        Modifier.pointerInput(editing) {
            awaitEachGesture {
                val gestureLayout = latestLayout.value
                val down = awaitPointerEvent().changes.firstOrNull { it.changedToDown() } ?: return@awaitEachGesture
                val hit = hitTest(gestureLayout, down.position, size.width.toFloat(), size.height.toFloat(), shapeAware = false)
                if (hit < 0) { onSelected(null); return@awaitEachGesture }
                down.consume()
                onSelected(gestureLayout[hit].type)
                val original = gestureLayout[hit]
                val rect = visualRectOf(original, size.width.toFloat(), size.height.toFloat())
                val resizing = down.position.x > rect.right - 36f && down.position.y > rect.bottom - 36f
                val edit = EditTouch(hit, down.position, original, resizing)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                        change.consume()
                        break
                    }
                    val delta = change.position - edit.start
                    val dx = delta.x / size.width
                    val dy = delta.y / size.height
                    val changed = if (edit.resizing) {
                        edit.original.copy(
                            width = (edit.original.width + dx).coerceIn(.045f, 1f - edit.original.x),
                            height = (edit.original.height + dy).coerceIn(.07f, 1f - edit.original.y))
                    } else {
                        edit.original.copy(
                            x = (edit.original.x + dx).coerceIn(0f, 1f - edit.original.width),
                            y = (edit.original.y + dy).coerceIn(0f, 1f - edit.original.height))
                    }
                    onLayoutChanged(gestureLayout.toMutableList().also { it[edit.index] = changed })
                    change.consume()
                }
            }
        }
    } else {
        Modifier.pointerInput(editing) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    var pressed = false
                    event.changes.forEach { change ->
                        if (change.changedToDown()) {
                            val currentLayout = latestLayout.value
                            val index = hitTest(currentLayout, change.position, size.width.toFloat(), size.height.toFloat(), shapeAware = true)
                            if (index >= 0) {
                                active[change.id] = ActiveTouch(currentLayout[index], change.position, change.position)
                                pressed = true
                                change.consume()
                            }
                        } else if (change.changedToUpIgnoreConsumed()) {
                            active.remove(change.id)
                            change.consume()
                        } else {
                            active[change.id]?.let {
                                active[change.id] = it.copy(current = change.position)
                                change.consume()
                            }
                        }
                    }
                    if (pressed) onPressHaptic()
                    onStateChanged(buildState(active.values, latestSettings.value, size.width.toFloat(), size.height.toFloat()))
                }
            }
        }
    }

    Canvas(modifier.fillMaxSize().then(editModifier)) {
        drawRect(Color(0xFF0B0D11))
        val alpha = settings.opacity.coerceIn(.2f, 1f)
        layout.sortedBy { it.z }.forEach { control ->
            val rect = visualRectOf(control, size.width, size.height)
            val isActive = active.values.any { it.control.type == control.type }
            val base = colorFor(control.type)
            val fill = if (isActive) base.copy(alpha = alpha) else Color(0xFF2A303A).copy(alpha = alpha)
            when (control.type) {
                ControlType.LEFT_STICK, ControlType.RIGHT_STICK -> {
                    val radius = minOf(rect.width, rect.height) * .46f
                    drawCircle(fill, radius, rect.center)
                    drawCircle(base.copy(alpha = .8f), radius, rect.center, style = Stroke(width = 4f))
                    val touch = active.values.firstOrNull { it.control.type == control.type }
                    val thumb = touch?.let { stickOffset(it.current, rect, radius * .72f) } ?: Offset.Zero
                    drawCircle(base.copy(alpha = .9f), radius * .42f, rect.center + thumb)
                }
                ControlType.DPAD -> {
                    val touch = active.values.firstOrNull { it.control.type == control.type }
                    drawDpad(rect, fill, base, touch?.let { hatAt(it.current, rect) } ?: Hat.NEUTRAL)
                }
                ControlType.LB, ControlType.RB -> {
                    val corner = androidx.compose.ui.geometry.CornerRadius(minOf(14f, rect.height * .24f))
                    drawRoundRect(fill, rect.topLeft, rect.size, cornerRadius = corner)
                    drawRoundRect(base.copy(alpha = .75f), rect.topLeft, rect.size,
                        cornerRadius = corner, style = Stroke(width = 3f))
                }
                ControlType.LT, ControlType.RT -> {
                    drawRoundRect(fill, rect.topLeft, rect.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f))
                    val touch = active.values.firstOrNull { it.control.type == control.type }
                    val value = touch?.let { triggerValue(it, rect) } ?: 0f
                    drawRoundRect(base.copy(alpha = .85f),
                        Offset(rect.left, rect.bottom - rect.height * value),
                        Size(rect.width, rect.height * value),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f))
                }
                else -> {
                    val radius = minOf(rect.width, rect.height) * .48f
                    drawCircle(fill, radius, rect.center)
                    drawCircle(base.copy(alpha = .75f), radius, rect.center, style = Stroke(width = 3f))
                }
            }
            if (control.type != ControlType.DPAD) {
                drawLabel(control.type.label, rect.center, minOf(rect.width, rect.height) * .25f)
            }
            if (editing && selectedType == control.type) {
                drawRect(Color(0xFF7CFF6B), rect.topLeft, rect.size, style = Stroke(width = 4f))
                drawCircle(Color(0xFF7CFF6B), 14f, rect.bottomRight)
            }
        }
    }
}

private fun hitTest(layout: List<ControlSpec>, point: Offset, width: Float, height: Float, shapeAware: Boolean): Int =
    layout.withIndex().filter {
        if (shapeAware) controlContains(it.value, point, width, height)
        else rectOf(it.value, width, height).contains(point)
    }
        .maxByOrNull { it.value.z }?.index ?: -1

internal fun controlContains(control: ControlSpec, point: Offset, width: Float, height: Float): Boolean {
    val rect = visualRectOf(control, width, height)
    if (!rect.contains(point)) return false
    return when (control.type) {
        ControlType.LEFT_STICK, ControlType.RIGHT_STICK ->
            hypot(point.x - rect.center.x, point.y - rect.center.y) <= minOf(rect.width, rect.height) * .46f
        ControlType.DPAD -> {
            val armWidth = minOf(rect.width, rect.height) * .34f
            kotlin.math.abs(point.x - rect.center.x) <= armWidth / 2f ||
                kotlin.math.abs(point.y - rect.center.y) <= armWidth / 2f
        }
        ControlType.LB, ControlType.RB -> roundedRectContains(rect, point, minOf(14f, rect.height * .24f))
        ControlType.LT, ControlType.RT -> roundedRectContains(rect, point, 16f)
        else -> hypot(point.x - rect.center.x, point.y - rect.center.y) <= minOf(rect.width, rect.height) * .48f
    }
}

private fun roundedRectContains(rect: Rect, point: Offset, requestedRadius: Float): Boolean {
    val radius = minOf(requestedRadius, rect.width / 2f, rect.height / 2f)
    val nearestX = point.x.coerceIn(rect.left + radius, rect.right - radius)
    val nearestY = point.y.coerceIn(rect.top + radius, rect.bottom - radius)
    return hypot(point.x - nearestX, point.y - nearestY) <= radius
}

private fun rectOf(c: ControlSpec, width: Float, height: Float) =
    Rect(c.x * width, c.y * height, (c.x + c.width) * width, (c.y + c.height) * height)

private fun visualRectOf(control: ControlSpec, width: Float, height: Float): Rect {
    val rect = rectOf(control, width, height)
    if (control.type != ControlType.DPAD) return rect
    val side = minOf(rect.width, rect.height)
    return Rect(
        rect.center.x - side / 2f,
        rect.center.y - side / 2f,
        rect.center.x + side / 2f,
        rect.center.y + side / 2f,
    )
}

private fun buildState(touches: Collection<ActiveTouch>, settings: ControllerSettings, width: Float, height: Float): GamepadState {
    var buttons = 0
    var hat = Hat.NEUTRAL
    var lx = 0f; var ly = 0f; var rx = 0f; var ry = 0f
    var lt = 0f; var rt = 0f
    touches.forEach { touch ->
        val rect = visualRectOf(touch.control, width, height)
        when (touch.control.type) {
            ControlType.A -> buttons = buttons or Buttons.A
            ControlType.B -> buttons = buttons or Buttons.B
            ControlType.X -> buttons = buttons or Buttons.X
            ControlType.Y -> buttons = buttons or Buttons.Y
            ControlType.LB -> buttons = buttons or Buttons.LB
            ControlType.RB -> buttons = buttons or Buttons.RB
            ControlType.BACK -> buttons = buttons or Buttons.BACK
            ControlType.START -> buttons = buttons or Buttons.START
            ControlType.GUIDE -> buttons = buttons or Buttons.GUIDE
            ControlType.L3 -> buttons = buttons or Buttons.L3
            ControlType.R3 -> buttons = buttons or Buttons.R3
            ControlType.DPAD -> hat = hatAt(touch.current, rect)
            ControlType.LT -> lt = maxOf(lt, triggerValue(touch, rect))
            ControlType.RT -> rt = maxOf(rt, triggerValue(touch, rect))
            ControlType.LEFT_STICK -> stickValue(touch.current, rect, settings).also { lx = it.x; ly = -it.y }
            ControlType.RIGHT_STICK -> stickValue(touch.current, rect, settings).also { rx = it.x; ry = -it.y }
        }
    }
    fun axis(value: Float) = (value.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
    return GamepadState(buttons = buttons, hat = hat, leftX = axis(lx), leftY = axis(ly),
        rightX = axis(rx), rightY = axis(ry), leftTrigger = (lt * 255).roundToInt(),
        rightTrigger = (rt * 255).roundToInt())
}

private fun stickValue(point: Offset, rect: Rect, settings: ControllerSettings): Offset {
    val radius = minOf(rect.width, rect.height) * .36f
    val raw = point - rect.center
    val distance = hypot(raw.x, raw.y)
    if (distance <= radius * settings.deadzone) return Offset.Zero
    val normalized = ((distance / radius - settings.deadzone) / (1f - settings.deadzone) * settings.sensitivity).coerceIn(0f, 1f)
    return if (distance == 0f) Offset.Zero else Offset(raw.x / distance * normalized, raw.y / distance * normalized)
}

private fun stickOffset(point: Offset, rect: Rect, maxRadius: Float): Offset {
    val raw = point - rect.center
    val distance = hypot(raw.x, raw.y)
    return if (distance <= maxRadius || distance == 0f) raw else Offset(raw.x / distance * maxRadius, raw.y / distance * maxRadius)
}

private fun triggerValue(touch: ActiveTouch, rect: Rect) =
    (1f - (touch.current.y - touch.start.y).coerceAtLeast(0f) / rect.height).coerceIn(0f, 1f)

private fun hatAt(point: Offset, rect: Rect): Int {
    val delta = point - rect.center
    if (hypot(delta.x, delta.y) < minOf(rect.width, rect.height) * .12f) return Hat.NEUTRAL
    val angle = (atan2(delta.y, delta.x) * 180 / PI + 360) % 360
    return when {
        angle < 22.5 || angle >= 337.5 -> Hat.EAST
        angle < 67.5 -> Hat.SOUTH_EAST
        angle < 112.5 -> Hat.SOUTH
        angle < 157.5 -> Hat.SOUTH_WEST
        angle < 202.5 -> Hat.WEST
        angle < 247.5 -> Hat.NORTH_WEST
        angle < 292.5 -> Hat.NORTH
        else -> Hat.NORTH_EAST
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDpad(rect: Rect, fill: Color, accent: Color, hat: Int) {
    val unit = minOf(rect.width, rect.height)
    val armWidth = unit * .34f
    val halfArm = armWidth / 2f
    val corner = androidx.compose.ui.geometry.CornerRadius(unit * .06f)
    val up = Rect(rect.center.x - halfArm, rect.top, rect.center.x + halfArm, rect.center.y)
    val down = Rect(rect.center.x - halfArm, rect.center.y, rect.center.x + halfArm, rect.bottom)
    val left = Rect(rect.left, rect.center.y - halfArm, rect.center.x, rect.center.y + halfArm)
    val right = Rect(rect.center.x, rect.center.y - halfArm, rect.right, rect.center.y + halfArm)
    val upActive = hat == Hat.NORTH || hat == Hat.NORTH_EAST || hat == Hat.NORTH_WEST
    val downActive = hat == Hat.SOUTH || hat == Hat.SOUTH_EAST || hat == Hat.SOUTH_WEST
    val leftActive = hat == Hat.WEST || hat == Hat.NORTH_WEST || hat == Hat.SOUTH_WEST
    val rightActive = hat == Hat.EAST || hat == Hat.NORTH_EAST || hat == Hat.SOUTH_EAST

    drawRoundRect(if (upActive) accent else fill, up.topLeft, up.size, corner)
    drawRoundRect(if (downActive) accent else fill, down.topLeft, down.size, corner)
    drawRoundRect(if (leftActive) accent else fill, left.topLeft, left.size, corner)
    drawRoundRect(if (rightActive) accent else fill, right.topLeft, right.size, corner)
    drawRect(fill, Offset(rect.center.x - halfArm, rect.center.y - halfArm), Size(armWidth, armWidth))

    drawDpadArrow(Offset(rect.center.x, rect.top + unit * .13f), 0f, if (upActive) Color.Black else accent)
    drawDpadArrow(Offset(rect.center.x, rect.bottom - unit * .13f), 180f, if (downActive) Color.Black else accent)
    drawDpadArrow(Offset(rect.left + unit * .13f, rect.center.y), -90f, if (leftActive) Color.Black else accent)
    drawDpadArrow(Offset(rect.right - unit * .13f, rect.center.y), 90f, if (rightActive) Color.Black else accent)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDpadArrow(center: Offset, rotation: Float, color: Color) {
    val radius = 9f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x - radius * .8f, center.y + radius * .55f)
        lineTo(center.x + radius * .8f, center.y + radius * .55f)
        close()
    }
    rotate(rotation, center) { drawPath(path, color) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(label: String, center: Offset, textSize: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        this.textSize = textSize.coerceAtLeast(12f)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(label, center.x, center.y - (paint.ascent() + paint.descent()) / 2, paint)
}

private fun colorFor(type: ControlType) = when (type) {
    ControlType.A -> Color(0xFF34C759)
    ControlType.B -> Color(0xFFFF453A)
    ControlType.X -> Color(0xFF0A84FF)
    ControlType.Y -> Color(0xFFFFD60A)
    else -> Color(0xFF7CFF6B)
}

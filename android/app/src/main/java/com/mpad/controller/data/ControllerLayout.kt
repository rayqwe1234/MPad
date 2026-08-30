package com.mpad.controller.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ControlType(val label: String) {
    LEFT_STICK("LS"), RIGHT_STICK("RS"), DPAD("D-PAD"),
    A("A"), B("B"), X("X"), Y("Y"),
    LB("LB"), RB("RB"), LT("LT"), RT("RT"),
    BACK("BACK"), START("START"), GUIDE("●"), L3("L3"), R3("R3")
}

data class ControlSpec(
    val type: ControlType,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val z: Int,
)

data class ControllerSettings(
    val deadzone: Float = 0.08f,
    val sensitivity: Float = 1f,
    val opacity: Float = 0.86f,
    val hapticStrength: Float = 0.55f,
)

object DefaultLayout {
    val controls = listOf(
        ControlSpec(ControlType.LB, .05f, .042f, .11f, .106f, 1),
        ControlSpec(ControlType.LT, .05f, .17f, .11f, .25f, 1),
        ControlSpec(ControlType.DPAD, .06f, .48f, .18f, .36f, 1),
        ControlSpec(ControlType.LEFT_STICK, .25f, .48f, .19f, .38f, 1),
        ControlSpec(ControlType.L3, .29f, .39f, .09f, .08f, 2),
        ControlSpec(ControlType.BACK, .42f, .11f, .08f, .09f, 1),
        ControlSpec(ControlType.GUIDE, .47f, .24f, .06f, .12f, 1),
        ControlSpec(ControlType.START, .50f, .11f, .08f, .09f, 1),
        ControlSpec(ControlType.RIGHT_STICK, .57f, .50f, .18f, .36f, 1),
        ControlSpec(ControlType.R3, .61f, .40f, .09f, .08f, 2),
        ControlSpec(ControlType.Y, .82f, .43f, .07f, .14f, 2),
        ControlSpec(ControlType.X, .75f, .56f, .07f, .14f, 2),
        ControlSpec(ControlType.B, .89f, .56f, .07f, .14f, 2),
        ControlSpec(ControlType.A, .82f, .69f, .07f, .14f, 2),
        ControlSpec(ControlType.RB, .84f, .042f, .11f, .106f, 1),
        ControlSpec(ControlType.RT, .84f, .17f, .11f, .25f, 1),
    )
}

class PreferencesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("mpad_preferences", Context.MODE_PRIVATE)

    fun loadLayout(): List<ControlSpec> = try {
        val source = prefs.getString("layout", null) ?: return DefaultLayout.controls
        val array = JSONArray(source)
        val loaded = buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(ControlSpec(ControlType.valueOf(o.getString("type")),
                    o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
                    o.getDouble("w").toFloat(), o.getDouble("h").toFloat(), o.getInt("z")))
            }
        }
        if (prefs.getInt("layout_version", 1) < 2) migrateShoulderButtons(loaded).also(::saveLayout) else loaded
    } catch (_: Exception) { DefaultLayout.controls }

    fun saveLayout(layout: List<ControlSpec>) {
        val array = JSONArray()
        layout.forEach { c ->
            array.put(JSONObject().apply {
                put("type", c.type.name); put("x", c.x); put("y", c.y)
                put("w", c.width); put("h", c.height); put("z", c.z)
            })
        }
        prefs.edit().putString("layout", array.toString()).putInt("layout_version", 2).apply()
    }

    private fun migrateShoulderButtons(layout: List<ControlSpec>): List<ControlSpec> {
        val lt = layout.firstOrNull { it.type == ControlType.LT }
        val rt = layout.firstOrNull { it.type == ControlType.RT }
        return layout.map { control ->
            when (control.type) {
                ControlType.LB -> lt?.let {
                    val height = control.height * .96f
                    control.copy(x = it.x, y = control.y + (control.height - height) / 2f,
                        width = it.width, height = height)
                } ?: control
                ControlType.RB -> rt?.let {
                    val height = control.height * .96f
                    control.copy(x = it.x, y = control.y + (control.height - height) / 2f,
                        width = it.width, height = height)
                } ?: control
                else -> control
            }
        }
    }

    fun loadSettings() = ControllerSettings(
        prefs.getFloat("deadzone", .08f), prefs.getFloat("sensitivity", 1f),
        prefs.getFloat("opacity", .86f), prefs.getFloat("haptics", .55f))

    fun saveSettings(value: ControllerSettings) {
        prefs.edit().putFloat("deadzone", value.deadzone).putFloat("sensitivity", value.sensitivity)
            .putFloat("opacity", value.opacity).putFloat("haptics", value.hapticStrength).apply()
    }
}

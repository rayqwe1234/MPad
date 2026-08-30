package com.mpad.controller.ui

import androidx.compose.ui.geometry.Offset
import com.mpad.controller.data.ControlSpec
import com.mpad.controller.data.ControlType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadHitTestTest {
    @Test
    fun circularButtonDoesNotUseItsRectangularCorners() {
        val button = ControlSpec(ControlType.A, 0f, 0f, .2f, .1f, 1)

        assertTrue(controlContains(button, Offset(100f, 25f), 1000f, 500f))
        assertFalse(controlContains(button, Offset(20f, 25f), 1000f, 500f))
        assertFalse(controlContains(button, Offset(100f, 49.5f), 1000f, 500f))
    }

    @Test
    fun dpadOnlyUsesTheCrossArms() {
        val dpad = ControlSpec(ControlType.DPAD, 0f, 0f, 1f, 1f, 1)

        assertTrue(controlContains(dpad, Offset(50f, 8f), 100f, 100f))
        assertTrue(controlContains(dpad, Offset(8f, 50f), 100f, 100f))
        assertFalse(controlContains(dpad, Offset(8f, 8f), 100f, 100f))
    }

    @Test
    fun dpadStaysSquareInsideAWideLayoutBox() {
        val dpad = ControlSpec(ControlType.DPAD, 0f, 0f, 1f, 1f, 1)

        assertTrue(controlContains(dpad, Offset(100f, 8f), 200f, 100f))
        assertFalse(controlContains(dpad, Offset(8f, 50f), 200f, 100f))
    }

    @Test
    fun triggerUsesItsRoundedCorners() {
        val trigger = ControlSpec(ControlType.LT, 0f, 0f, 1f, 1f, 1)

        assertTrue(controlContains(trigger, Offset(50f, 50f), 100f, 100f))
        assertFalse(controlContains(trigger, Offset(1f, 1f), 100f, 100f))
    }

    @Test
    fun shoulderButtonUsesWideRoundedRectangle() {
        val shoulder = ControlSpec(ControlType.LB, 0f, 0f, 1f, .4f, 1)

        assertTrue(controlContains(shoulder, Offset(10f, 20f), 100f, 100f))
        assertTrue(controlContains(shoulder, Offset(90f, 20f), 100f, 100f))
        assertFalse(controlContains(shoulder, Offset(1f, 1f), 100f, 100f))
    }
}

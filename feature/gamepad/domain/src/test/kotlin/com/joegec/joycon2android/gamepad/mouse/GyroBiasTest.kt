package com.joegec.joycon2android.gamepad.mouse

import org.junit.Assert.assertEquals
import org.junit.Test

class GyroBiasTest {

    private val bias = GyroBias()

    @Test
    fun `nothing is removed before the gyro has lain still`() {
        assertEquals(Triple(0.9f, 0f, 0f), bias.remove(0.9f, 0f, 0f))
    }

    @Test
    fun `a second of stillness becomes the offset`() {
        repeat(64) { bias.remove(0.9f, -0.2f, 0.1f) }

        val (x, y, z) = bias.remove(0.9f, -0.2f, 0.1f)

        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
        assertEquals(0f, z, 0.001f)
    }

    @Test
    fun `movement in the window starts it over`() {
        repeat(63) { bias.remove(0.9f, 0f, 0f) }
        bias.remove(30f, 0f, 0f)
        repeat(62) { bias.remove(0.9f, 0f, 0f) }

        assertEquals(0.9f, bias.remove(0.9f, 0f, 0f).first, 0.001f)
    }
}

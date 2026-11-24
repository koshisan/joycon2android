package com.joegec.joycon2android.gamepad.mouse

import com.joegec.joycon2android.model.JoyconInput
import org.junit.Assert.assertEquals
import org.junit.Test

class GyroPointerTest {

    private val pointer = GyroPointer()

    private val flat = JoyconInput(accelZ = ONE_G)
    private val upright = JoyconInput(accelY = -ONE_G)

    @Test
    fun `a still Joy-Con moves nothing`() {
        assertEquals(0 to 0, pointer.move(flat.copy(gyroX = 10, gyroY = -12, gyroZ = 14), elapsedMs = 100))
    }

    @Test
    fun `turning right while flat moves the pointer right`() {
        val (dx, dy) = pointer.move(flat.copy(gyroZ = -HUNDRED_DPS), elapsedMs = 100)

        assertEquals(246, dx) // 98.5 °/s past the dead zone, 0.1 s, 25 counts a degree
        assertEquals(0, dy)
    }

    @Test
    fun `turning about gravity moves the pointer sideways whatever the pose`() {
        val (dx, dy) = pointer.move(upright.copy(gyroY = HUNDRED_DPS), elapsedMs = 100)

        assertEquals(246, dx)
        assertEquals(0, dy)
    }

    @Test
    fun `nose up moves the pointer up`() {
        val (dx, dy) = pointer.move(flat.copy(gyroX = -HUNDRED_DPS), elapsedMs = 100)

        assertEquals(0, dx)
        assertEquals(-246, dy)
    }

    @Test
    fun `slow turns add up across reports`() {
        val slow = flat.copy(gyroZ = -41) // 2.5 °/s: a quarter of a count per 10 ms

        val moved = (1..8).sumOf { pointer.move(slow, elapsedMs = 10).first }

        assertEquals(2, moved)
    }

    @Test
    fun `a swing keeps the last resting pose as up`() {
        pointer.move(flat, elapsedMs = 10)

        val (dx, _) = pointer.move(JoyconInput(accelX = 3 * ONE_G, gyroZ = -HUNDRED_DPS), elapsedMs = 100)

        assertEquals(246, dx)
    }

    private companion object {
        const val ONE_G = 4096
        const val HUNDRED_DPS = 1639 // 100.04 °/s
    }
}

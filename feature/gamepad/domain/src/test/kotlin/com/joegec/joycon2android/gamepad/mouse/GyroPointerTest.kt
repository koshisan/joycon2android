package com.joegec.joycon2android.gamepad.mouse

import com.joegec.joycon2android.model.JoyconInput
import org.junit.Assert.assertEquals
import org.junit.Test

class GyroPointerTest {

    private val pointer = GyroPointer()

    // 40 counts a degree at any speed, nothing steadied: 100 °/s wants 4000 a second, which Android reaches from 1893.
    private val linear = GyroTuning(countsPerDegree = 40f, acceleration = 0f, steadyBelowDps = 0f)

    private val flat = JoyconInput(accelZ = ONE_G)
    private val upright = JoyconInput(accelY = -ONE_G)

    @Test
    fun `a Joy-Con that has lain still moves nothing, whatever its idle offset`() {
        val idle = flat.copy(gyroX = 10, gyroY = -12, gyroZ = 14)
        repeat(64) { pointer.move(idle, elapsedMs = 15, linear) }

        assertEquals(0 to 0, pointer.move(idle, elapsedMs = 100, linear))
    }

    @Test
    fun `turning right while flat moves the pointer right`() {
        val (dx, dy) = pointer.move(flat.copy(gyroZ = -HUNDRED_DPS), elapsedMs = 100, linear)

        assertEquals(189, dx)
        assertEquals(0, dy)
    }

    @Test
    fun `turning about gravity moves the pointer sideways whatever the pose`() {
        val (dx, dy) = pointer.move(upright.copy(gyroY = HUNDRED_DPS), elapsedMs = 100, linear)

        assertEquals(189, dx)
        assertEquals(0, dy)
    }

    @Test
    fun `a positive turn about x moves the pointer up`() {
        val (dx, dy) = pointer.move(flat.copy(gyroX = HUNDRED_DPS), elapsedMs = 100, linear)

        assertEquals(0, dx)
        assertEquals(-189, dy)
    }

    @Test
    fun `slow turns add up across reports`() {
        val slow = flat.copy(gyroZ = -10) // 0.61 °/s: a quarter of a count per 10 ms

        val moved = (1..8).sumOf { pointer.move(slow, elapsedMs = 10, linear).first }

        assertEquals(1, moved)
    }

    @Test
    fun `a swing keeps the last resting pose as up`() {
        pointer.move(flat, elapsedMs = 10, linear)

        val (dx, _) = pointer.move(JoyconInput(accelX = 3 * ONE_G, gyroZ = -HUNDRED_DPS), elapsedMs = 100, linear)

        assertEquals(189, dx)
    }

    @Test
    fun `full acceleration sends the turn as it is and leaves the curve to Android`() {
        val (dx, _) = pointer.move(flat.copy(gyroZ = -HUNDRED_DPS), elapsedMs = 100, linear.copy(acceleration = 1f))

        assertEquals(400, dx)
    }

    @Test
    fun `a turn under the steady speed moves less than its share`() {
        val steady = linear.copy(steadyBelowDps = 2f)
        val oneDps = flat.copy(gyroZ = -16) // 0.98 °/s, half the steady speed: half the movement

        val moved = (1..50).sumOf { pointer.move(oneDps, elapsedMs = 10, steady).first }

        assertEquals(9, moved) // 19 unsteadied
    }

    @Test
    fun `a turn over the steady speed is not slowed`() {
        val (dx, _) = pointer.move(flat.copy(gyroZ = -HUNDRED_DPS), elapsedMs = 100, linear.copy(steadyBelowDps = 2f))

        assertEquals(189, dx)
    }

    private companion object {
        const val ONE_G = 4096
        const val HUNDRED_DPS = 1639 // 100.04 °/s
    }
}

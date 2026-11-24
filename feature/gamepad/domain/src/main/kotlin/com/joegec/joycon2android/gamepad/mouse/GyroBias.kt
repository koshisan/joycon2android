package com.joegec.joycon2android.gamepad.mouse

import kotlin.math.abs

/** Learns a gyro's idle offset while it lies still, in °/s: docs/virtual-gamepad.md#gyro-mouse */
class GyroBias {

    private val offset = FloatArray(3)
    private val sum = FloatArray(3)
    private var still = 0

    fun remove(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        learn(x, y, z)
        return Triple(x - offset[0], y - offset[1], z - offset[2])
    }

    private fun learn(x: Float, y: Float, z: Float) {
        val moving = abs(x - offset[0]) > STILL_DPS || abs(y - offset[1]) > STILL_DPS || abs(z - offset[2]) > STILL_DPS
        if (moving) {
            still = 0
            sum.fill(0f)
            return
        }
        sum[0] += x
        sum[1] += y
        sum[2] += z
        if (++still < WINDOW) return
        for (axis in offset.indices) offset[axis] = sum[axis] / WINDOW
        still = 0
        sum.fill(0f)
    }

    private companion object {
        // Wider than the offset itself, up to 0.9 °/s, so the first window can find it.
        const val STILL_DPS = 1.2f

        // About a second at the 15 ms report interval.
        const val WINDOW = 64
    }
}

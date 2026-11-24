package com.joegec.joycon2android.gamepad.mouse

import com.joegec.joycon2android.model.JoyconInput
import kotlin.math.abs
import kotlin.math.sqrt

/** Pointer movement from a Joy-Con turned in the hand: docs/virtual-gamepad.md#gyro-mouse */
class GyroPointer {

    private var carryX = 0f
    private var carryY = 0f

    // Raw accel points up at rest; kept through a swing, when it no longer reads gravity alone.
    private var upX = 0f
    private var upY = 0f
    private var upZ = 1f

    fun move(input: JoyconInput, elapsedMs: Int): Pair<Int, Int> {
        trackUp(input)
        // Turning right is a negative turn about up, and nose up a negative turn about x.
        val yaw = -(input.gyroX * upX + input.gyroY * upY + input.gyroZ * upZ) * DPS_PER_LSB
        val pitch = input.gyroX * DPS_PER_LSB
        carryX += pastDeadZone(yaw) * COUNTS_PER_DEGREE * elapsedMs / 1000f
        carryY += pastDeadZone(pitch) * COUNTS_PER_DEGREE * elapsedMs / 1000f
        val dx = carryX.toInt()
        val dy = carryY.toInt()
        carryX -= dx
        carryY -= dy
        return dx to dy
    }

    fun forget() {
        carryX = 0f
        carryY = 0f
    }

    private fun trackUp(input: JoyconInput) {
        val x = input.accelX.toFloat()
        val y = input.accelY.toFloat()
        val z = input.accelZ.toFloat()
        val length = sqrt(x * x + y * y + z * z)
        if (length !in NEAR_ONE_G) return
        upX = x / length
        upY = y / length
        upZ = z / length
    }

    private fun pastDeadZone(dps: Float): Float =
        if (abs(dps) <= DEAD_ZONE_DPS) 0f else dps - DEAD_ZONE_DPS * if (dps > 0) 1 else -1

    private companion object {
        const val DPS_PER_LSB = 0.06103515625f
        const val COUNTS_PER_DEGREE = 25f

        // Above the idle offset, up to 0.9 °/s: docs/dsu-motion.md#motion-frame
        const val DEAD_ZONE_DPS = 1.5f
        val NEAR_ONE_G = 3072f..5120f
    }
}

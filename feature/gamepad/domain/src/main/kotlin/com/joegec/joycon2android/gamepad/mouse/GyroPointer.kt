package com.joegec.joycon2android.gamepad.mouse

import com.joegec.joycon2android.model.JoyconInput
import kotlin.math.sqrt

/** Pointer movement from a Joy-Con turned in the hand: docs/virtual-gamepad.md#gyro-mouse */
class GyroPointer {

    private var carryX = 0f
    private var carryY = 0f
    private val bias = GyroBias()

    // Raw accel points up at rest; kept through a swing, when it no longer reads gravity alone.
    private var upX = 0f
    private var upY = 0f
    private var upZ = 1f

    fun move(input: JoyconInput, elapsedMs: Int): Pair<Int, Int> {
        trackUp(input)
        val (x, y, z) = bias.remove(input.gyroX * DPS_PER_LSB, input.gyroY * DPS_PER_LSB, input.gyroZ * DPS_PER_LSB)
        // Turning right is a negative turn about up; tipping the top up a positive one about x.
        val yaw = -(x * upX + y * upY + z * upZ)
        val pitch = -x
        val wanted = sqrt(yaw * yaw + pitch * pitch) * COUNTS_PER_DEGREE
        if (wanted / COUNTS_PER_DEGREE < STILL_DPS) return 0 to 0
        val sent = AndroidPointerSpeed.before(wanted) / wanted * COUNTS_PER_DEGREE * elapsedMs / 1000f
        carryX += yaw * sent
        carryY += pitch * sent
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

    private companion object {
        const val DPS_PER_LSB = 0.06103515625f
        const val COUNTS_PER_DEGREE = 40f

        // Sensor noise left once the idle offset is gone.
        const val STILL_DPS = 0.3f
        val NEAR_ONE_G = 3072f..5120f
    }
}

package com.joegec.joycon2android.gamepad.mouse

import com.joegec.joycon2android.model.JoyconInput
import kotlin.math.PI
import kotlin.math.sqrt

/** Pointer movement from a Joy-Con turned in the hand: docs/virtual-gamepad.md#gyro-mouse */
class GyroPointer {

    private var carryX = 0f
    private var carryY = 0f
    private val bias = GyroBias()
    private var smoothYaw = 0f
    private var smoothPitch = 0f

    // Degrees turned that the low-pass has not let through yet.
    private var owedYaw = 0f
    private var owedPitch = 0f

    // Raw accel points up at rest; kept through a swing, when it no longer reads gravity alone.
    private var upX = 0f
    private var upY = 0f
    private var upZ = 1f

    fun move(input: JoyconInput, elapsedMs: Int, tuning: GyroTuning = GyroTuning()): Pair<Int, Int> {
        trackUp(input)
        val (x, y, z) = bias.remove(input.gyroX * DPS_PER_LSB, input.gyroY * DPS_PER_LSB, input.gyroZ * DPS_PER_LSB)
        // Turning right is a negative turn about up; tipping the top up a positive one about x.
        smooth(-(x * upX + y * upY + z * upZ), -x, elapsedMs, tuning.steadyHz)
        val yaw = smoothYaw
        val pitch = smoothPitch
        val turn = sqrt(yaw * yaw + pitch * pitch)
        if (turn < NOISE_DPS) return 0 to 0
        val countsPerDps = sentSpeed(turn, tuning) / turn * elapsedMs / 1000f
        carryX += yaw * countsPerDps
        carryY += pitch * countsPerDps
        val dx = carryX.toInt()
        val dy = carryY.toInt()
        carryX -= dx
        carryY -= dy
        return dx to dy
    }

    fun forget() {
        carryX = 0f
        carryY = 0f
        smoothYaw = 0f
        smoothPitch = 0f
        owedYaw = 0f
        owedPitch = 0f
    }

    /** A low-pass that opens with the turn's speed: tremor averages out, a real turn passes, late but whole. */
    private fun smooth(yaw: Float, pitch: Float, elapsedMs: Int, restingHz: Float) {
        if (restingHz <= 0f || elapsedMs <= 0) {
            smoothYaw = yaw
            smoothPitch = pitch
            return
        }
        val speed = sqrt(smoothYaw * smoothYaw + smoothPitch * smoothPitch)
        val seconds = elapsedMs / 1000f
        val step = 2f * PI.toFloat() * (restingHz + HZ_PER_DPS * speed) * seconds
        val share = step / (1f + step)
        owedYaw += yaw * seconds
        owedPitch += pitch * seconds
        smoothYaw = share * owedYaw / seconds
        smoothPitch = share * owedPitch / seconds
        owedYaw -= share * owedYaw
        owedPitch -= share * owedPitch
    }

    /** Counts a second to send for a turn of [dps], so that Android's curve leaves the tuned one. */
    private fun sentSpeed(dps: Float, tuning: GyroTuning): Float {
        val steadied = if (dps < tuning.steadyBelowDps) dps * dps / tuning.steadyBelowDps else dps
        val linear = steadied * tuning.countsPerDegree
        val wanted = linear * (1f + tuning.acceleration * (AndroidPointerSpeed.factor(linear) - 1f))
        return AndroidPointerSpeed.before(wanted)
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

        // Sensor noise left once the idle offset is gone.
        const val NOISE_DPS = 0.3f

        // How fast the low-pass opens: at 20 °/s it passes 10 Hz and no longer lags.
        const val HZ_PER_DPS = 0.5f
        val NEAR_ONE_G = 3072f..5120f
    }
}

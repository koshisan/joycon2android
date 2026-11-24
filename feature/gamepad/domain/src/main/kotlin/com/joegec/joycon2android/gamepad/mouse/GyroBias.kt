package com.joegec.joycon2android.gamepad.mouse

import kotlin.math.abs

/** Learns a gyro's idle offset while it lies still, in °/s: docs/virtual-gamepad.md#gyro-mouse */
class GyroBias {

    private val offset = FloatArray(3)
    private val sum = FloatArray(3)
    private val low = FloatArray(3)
    private val high = FloatArray(3)
    private var still = 0

    fun remove(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        learn(floatArrayOf(x, y, z))
        return Triple(x - offset[0], y - offset[1], z - offset[2])
    }

    private fun learn(rates: FloatArray) {
        if (still == 0) {
            rates.copyInto(low)
            rates.copyInto(high)
        }
        for (axis in rates.indices) {
            low[axis] = minOf(low[axis], rates[axis])
            high[axis] = maxOf(high[axis], rates[axis])
            sum[axis] += rates[axis]
        }
        if (rates.indices.any { abs(rates[it]) > MAX_OFFSET_DPS || high[it] - low[it] > MAX_SPREAD_DPS }) return restart()
        if (++still < WINDOW) return
        for (axis in offset.indices) offset[axis] = sum[axis] / WINDOW
        restart()
    }

    private fun restart() {
        still = 0
        sum.fill(0f)
    }

    private companion object {
        // Offsets up to 0.9 °/s were seen: docs/dsu-motion.md#motion-frame
        const val MAX_OFFSET_DPS = 1.5f

        // A hand turning this slowly still trembles more than a Joy-Con lying down.
        const val MAX_SPREAD_DPS = 0.5f

        // About a second at the 15 ms report interval.
        const val WINDOW = 64
    }
}

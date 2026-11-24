package com.joegec.joycon2android.gamepad.mouse

import kotlin.math.sqrt

/** Android's own pointer acceleration, and how to undo it: docs/virtual-gamepad.md#gyro-mouse */
object AndroidPointerSpeed {

    // Counts a second. Below LOW a mouse moves 1:1, from HIGH on times ACCELERATION, linear between.
    private const val LOW = 500f
    private const val HIGH = 3000f
    private const val ACCELERATION = 3f
    private const val SLOPE = (ACCELERATION - 1f) / (HIGH - LOW)

    /** What Android multiplies a mouse moving at [sent] by. */
    fun factor(sent: Float): Float = when {
        sent <= LOW -> 1f
        sent >= HIGH -> ACCELERATION
        else -> 1f + SLOPE * (sent - LOW)
    }

    /** The speed to send so that the pointer ends up moving at [wanted]. */
    fun before(wanted: Float): Float = when {
        wanted <= LOW -> wanted
        wanted >= HIGH * ACCELERATION -> wanted / ACCELERATION
        // sent * (1 + SLOPE * (sent - LOW)) = wanted
        else -> {
            val linear = 1f - SLOPE * LOW
            (-linear + sqrt(linear * linear + 4f * SLOPE * wanted)) / (2f * SLOPE)
        }
    }
}

package com.joegec.joycon2android.gamepad.mouse

import kotlin.math.pow

/** How a turn becomes pointer movement: docs/virtual-gamepad.md#gyro-mouse */
data class GyroTuning(
    val countsPerDegree: Float = 30f,
    // 0 moves the pointer the same distance per degree at any speed, 1 as far as Android's own acceleration would.
    val acceleration: Float = 0.5f,
    // Turns slower than this, in °/s, are scaled down towards nothing, which takes a hand's tremor out.
    val steadyBelowDps: Float = 1.5f,
    // A resting hand's turns are low-passed at this many Hz, which averages the tremor out; 0 leaves them alone.
    val steadyHz: Float = 1.7f,
) {
    companion object {
        /** Both steadying values for the Steadiness setting. */
        fun steadied(countsPerDegree: Float, acceleration: Float, steadiness: Float) = GyroTuning(
            countsPerDegree,
            acceleration,
            steadyBelowDps = 3f * steadiness,
            steadyHz = if (steadiness <= 0f) 0f else LOOSE_HZ * (TIGHT_HZ / LOOSE_HZ).pow(steadiness),
        )

        private const val LOOSE_HZ = 6f
        private const val TIGHT_HZ = 0.5f
    }
}

package com.joegec.joycon2android.gamepad.mouse

/** How a turn becomes pointer movement: docs/virtual-gamepad.md#gyro-mouse */
data class GyroTuning(
    val countsPerDegree: Float = 30f,
    // 0 moves the pointer the same distance per degree at any speed, 1 as far as Android's own acceleration would.
    val acceleration: Float = 0.5f,
    // Turns slower than this, in °/s, are scaled down towards nothing, which takes a hand's tremor out.
    val steadyBelowDps: Float = 1.5f,
)

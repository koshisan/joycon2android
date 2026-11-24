package com.joegec.joycon2android.settings

/** Each as the settings show it; what they do to the pointer: docs/virtual-gamepad.md#gyro-mouse */
data class GyroMouseTuning(
    val countsPerDegree: Int = 30,
    val accelerationPercent: Int = 50,
    val steadinessPercent: Int = 50,
)

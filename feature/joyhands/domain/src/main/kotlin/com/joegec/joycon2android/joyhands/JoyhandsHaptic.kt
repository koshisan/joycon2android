package com.joegec.joycon2android.joyhands

import com.joegec.joycon2android.model.Rumble
import com.joegec.joycon2android.model.Side

/** One vibration SteamVR asked a hand for: docs/joyhands.md#vibration */
data class JoyhandsHaptic(val side: Side, val durationMs: Int, val frequencyHz: Int, val amplitude: Float) {

    val rumble: Rumble
        get() {
            val strength = amplitude.coerceIn(0f, 1f) * STRENGTH
            val low = frequencyHz in 1 until HIGH_FROM_HZ
            return if (low) Rumble(strong = strength, weak = 0f) else Rumble(strong = 0f, weak = strength)
        }

    // SteamVR's shortest pulse is too short for the actuator to be felt.
    val playMs: Long get() = durationMs.coerceIn(SHORTEST_MS, LONGEST_MS).toLong()

    private companion object {
        const val STRENGTH = 0.5f
        const val HIGH_FROM_HZ = 200
        const val SHORTEST_MS = 40
        const val LONGEST_MS = 2000
    }
}

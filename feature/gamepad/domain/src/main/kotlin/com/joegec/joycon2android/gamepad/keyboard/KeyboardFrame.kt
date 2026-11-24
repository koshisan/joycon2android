package com.joegec.joycon2android.gamepad.keyboard

import com.joegec.joycon2android.model.AndroidKey
import com.joegec.joycon2android.model.KeyboardKey

data class KeyboardFrame(
    val androidKeys: Set<AndroidKey> = emptySet(),
    val keys: Set<KeyboardKey> = emptySet(),
) {
    companion object {
        /** The launcher has no key a keyboard could send: docs/virtual-gamepad.md#android-keys */
        val SENDABLE_KEYS = setOf(AndroidKey.HOME, AndroidKey.BACK, AndroidKey.SCREENSHOT)
    }
}

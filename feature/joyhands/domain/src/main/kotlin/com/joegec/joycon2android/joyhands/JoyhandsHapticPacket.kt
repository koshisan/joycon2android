package com.joegec.joycon2android.joyhands

import com.joegec.joycon2android.model.Side

/** `struct jh_haptic` in joyhands' puck/jcstream.c: docs/joyhands.md#vibration */
object JoyhandsHapticPacket {
    const val SIZE = 14
    const val PORT = 27818

    private const val MAGIC = 0x3156484A // "JHV1"
    private const val PROTOCOL = 1
    private const val FULL = 65535f

    fun parse(data: ByteArray, length: Int): JoyhandsHaptic? {
        if (length != SIZE) return null
        fun byte(at: Int) = data[at].toInt() and 0xFF
        fun short(at: Int) = byte(at) or (byte(at + 1) shl 8)
        if (short(0) or (short(2) shl 16) != MAGIC || byte(4) != PROTOCOL) return null
        val side = when (byte(5)) {
            0 -> Side.LEFT
            1 -> Side.RIGHT
            else -> return null
        }
        return JoyhandsHaptic(side, durationMs = short(6), frequencyHz = short(8), amplitude = short(10) / FULL)
    }
}

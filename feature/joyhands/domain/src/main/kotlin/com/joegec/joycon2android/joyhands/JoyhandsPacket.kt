package com.joegec.joycon2android.joyhands

import com.joegec.joycon2android.model.ConnectedJoycon
import com.joegec.joycon2android.model.Side

/** One Joy-Con's report as joyhands' sender takes it: `struct jh_ble` in joyhands' puck/jcstream.c. */
object JoyhandsPacket {
    const val SIZE = 41
    const val PORT = 27817

    private const val MAGIC = 0x3142484A // "JHB1"
    private const val PROTOCOL = 1
    private const val STICK_CENTRE = 2048
    private const val NO_BATTERY = 255

    // joyhands' button bits, the same for both hands
    private const val SHOULDER = 1 shl 0
    private const val TRIGGER = 1 shl 1
    private const val SL = 1 shl 2
    private const val SR = 1 shl 3
    private const val MENU = 1 shl 4
    private const val STICK = 1 shl 5
    private const val SYSTEM = 1 shl 6
    private const val SOUTH = 1 shl 7
    private const val EAST = 1 shl 8
    private const val WEST = 1 shl 9
    private const val NORTH = 1 shl 10
    private const val CHAT = 1 shl 11

    // pairs of the Joy-Con's bit (docs/protocol.md#buttons-uint32-at-0x03) and joyhands' bit
    private val LEFT = longArrayOf(
        0x40000000, SHOULDER.toLong(), 0x80000000, TRIGGER.toLong(), 0x20000000, SL.toLong(), 0x10000000, SR.toLong(),
        0x00010000, MENU.toLong(), 0x00080000, STICK.toLong(), 0x00200000, SYSTEM.toLong(),
        0x01000000, SOUTH.toLong(), 0x04000000, EAST.toLong(), 0x08000000, WEST.toLong(), 0x02000000, NORTH.toLong(),
    )
    private val RIGHT = longArrayOf(
        0x00004000, SHOULDER.toLong(), 0x00008000, TRIGGER.toLong(), 0x00002000, SL.toLong(), 0x00001000, SR.toLong(),
        0x00020000, MENU.toLong(), 0x00040000, STICK.toLong(), 0x00100000, SYSTEM.toLong(),
        0x00000400, SOUTH.toLong(), 0x00000800, EAST.toLong(), 0x00000100, WEST.toLong(), 0x00000200, NORTH.toLong(),
        0x00400000, CHAT.toLong(),
    )

    /** Fills [out] and returns true; false for anything that is not a left or right Joy-Con. */
    fun encode(controller: ConnectedJoycon, out: ByteArray): Boolean {
        val left = when (controller.side) {
            Side.LEFT -> true
            Side.RIGHT -> false
            else -> return false
        }
        val mac = controller.address.split(":").mapNotNull { it.toIntOrNull(16) }
        if (mac.size != 6) return false
        val input = controller.input
        var at = 0
        fun byte(v: Int) { out[at++] = v.toByte() }
        fun short(v: Int) { byte(v); byte(v shr 8) }
        fun int(v: Int) { short(v); short(v shr 16) }

        int(MAGIC)
        byte(PROTOCOL)
        byte(if (left) 0 else 1)
        mac.forEach(::byte)
        short(buttons(input.buttons, if (left) LEFT else RIGHT))
        // The stick is calibrated onto 0..4095 around 2048 and grows upwards. joyhands wants -32767..32767 with
        // right and down positive, as the kernel reports a Joy-Con 1.
        // A lone Joy-Con's stick is stickX/stickY on either side; the right pair is the Pro Controller's.
        short(axis(input.stickX - STICK_CENTRE))
        short(axis(STICK_CENTRE - input.stickY))
        short(input.accelX); short(input.accelY); short(input.accelZ) // 4096 per g on both generations
        int(milliDps(input.gyroX)); int(milliDps(input.gyroY)); int(milliDps(input.gyroZ))
        byte(input.battery?.percent?.coerceIn(0, 100) ?: NO_BATTERY)
        val color = controller.accentColor
        byte(if (color != null) 1 else 0)
        byte((color ?: 0) shr 16); byte((color ?: 0) shr 8); byte(color ?: 0)
        return true
    }

    // 0.061035 degrees a second per LSB: docs/dsu-motion.md#motion-frame
    private fun milliDps(raw: Int): Int = raw * 61035 / 1000

    private fun axis(fromCentre: Int): Int = (fromCentre * 16).coerceIn(-32767, 32767)

    private fun buttons(raw: Long, map: LongArray): Int {
        var bits = 0L
        for (i in map.indices step 2) if (raw and map[i] != 0L) bits = bits or map[i + 1]
        return bits.toInt()
    }
}

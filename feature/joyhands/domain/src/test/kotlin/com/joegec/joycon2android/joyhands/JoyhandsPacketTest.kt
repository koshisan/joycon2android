package com.joegec.joycon2android.joyhands

import com.joegec.joycon2android.model.BatteryCharge
import com.joegec.joycon2android.model.ConnectedJoycon
import com.joegec.joycon2android.model.JoyconConnectionState
import com.joegec.joycon2android.model.JoyconInput
import com.joegec.joycon2android.model.Side
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JoyhandsPacketTest {

    private val out = ByteArray(JoyhandsPacket.SIZE)

    private fun encode(side: Side, input: JoyconInput, color: Int? = null): ByteBuffer {
        val controller = ConnectedJoycon(
            address = "C8:91:43:E9:85:E8",
            side = side,
            deviceName = "",
            connectionState = JoyconConnectionState(accentColor = color),
            input = input,
        )
        assertTrue(JoyhandsPacket.encode(controller, out))
        return ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
    }

    @Test
    fun `header names the side and the address`() {
        val b = encode(Side.RIGHT, JoyconInput())
        assertEquals(0x3142484A, b.getInt(0))
        assertEquals(1, b.get(4).toInt())
        assertEquals(1, b.get(5).toInt())
        assertEquals(0xC8.toByte(), b.get(6))
        assertEquals(0xE8.toByte(), b.get(11))
    }

    @Test
    fun `left buttons map onto the shared bits`() {
        // ZL, stick click and d-pad up
        val b = encode(Side.LEFT, JoyconInput(buttons = 0x80000000L or 0x00080000L or 0x02000000L))
        assertEquals((1 shl 1) or (1 shl 5) or (1 shl 10), b.getShort(12).toInt())
    }

    @Test
    fun `right buttons map onto the shared bits`() {
        // A, home and C
        val b = encode(Side.RIGHT, JoyconInput(buttons = 0x00000800L or 0x00100000L or 0x00400000L))
        assertEquals((1 shl 8) or (1 shl 6) or (1 shl 11), b.getShort(12).toInt())
    }

    @Test
    fun `stick is centred with right and down positive`() {
        val rest = encode(Side.LEFT, JoyconInput())
        assertEquals(0, rest.getShort(14).toInt())
        assertEquals(0, rest.getShort(16).toInt())
        val upRight = encode(Side.LEFT, JoyconInput(stickX = 4095, stickY = 4095))
        assertEquals(32752, upRight.getShort(14).toInt())
        assertEquals(-32752, upRight.getShort(16).toInt())
    }

    @Test
    fun `a right Joy-Con's stick is read from the same fields as a left one's`() {
        val b = encode(Side.RIGHT, JoyconInput(stickX = 3048, stickY = 1048, rightStickX = 0))
        assertEquals(16000, b.getShort(14).toInt())
        assertEquals(16000, b.getShort(16).toInt())
    }

    @Test
    fun `gyro is sent in thousandths of a degree per second`() {
        val b = encode(Side.LEFT, JoyconInput(accelZ = 4096, gyroX = 1639, gyroZ = -32768))
        assertEquals(4096, b.getShort(22).toInt())
        assertEquals(100_036, b.getInt(24))
        assertEquals(-1_999_994, b.getInt(32))
    }

    @Test
    fun `battery and colour are marked when unknown`() {
        val unknown = encode(Side.LEFT, JoyconInput())
        assertEquals(255.toByte(), unknown.get(36))
        assertEquals(0, unknown.get(37).toInt())
        val known = encode(Side.LEFT, JoyconInput(battery = BatteryCharge(75)), color = 0x5F96E1)
        assertEquals(75, known.get(36).toInt())
        assertEquals(1, known.get(37).toInt())
        assertEquals(0x5F.toByte(), known.get(38))
        assertEquals(0xE1.toByte(), known.get(40))
    }

    @Test
    fun `a Pro Controller is not sent`() {
        val controller = ConnectedJoycon(address = "C8:91:43:E9:85:E8", side = Side.PRO, deviceName = "")
        assertFalse(JoyhandsPacket.encode(controller, out))
    }
}

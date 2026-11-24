package com.joegec.joycon2android.joyhands

import com.joegec.joycon2android.model.Rumble
import com.joegec.joycon2android.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoyhandsHapticTest {

    // JHV1, protocol 1, right, 150 ms, 160 Hz, amplitude 0x8000, padding
    private val packet =
        byteArrayOf(0x4A, 0x48, 0x56, 0x31, 1, 1, 150.toByte(), 0, 160.toByte(), 0, 0, 0x80.toByte(), 0, 0)

    @Test
    fun `a packet names the hand, the length, the pitch and the strength`() {
        val haptic = JoyhandsHapticPacket.parse(packet, packet.size)!!

        assertEquals(Side.RIGHT, haptic.side)
        assertEquals(150, haptic.durationMs)
        assertEquals(160, haptic.frequencyHz)
        assertEquals(0.5f, haptic.amplitude, 0.001f)
    }

    @Test
    fun `anything else is no vibration`() {
        assertNull(JoyhandsHapticPacket.parse(packet, packet.size - 1))
        assertNull(JoyhandsHapticPacket.parse(packet.clone().also { it[0] = 0 }, packet.size))
        assertNull(JoyhandsHapticPacket.parse(packet.clone().also { it[5] = 2 }, packet.size))
    }

    @Test
    fun `a low pitch plays on the strong motor at half the asked strength`() {
        assertEquals(Rumble(strong = 0.5f, weak = 0f), JoyhandsHaptic(Side.LEFT, 100, 160, 1f).rumble)
    }

    @Test
    fun `a high pitch plays on the weak motor`() {
        assertEquals(Rumble(strong = 0f, weak = 0.25f), JoyhandsHaptic(Side.LEFT, 100, 320, 0.5f).rumble)
    }

    @Test
    fun `a single pulse is stretched until it can be felt`() {
        assertEquals(40L, JoyhandsHaptic(Side.LEFT, 0, 160, 1f).playMs)
        assertEquals(300L, JoyhandsHaptic(Side.LEFT, 300, 160, 1f).playMs)
    }
}

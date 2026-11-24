package com.joegec.joycon2android.gamepad.mouse

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidPointerSpeedTest {

    // Android's curve, as dumpsys input prints it: 1x to 500, 3x from 3000, linear between.
    private fun accelerated(sent: Float): Float = sent * when {
        sent <= 500f -> 1f
        sent >= 3000f -> 3f
        else -> 1f + (sent - 500f) * 2f / 2500f
    }

    @Test
    fun `slow movement is sent as it is`() {
        assertEquals(300f, AndroidPointerSpeed.before(300f), 0.01f)
    }

    @Test
    fun `what is sent comes out of Android's curve at the wanted speed`() {
        for (wanted in listOf(500f, 800f, 2000f, 5000f, 8999f, 9000f, 20000f)) {
            assertEquals(wanted, accelerated(AndroidPointerSpeed.before(wanted)), wanted * 0.001f)
        }
    }
}

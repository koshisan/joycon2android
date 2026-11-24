package com.joegec.joycon2android.joyhands

import com.joegec.joycon2android.connection.ControllerRepository
import com.joegec.joycon2android.model.Rumble
import com.joegec.joycon2android.model.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Plays SteamVR's vibration on the Joy-Con of that hand, whether a player holds it or not. */
class JoyhandsVibration(private val scope: CoroutineScope, private val controllers: ControllerRepository) {

    private val stops = mutableMapOf<Side, Job>()

    /** Called from one coroutine at a time. A rumble holds until the next call, so each is stopped when its time is up. */
    fun play(haptic: JoyhandsHaptic) {
        val address = controllers.controllers.value.firstOrNull { it.side == haptic.side && it.ready }?.address ?: return
        stops.remove(haptic.side)?.cancel()
        controllers.rumble(address, haptic.rumble)
        stops[haptic.side] = scope.launch {
            delay(haptic.playMs)
            controllers.rumble(address, Rumble.OFF)
        }
    }
}

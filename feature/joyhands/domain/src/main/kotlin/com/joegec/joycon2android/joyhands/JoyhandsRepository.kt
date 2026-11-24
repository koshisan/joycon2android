package com.joegec.joycon2android.joyhands

import com.joegec.joycon2android.model.ConnectedJoycon
import kotlinx.coroutines.flow.Flow

/** The link to joyhands' sender on this device: docs/joyhands.md */
interface JoyhandsRepository {
    /** Listens only while collected. */
    val haptics: Flow<JoyhandsHaptic>

    fun push(controllers: List<ConnectedJoycon>)
}

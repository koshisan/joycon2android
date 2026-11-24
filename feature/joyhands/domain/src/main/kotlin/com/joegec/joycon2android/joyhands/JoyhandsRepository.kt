package com.joegec.joycon2android.joyhands

import com.joegec.joycon2android.model.ConnectedJoycon

/** Hands the Joy-Cons' reports to joyhands' sender on this device: docs/joyhands.md */
interface JoyhandsRepository {
    fun push(controllers: List<ConnectedJoycon>)
}

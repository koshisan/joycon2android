package com.joegec.joycon2android.joyhands

import com.joegec.joycon2android.model.ConnectedJoycon

class PushJoyhandsUseCase(private val repository: JoyhandsRepository) {
    operator fun invoke(controllers: List<ConnectedJoycon>) = repository.push(controllers)
}

package com.joegec.joycon2android.joyhands

import kotlinx.coroutines.flow.Flow

class ObserveJoyhandsHapticsUseCase(private val repository: JoyhandsRepository) {
    operator fun invoke(): Flow<JoyhandsHaptic> = repository.haptics
}

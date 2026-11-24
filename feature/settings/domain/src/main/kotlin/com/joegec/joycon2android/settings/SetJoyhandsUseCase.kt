package com.joegec.joycon2android.settings

class SetJoyhandsUseCase(private val repository: OutputSettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setJoyhands(enabled)
}

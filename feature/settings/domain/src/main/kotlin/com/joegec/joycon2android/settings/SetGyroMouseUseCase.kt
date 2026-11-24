package com.joegec.joycon2android.settings

class SetGyroMouseUseCase(private val repository: OutputSettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setGyroMouse(enabled)
}

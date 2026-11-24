package com.joegec.joycon2android.settings

class SetGyroMouseTuningUseCase(private val repository: OutputSettingsRepository) {
    suspend operator fun invoke(tuning: GyroMouseTuning) = repository.setGyroMouseTuning(tuning)
}

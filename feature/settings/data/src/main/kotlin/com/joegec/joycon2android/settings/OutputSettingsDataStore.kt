package com.joegec.joycon2android.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// File and keys predate the move out of DSU; renaming them would reset players' choices.
private val Context.outputSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "dsu_preferences")

class OutputSettingsDataStore(context: Context) : OutputSettingsRepository {

    private val dataStore = context.applicationContext.outputSettingsDataStore
    private val defaults = OutputSettings()

    override val settings: Flow<OutputSettings> = dataStore.data.map { prefs ->
        OutputSettings(
            fasterUpdates = prefs[FASTER_UPDATES] ?: defaults.fasterUpdates,
            blockDeviceMotion = prefs[BLOCK_DEVICE_MOTION] ?: defaults.blockDeviceMotion,
            joyhands = prefs[JOYHANDS] ?: defaults.joyhands,
            gyroMouse = prefs[GYRO_MOUSE] ?: defaults.gyroMouse,
        )
    }

    override suspend fun setFasterUpdates(enabled: Boolean) {
        dataStore.edit { it[FASTER_UPDATES] = enabled }
    }

    override suspend fun setBlockDeviceMotion(enabled: Boolean) {
        dataStore.edit { it[BLOCK_DEVICE_MOTION] = enabled }
    }

    override suspend fun setJoyhands(enabled: Boolean) {
        dataStore.edit { it[JOYHANDS] = enabled }
    }

    override suspend fun setGyroMouse(enabled: Boolean) {
        dataStore.edit { it[GYRO_MOUSE] = enabled }
    }

    private companion object {
        val FASTER_UPDATES = booleanPreferencesKey("fast_motion")
        val BLOCK_DEVICE_MOTION = booleanPreferencesKey("block_device_motion")
        val JOYHANDS = booleanPreferencesKey("joyhands")
        val GYRO_MOUSE = booleanPreferencesKey("gyro_mouse")
    }
}

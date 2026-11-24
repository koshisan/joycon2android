package com.joegec.joycon2android.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joegec.joycon2android.settings.GyroMouseTuning
import com.joegec.joycon2android.ui.components.SettingSlider
import com.joegec.joycon2android.ui.theme.Dimens
import kotlin.math.roundToInt

private const val SPEED_STEP = 5
private const val PERCENT_STEP = 10
private val SPEED_RANGE = 10f..80f
private val PERCENT_RANGE = 0f..100f

@Composable
internal fun GyroMouseTuningSliders(tuning: GyroMouseTuning, onChange: (GyroMouseTuning) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)) {
        SettingSlider(
            title = stringResource(R.string.settings_gyro_mouse_speed_title),
            description = stringResource(R.string.settings_gyro_mouse_speed_description),
            valueLabel = tuning.countsPerDegree.toString(),
            value = tuning.countsPerDegree.toFloat(),
            valueRange = SPEED_RANGE,
            steps = stepsBetween(SPEED_RANGE, SPEED_STEP),
            onValueChange = { onChange(tuning.copy(countsPerDegree = it.roundToInt())) },
            onValueChangeFinished = {},
        )
        SettingSlider(
            title = stringResource(R.string.settings_gyro_mouse_acceleration_title),
            description = stringResource(R.string.settings_gyro_mouse_acceleration_description),
            valueLabel = stringResource(R.string.settings_percent, tuning.accelerationPercent),
            value = tuning.accelerationPercent.toFloat(),
            valueRange = PERCENT_RANGE,
            steps = stepsBetween(PERCENT_RANGE, PERCENT_STEP),
            onValueChange = { onChange(tuning.copy(accelerationPercent = it.roundToInt())) },
            onValueChangeFinished = {},
        )
        SettingSlider(
            title = stringResource(R.string.settings_gyro_mouse_steadiness_title),
            description = stringResource(R.string.settings_gyro_mouse_steadiness_description),
            valueLabel = stringResource(R.string.settings_percent, tuning.steadinessPercent),
            value = tuning.steadinessPercent.toFloat(),
            valueRange = PERCENT_RANGE,
            steps = stepsBetween(PERCENT_RANGE, PERCENT_STEP),
            onValueChange = { onChange(tuning.copy(steadinessPercent = it.roundToInt())) },
            onValueChangeFinished = {},
        )
    }
}

// A Slider counts the stops between its ends.
private fun stepsBetween(range: ClosedFloatingPointRange<Float>, step: Int): Int =
    ((range.endInclusive - range.start) / step).roundToInt() - 1

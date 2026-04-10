/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.ui.composable

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.AudioSystem
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.modifiers.padding
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.brightness.ui.compose.Dimensions.IconPadding
import com.android.systemui.brightness.ui.compose.Dimensions.IconSize
import com.android.systemui.brightness.ui.compose.Dimensions.SliderBackgroundFrameSize
import com.android.systemui.brightness.ui.compose.Dimensions.SliderTrackRoundedCorner
import com.android.systemui.brightness.ui.compose.Dimensions.ThumbHeight
import com.android.systemui.brightness.ui.compose.Dimensions.ThumbWidth
import com.android.systemui.common.ringer.RingerModeInteractorImpl
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.haptics.slider.SliderHapticFeedbackFilter
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.ui.compose.QsHorizontalSliderTrack
import com.android.systemui.qs.ui.compose.QsSliderIconAlignment
import com.android.systemui.qs.ui.compose.qsSliderTrackCornerDp
import com.android.systemui.qs.ui.compose.rememberQsAutoBrightnessButtonVisible
import com.android.systemui.qs.ui.compose.rememberQsSliderColors
import com.android.systemui.qs.ui.compose.rememberQsSliderGradient
import com.android.systemui.qs.ui.compose.rememberQsSliderUseNewTint
import com.android.systemui.qs.ui.compose.rememberSliderShapeMode
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors
import com.android.systemui.volume.haptics.ui.VolumeHapticsConfigsProvider
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.SliderState
import kotlin.math.round
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QsStyledMediaVolumeSliderContent(
    state: SliderState,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onIconTapped: () -> Unit,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val showAutoBrightnessButton = rememberQsAutoBrightnessButtonVisible()
    val showRingerButton =
        showAutoBrightnessButton && !AudioSystem.isSingleVolume(context)

    if (state is SliderState.Empty) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(ThumbHeight)
                    .padding(vertical = { SliderBackgroundFrameSize.height.roundToPx() })
        )
        return
    }

    val value by qsVolumeSliderValueState(state)
    val interactionSource = remember { MutableInteractionSource() }
    val hapticsViewModel =
        setUpVolumeHapticsViewModel(
            value,
            state.valueRange,
            state.hapticFilter,
            interactionSource,
            hapticsViewModelFactory,
        )

    val shapeMode = rememberSliderShapeMode()
    val trackCornerDp = qsSliderTrackCornerDp(shapeMode)
    val gradient = rememberQsSliderGradient()
    val colors = rememberQsSliderColors(gradient)

    val iconPainter by
        produceState<Painter?>(initialValue = null, key1 = state.icon) {
            this@produceState.value =
                state.icon?.drawable?.toBitmap()?.asImageBitmap()?.let { BitmapPainter(it) }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = { SliderBackgroundFrameSize.height.roundToPx() })
                .sysuiResTag(state.label)
                .clearAndSetSemantics {
                    if (state.isEnabled) {
                        contentDescription = state.a11yContentDescription
                        state.a11yClickDescription?.let {
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(it) {
                                        onIconTapped()
                                        true
                                    }
                                )
                        }
                        state.a11yStateDescription?.let { stateDescription = it }
                        progressBarRangeInfo = ProgressBarRangeInfo(state.value, state.valueRange)
                    } else {
                        disabled()
                        contentDescription =
                            state.disabledMessage?.let { "${state.label}, $it" } ?: state.label
                    }
                    setProgress { targetValue ->
                        val targetDirection =
                            when {
                                targetValue > value -> 1
                                targetValue < value -> -1
                                else -> 0
                            }
                        val newValue =
                            (value + targetDirection * state.step).coerceIn(
                                state.valueRange.start,
                                state.valueRange.endInclusive,
                            )
                        onValueChange(newValue)
                        true
                    }
                }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Slider(
                    value = value,
                    valueRange = state.valueRange,
                    enabled = state.isEnabled,
                    colors = colors,
                    onValueChange = { newValue ->
                        hapticsViewModel?.addVelocityDataPoint(newValue)
                        onValueChange(newValue)
                    },
                    onValueChangeFinished = {
                        hapticsViewModel?.onValueChangeEnded()
                        onValueChangeFinished()
                    },
                    interactionSource = interactionSource,
                    modifier = Modifier.fillMaxWidth(),
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = interactionSource,
                            enabled = state.isEnabled,
                            thumbSize = DpSize(ThumbWidth, ThumbHeight),
                            colors = colors,
                        )
                    },
                    track = { sliderState ->
                        QsHorizontalSliderTrack(
                            sliderState = sliderState,
                            colors = colors,
                            trackCornerDp = trackCornerDp,
                            gradientBrush = gradient?.brush,
                            iconAlignment = QsSliderIconAlignment.End,
                            iconPainter = iconPainter,
                        )
                    },
                )

                if (state.isMutable && iconPainter != null) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.CenterEnd)
                                .padding(end = IconPadding)
                                .size(IconSize)
                                .clickable(
                                    onClick = onIconTapped,
                                    interactionSource = null,
                                    indication = null,
                                ),
                    )
                }
            }

            if (showRingerButton) {
                Spacer(modifier = Modifier.width(10.dp))
                QsRingerModeButton()
            }
        }
    }
}

@Composable
private fun QsRingerModeButton() {
    val context = LocalContext.current
    val view = LocalView.current
    val audioManager =
        remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val interactor =
        remember(context, audioManager) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            RingerModeInteractorImpl(context, audioManager, notificationManager)
        }
    val ringerMode by
        interactor.ringerMode.collectAsStateWithLifecycle(initialValue = interactor.getCurrentMode())
    val hasVibrator =
        remember(context) {
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.hasVibrator() == true
        }

    val isRingActive = ringerMode == AudioManager.RINGER_MODE_NORMAL

    val animatedCornerRadius by
        animateDpAsState(
            targetValue =
                if (isRingActive) {
                    SliderTrackRoundedCorner
                } else {
                    22.5.dp
                },
            label = "QsRingerButtonCornerRadius",
        )
    val shapeMode = rememberSliderShapeMode()
    val buttonShape =
        when (shapeMode) {
            1 -> CircleShape
            2 -> RoundedCornerShape(12.dp)
            3 -> RoundedCornerShape(0.dp)
            else -> RoundedCornerShape(animatedCornerRadius)
        }

    val useNewTint = rememberQsSliderUseNewTint()
    val gradient = rememberQsSliderGradient()
    val gradientBrush = gradient?.brush
    val activeBrush = if (isRingActive) gradientBrush else null
    val backgroundColor by
        animateColorAsState(
            targetValue =
                when {
                    activeBrush != null -> MaterialTheme.colorScheme.primary
                    isRingActive -> MaterialTheme.colorScheme.primary
                    useNewTint -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else -> LocalAndroidColorScheme.current.surfaceEffect1
                },
            label = "QsRingerButtonBackground",
        )
    val iconTint by
        animateColorAsState(
            targetValue =
                when {
                    isRingActive && gradient?.endColor != null ->
                        Color(
                            BatteryColors.textColorOnBackground(
                                context,
                                gradient.endColor.toArgb(),
                            )
                        )
                    isRingActive -> MaterialTheme.colorScheme.onPrimary
                    useNewTint -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            label = "QsRingerButtonIconTint",
        )

    val iconRes =
        if (isRingActive) {
            R.drawable.ic_speaker_on
        } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            R.drawable.ic_volume_ringer_vibrate
        } else {
            R.drawable.ic_speaker_mute
        }
    val statusDescription =
        when (ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> stringResource(R.string.volume_ringer_status_silent)
            AudioManager.RINGER_MODE_VIBRATE -> stringResource(R.string.volume_ringer_status_vibrate)
            else -> stringResource(R.string.volume_ringer_status_normal)
        }
    val contentDescription =
        stringResource(R.string.volume_ringer_drawer_closed_content_description, statusDescription)

    Box(
        modifier =
            Modifier.size(45.dp)
                .clip(buttonShape)
                .then(
                    if (activeBrush != null) {
                        Modifier.background(activeBrush)
                    } else {
                        Modifier.background(backgroundColor)
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        val newMode =
                            when (ringerMode) {
                                AudioManager.RINGER_MODE_NORMAL ->
                                    if (hasVibrator) {
                                        AudioManager.RINGER_MODE_VIBRATE
                                    } else {
                                        AudioManager.RINGER_MODE_SILENT
                                    }
                                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                                else -> AudioManager.RINGER_MODE_NORMAL
                            }
                        val hapticConstant =
                            when {
                                newMode == AudioManager.RINGER_MODE_NORMAL ->
                                    HapticFeedbackConstants.TOGGLE_ON
                                ringerMode == AudioManager.RINGER_MODE_NORMAL ->
                                    HapticFeedbackConstants.TOGGLE_OFF
                                else -> HapticFeedbackConstants.CONTEXT_CLICK
                            }
                        view.performHapticFeedback(hapticConstant)
                        if (newMode == AudioManager.RINGER_MODE_NORMAL) {
                            if (audioManager.getStreamVolume(AudioManager.STREAM_RING) == 0) {
                                audioManager.setStreamVolume(AudioManager.STREAM_RING, 1, 0)
                            }
                        }
                        interactor.setRingerMode(newMode)
                    },
                )
                .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
        )
    }
}

@Composable
private fun qsVolumeSliderValueState(state: SliderState): State<Float> {
    var prevState by remember { mutableStateOf(state) }
    val isVisible =
        LocalLifecycleOwner.current.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    val shouldSkipAnimation =
        prevState is SliderState.Empty ||
            prevState.isEnabled != state.isEnabled ||
            !isVisible
    val value =
        if (shouldSkipAnimation) remember(state.value) { mutableFloatStateOf(state.value) }
        else animateFloatAsState(targetValue = state.value, label = "QsVolumeSliderValueAnimation")
    prevState = state
    return value
}

@Composable
private fun setUpVolumeHapticsViewModel(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    hapticFilter: SliderHapticFeedbackFilter,
    interactionSource: MutableInteractionSource,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory?,
): SliderHapticsViewModel? {
    return hapticsViewModelFactory?.let {
        val configs =
            VolumeHapticsConfigsProvider.discreteConfigs(valueRange.stepSize(), hapticFilter)
        rememberViewModel(traceName = "QsVolumeSliderHapticsViewModel") {
                it.create(
                    interactionSource,
                    valueRange,
                    Orientation.Horizontal,
                    configs.hapticFeedbackConfig,
                    configs.sliderTrackerConfig,
                )
            }
            .also { hapticsViewModel ->
                var lastDiscreteStep by remember { mutableFloatStateOf(round(value)) }
                LaunchedEffect(value) {
                    snapshotFlow { value }
                        .map { round(it) }
                        .filter { it != lastDiscreteStep }
                        .distinctUntilChanged()
                        .collect { discreteStep ->
                            lastDiscreteStep = discreteStep
                            hapticsViewModel.onValueChange(discreteStep)
                        }
                }
            }
    }
}

private fun ClosedFloatingPointRange<Float>.stepSize(): Float = 1f / (endInclusive - start)

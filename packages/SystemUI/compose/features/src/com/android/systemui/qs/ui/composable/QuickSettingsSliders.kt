/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.ui.composable

import android.media.AudioManager
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settingslib.volume.shared.model.AudioStream
import com.android.systemui.res.R
import com.android.systemui.volume.panel.component.volume.domain.model.SliderType
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel

const val QS_MEDIA_VOLUME_SLIDER_TAG = "qs_media_volume_slider"

@Composable
fun QuickSettingsSliders(
    brightness: @Composable () -> Unit,
    volume: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isVolumeSliderEnabled: Boolean = isQsMediaVolumeSliderEnabled(),
) {
    if (isVolumeSliderEnabled) {
        Row(
            horizontalArrangement = spacedBy(dimensionResource(id = R.dimen.qs_split_sliders_gap)),
            modifier = modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.weight(1f)) { brightness() }
            Box(modifier = Modifier.weight(1f)) { volume() }
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) { brightness() }
    }
}

@Composable
fun QSMediaVolumeSlider(
    audioStreamSliderViewModelFactory: AudioStreamSliderViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel =
        remember(audioStreamSliderViewModelFactory, coroutineScope) {
            audioStreamSliderViewModelFactory.create(
                AudioStreamSliderViewModel.FactoryAudioStreamWrapper(
                    SliderType.Stream(AudioStream(AudioManager.STREAM_MUSIC)).stream
                ),
                coroutineScope,
            )
        }
    val sliderState by
        viewModel.slider.collectAsStateWithLifecycle(
            minActiveState = Lifecycle.State.CREATED
        )

    QsStyledMediaVolumeSliderContent(
        state = sliderState,
        onValueChange = { newValue -> viewModel.onValueChanged(sliderState, newValue) },
        onValueChangeFinished = { viewModel.onValueChangeFinished() },
        onIconTapped = { viewModel.toggleMuted(sliderState) },
        hapticsViewModelFactory = viewModel.getSliderHapticsViewModelFactory(),
        modifier = modifier.fillMaxWidth().testTag(QS_MEDIA_VOLUME_SLIDER_TAG),
    )
}

@Composable
fun isQsMediaVolumeSliderEnabled(): Boolean {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    fun readEnabled(): Boolean {
        return try {
            Settings.System.getIntForUser(
                contentResolver,
                Settings.System.QS_MEDIA_VOLUME_SLIDER_ENABLED,
                0,
                UserHandle.USER_CURRENT,
            ) == 1
        } catch (_: Throwable) {
            false
        }
    }

    var enabled by remember { mutableStateOf(readEnabled()) }

    DisposableEffect(contentResolver) {
        val observer =
            object : android.database.ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    context.mainExecutor.execute { enabled = readEnabled() }
                }
            }

        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.QS_MEDIA_VOLUME_SLIDER_ENABLED),
            false,
            observer,
            UserHandle.USER_ALL,
        )

        onDispose { contentResolver.unregisterContentObserver(observer) }
    }

    return enabled
}

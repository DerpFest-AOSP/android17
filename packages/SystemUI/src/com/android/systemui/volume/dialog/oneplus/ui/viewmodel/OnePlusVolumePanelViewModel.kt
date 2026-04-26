/*
 * Copyright (C) 2014-2026 The BlissRoms Project
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

package com.android.systemui.volume.dialog.oneplus.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.volume.Events
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialog
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogVisibilityInteractor
import com.android.systemui.volume.dialog.sliders.dagger.VolumeDialogSliderComponent
import com.android.systemui.volume.dialog.sliders.domain.interactor.VolumeDialogSlidersInteractor
import com.android.systemui.volume.dialog.sliders.domain.model.VolumeDialogSliderType
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@VolumeDialogScope
class OnePlusVolumePanelViewModel
@Inject
constructor(
    @Application private val context: Context,
    @VolumeDialog private val coroutineScope: CoroutineScope,
    slidersInteractor: VolumeDialogSlidersInteractor,
    private val sliderComponentFactory: VolumeDialogSliderComponent.Factory,
    private val visibilityInteractor: VolumeDialogVisibilityInteractor,
) {
    val isExpanded: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /**
     * Drives [FLAG_BLUR_BEHIND] in the view binder. Kept true briefly after [isExpanded] becomes
     * false so the window still blurs while [AnimatedContent] runs the exit transition (fade/scale);
     * clearing blur in the same frame as collapse removes the underlay during the exit animation
     * and can flash incorrectly.
     */
    val windowBlurEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private var clearWindowBlurJob: Job? = null

    val activeSliderComponent: StateFlow<VolumeDialogSliderComponent?> =
        slidersInteractor.sliders
            .map { sliderComponentFactory.create(it.slider) }
            .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    val expandedSliderComponents: List<VolumeDialogSliderComponent> = listOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_ALARM,
    ).map { sliderComponentFactory.create(VolumeDialogSliderType.Stream(it)) }

    /** Call when the volume dialog is shown (binder [Visible]) so state matches a fresh show. */
    fun resetForDialogShow() {
        clearWindowBlurJob?.cancel()
        isExpanded.value = false
        windowBlurEnabled.value = false
    }

    fun onExpandClicked() {
        clearWindowBlurJob?.cancel()
        isExpanded.value = true
        windowBlurEnabled.value = true
        visibilityInteractor.resetDismissTimeout()
    }

    fun onCollapseRequested() {
        isExpanded.value = false
        scheduleClearWindowBlurAfterExitAnimation()
        visibilityInteractor.resetDismissTimeout()
    }

    fun onBackPressed(): Boolean {
        if (isExpanded.value) {
            onCollapseRequested()
            return true
        }
        return false
    }

    fun onSettingsClicked() {
        clearWindowBlurJob?.cancel()
        isExpanded.value = false
        windowBlurEnabled.value = false
        val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        visibilityInteractor.dismissDialog(Events.DISMISS_REASON_SETTINGS_CLICKED)
    }

    fun onDismissRequested() {
        clearWindowBlurJob?.cancel()
        isExpanded.value = false
        windowBlurEnabled.value = false
        visibilityInteractor.dismissDialog(Events.DISMISS_REASON_TOUCH_OUTSIDE)
    }

    private fun scheduleClearWindowBlurAfterExitAnimation() {
        clearWindowBlurJob?.cancel()
        clearWindowBlurJob =
            coroutineScope.launch {
                // Match OnePlus [AnimatedContent] exit (fade + scale) approx. duration.
                delay(EXPANDED_EXIT_MS)
                if (!isExpanded.value) {
                    windowBlurEnabled.value = false
                }
            }
    }

    private companion object {
        private const val EXPANDED_EXIT_MS = 350L
    }
}

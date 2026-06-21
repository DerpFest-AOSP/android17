/*
 * SPDX-FileCopyrightText: The BlissRoms Project
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

    fun resetForDialogShow() {
        isExpanded.value = false
    }

    fun onExpandClicked() {
        isExpanded.value = true
        visibilityInteractor.resetDismissTimeout()
    }

    fun onCollapseRequested() {
        isExpanded.value = false
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
        isExpanded.value = false
        val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        visibilityInteractor.dismissDialog(Events.DISMISS_REASON_SETTINGS_CLICKED)
    }

    fun onDismissRequested() {
        isExpanded.value = false
        visibilityInteractor.dismissDialog(Events.DISMISS_REASON_TOUCH_OUTSIDE)
    }
}

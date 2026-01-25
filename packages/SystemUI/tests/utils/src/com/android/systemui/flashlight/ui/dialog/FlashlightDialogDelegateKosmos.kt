/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.flashlight.ui.dialog

import com.android.systemui.animation.dialogTransitionAnimator
import com.android.systemui.animation.mockDialogTransitionAnimator
import com.android.internal.logging.uiEventLogger
import com.android.systemui.flashlight.shared.logger.flashlightLogger
import com.android.systemui.flashlight.ui.viewmodel.FlashlightSliderViewModelLegacy
import com.android.systemui.flashlight.ui.viewmodel.flashlightSlicerViewModelFactory
import com.android.systemui.graphics.imageLoader
import com.android.systemui.haptics.slider.sliderHapticsViewModelFactory
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.mainCoroutineContext
import com.android.systemui.shade.data.repository.shadeDialogContextInteractor
import com.android.systemui.statusbar.phone.systemUIDialogFactory
import com.android.systemui.statusbar.policy.flashlightController
import org.mockito.kotlin.mock

val Kosmos.flashlightDialogDelegate: FlashlightDialogDelegate by
    Kosmos.Fixture {
        FlashlightDialogDelegate(
            mainCoroutineContext,
            systemUIDialogFactory,
            shadeDialogContextInteractor,
            dialogTransitionAnimator,
            flashlightSlicerViewModelFactory,
            flashlightSliderViewModelLegacyFactory,
            flashlightController,
            flashlightLogger,
        )
    }

val Kosmos.flashlightDialogDelegateWithMockAnimator: FlashlightDialogDelegate by
    Kosmos.Fixture {
        FlashlightDialogDelegate(
            mainCoroutineContext,
            systemUIDialogFactory,
            shadeDialogContextInteractor,
            mockDialogTransitionAnimator,
            flashlightSlicerViewModelFactory,
            flashlightSliderViewModelLegacyFactory,
            flashlightController,
            flashlightLogger,
        )
    }

val Kosmos.mockFlashlightDialogDelegate: FlashlightDialogDelegate by Kosmos.Fixture { mock() }

private val Kosmos.flashlightSliderViewModelLegacyFactory:
    FlashlightSliderViewModelLegacy.Factory by
    Kosmos.Fixture {
        object : FlashlightSliderViewModelLegacy.Factory {
            override fun create(): FlashlightSliderViewModelLegacy =
                FlashlightSliderViewModelLegacy(
                    sliderHapticsViewModelFactory,
                    flashlightController,
                    flashlightLogger,
                    uiEventLogger,
                    imageLoader,
                )
        }
    }

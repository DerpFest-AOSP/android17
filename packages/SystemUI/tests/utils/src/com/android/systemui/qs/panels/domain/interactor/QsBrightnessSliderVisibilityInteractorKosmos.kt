/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.domain.interactor

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.qs.panels.ui.model.QsSliderVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeQsBrightnessSliderVisibilityInteractor(
    initial: QsSliderVisibility = QsSliderVisibility.EXPANDED
) : QsBrightnessSliderVisibilityInteractor {
    private val _visibility = MutableStateFlow(initial)
    override val visibility: StateFlow<QsSliderVisibility> = _visibility.asStateFlow()

    override fun setVisibility(visibility: QsSliderVisibility) {
        _visibility.value = visibility
    }
}

val Kosmos.fakeQsBrightnessSliderVisibilityInteractor by
    Kosmos.Fixture { FakeQsBrightnessSliderVisibilityInteractor() }

var Kosmos.qsBrightnessSliderVisibilityInteractor: QsBrightnessSliderVisibilityInteractor by
    Kosmos.Fixture { fakeQsBrightnessSliderVisibilityInteractor }

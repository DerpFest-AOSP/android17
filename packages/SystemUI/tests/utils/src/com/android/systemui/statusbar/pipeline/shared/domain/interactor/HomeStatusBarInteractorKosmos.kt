/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.shared.domain.interactor

import android.provider.Settings
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.shared.settings.data.repository.systemSettingsRepository
import com.android.systemui.statusbar.disableflags.domain.interactor.disableFlagsInteractor
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.airplaneModeInteractor

val Kosmos.homeStatusBarInteractor: HomeStatusBarInteractor by
    Kosmos.Fixture {
        HomeStatusBarInteractor(
            airplaneModeInteractor,
            disableFlagsInteractor,
            systemSettingsRepository,
        )
    }

/** Set [Settings.System.LOCKSCREEN_SHOW_CARRIER] so the carrier shows on the status bar. */
suspend fun Kosmos.setHomeStatusBarInteractorShowOperatorName(show: Boolean) {
    // 2 = status bar only, 1 = lockscreen only (hidden in status bar)
    systemSettingsRepository.setInt(Settings.System.LOCKSCREEN_SHOW_CARRIER, if (show) 2 else 1)
}

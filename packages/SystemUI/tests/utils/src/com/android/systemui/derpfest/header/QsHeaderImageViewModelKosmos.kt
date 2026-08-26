/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.derpfest.header

import android.content.applicationContext
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.shared.settings.data.repository.systemSettingsRepository

val Kosmos.qsHeaderImageViewModel by Fixture {
    QsHeaderImageViewModel(
        context = applicationContext,
        shadeInteractor = shadeInteractor,
        systemSettingsRepository = systemSettingsRepository,
        scope = applicationCoroutineScope,
    )
}

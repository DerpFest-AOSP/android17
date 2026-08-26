/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.derpfest.header

import android.content.Context
import android.graphics.drawable.Drawable
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shared.settings.data.repository.SystemSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * OmniStyle QS header image state for scene-container Quick Settings.
 *
 * Legacy shade still owns this via [com.android.systemui.shade.NotificationPanelViewController].
 */
@SysUISingleton
class QsHeaderImageViewModel
@Inject
constructor(
    @ShadeDisplayAware context: Context,
    shadeInteractor: ShadeInteractor,
    systemSettingsRepository: SystemSettingsRepository,
    @Application scope: CoroutineScope,
) {
    private val headerMachine by lazy { StatusBarHeaderMachine(context) }

    private val _drawable = MutableStateFlow<Drawable?>(null)
    val drawable: StateFlow<Drawable?> = _drawable.asStateFlow()

    /** QS (tiles) expansion only — notifications shade stays 0 in dual shade. */
    val qsExpansion: StateFlow<Float> = shadeInteractor.qsExpansion

    val heightDp: StateFlow<Int> =
        systemSettingsRepository
            .intSetting(Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT, DEFAULT_HEIGHT_DP)
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_HEIGHT_DP)

    val shadow: StateFlow<Int> =
        systemSettingsRepository
            .intSetting(Settings.System.STATUS_BAR_CUSTOM_HEADER_SHADOW, 0)
            .stateIn(scope, SharingStarted.Eagerly, 0)

    init {
        if (SceneContainerFlag.isEnabled) {
            headerMachine.addObserver(
                object : StatusBarHeaderMachine.IStatusBarHeaderMachineObserver {
                    override fun updateHeader(headerImage: Drawable?, force: Boolean) {
                        _drawable.value = headerImage
                    }

                    override fun disableHeader() {
                        _drawable.value = null
                    }

                    override fun refreshHeader() {
                        // Shadow is applied as alpha in compose; drawable is unchanged.
                    }
                }
            )
            headerMachine.updateEnablement()
        }
    }

    companion object {
        const val DEFAULT_HEIGHT_DP = 142
        const val FADE_FRACTION = 0.555f
    }
}

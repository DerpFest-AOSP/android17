/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.weather

import com.android.systemui.plugins.BcSmartspaceDataPlugin

/**
 * Holds the lockscreen weather [BcSmartspaceDataPlugin] (same instance as decoupled
 * [com.google.android.systemui.smartspace.WeatherSmartspaceView]) so keyguard clock views can
 * subscribe without OmniJaw / custom content providers.
 */
object WeatherSmartspacePluginAccessor {
    @Volatile private var plugin: BcSmartspaceDataPlugin? = null

    fun setPlugin(p: BcSmartspaceDataPlugin?) {
        plugin = p
    }

    fun getPlugin(): BcSmartspaceDataPlugin? = plugin
}

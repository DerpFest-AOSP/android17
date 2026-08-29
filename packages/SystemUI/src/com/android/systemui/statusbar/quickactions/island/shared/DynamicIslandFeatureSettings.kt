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

package com.android.systemui.statusbar.quickactions.island.shared

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object DynamicIslandFeatureSettings {
    const val SHOW_DYNAMIC_ISLAND = Settings.System.STATUS_BAR_SHOW_DYNAMIC_ISLAND
    const val MEDIA_CONTROLS = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_MEDIA_CONTROLS
    const val SCREEN_RECORDING = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_SCREEN_RECORDING
    const val ALARMS = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_ALARMS
    const val FLASHLIGHT = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_FLASHLIGHT
    const val STOPWATCH = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_STOPWATCH
    const val LIVE_SCORES = Settings.System.STATUS_BAR_DYNAMIC_ISLAND_LIVE_SCORES

    fun ContentResolver.readDynamicIslandFeatureEnabled(
        key: String,
        defaultValue: Boolean = true,
    ): Boolean {
        return Settings.System.getIntForUser(
            this,
            key,
            if (defaultValue) 1 else 0,
            UserHandle.USER_CURRENT,
        ) != 0
    }

    fun observeDynamicIslandFeatureEnabled(
        context: Context,
        key: String,
        defaultValue: Boolean = true,
    ): Flow<Boolean> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(
                            context.contentResolver.readDynamicIslandFeatureEnabled(
                                key,
                                defaultValue,
                            )
                        )
                    }
                }

            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(key),
                false,
                observer,
                UserHandle.USER_ALL,
            )
            trySend(context.contentResolver.readDynamicIslandFeatureEnabled(key, defaultValue))
            awaitClose { context.contentResolver.unregisterContentObserver(observer) }
        }

    fun observeDynamicIslandEnabled(context: Context): Flow<Boolean> =
        observeDynamicIslandFeatureEnabled(context, SHOW_DYNAMIC_ISLAND, defaultValue = false)
}
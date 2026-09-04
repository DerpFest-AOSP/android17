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

package com.android.systemui.qs.panels.data.repository

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.database.ContentObserver
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.common.ui.data.repository.ConfigurationRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.util.kotlin.mapDirect
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge

@SysUISingleton
class QuickQuickSettingsRowRepository
@Inject
constructor(
    @Application private val context: Context,
    @ShadeDisplayAware private val resources: Resources,
    @ShadeDisplayAware private val configurationRepository: ConfigurationRepository,
) {
    private fun settingsChanges(vararg keys: String): Flow<Unit> = callbackFlow {
        val observer =
            object : ContentObserver(/* handler */ null) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    trySend(Unit)
                }
            }
        val cr = context.contentResolver
        keys.forEach {
            cr.registerContentObserver(
                Settings.System.getUriFor(it),
                /* notifyForDescendants */ false,
                observer,
                UserHandle.USER_ALL,
            )
        }
        awaitClose { cr.unregisterContentObserver(observer) }
    }

    private fun readRows(): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val key =
            if (isLandscape) Settings.System.QQS_LAYOUT_ROWS_LANDSCAPE
            else Settings.System.QQS_LAYOUT_ROWS
        val def = resources.getInteger(R.integer.quick_qs_paginated_grid_num_rows)
        return Settings.System.getIntForUser(
                context.contentResolver,
                key,
                def,
                UserHandle.USER_CURRENT,
            )
            .coerceAtLeast(1)
    }

    val rows: Flow<Int> =
        merge(
                configurationRepository.onConfigurationChange,
                settingsChanges(
                    Settings.System.QQS_LAYOUT_ROWS,
                    Settings.System.QQS_LAYOUT_ROWS_LANDSCAPE,
                ),
            )
            .emitOnStart()
            .mapDirect { readRows() }
            .distinctUntilChanged()

    val defaultRows: Int = resources.getInteger(R.integer.quick_qs_paginated_grid_num_rows)

    /**
     * Row count for QQS when using classic circular tiles (`qqs_layout_rows_classic` /
     * `qqs_layout_rows_landscape_classic`).
     */
    val classicQqsRows: Flow<Int> =
        merge(
                configurationRepository.onConfigurationChange,
                settingsChanges(
                    Settings.System.QQS_LAYOUT_ROWS_CLASSIC,
                    Settings.System.QQS_LAYOUT_ROWS_LANDSCAPE_CLASSIC,
                ),
            )
            .emitOnStart()
            .mapDirect { readClassicQqsRows() }
            .distinctUntilChanged()

    val defaultClassicQqsRows: Int
        get() = resources.getInteger(R.integer.quick_qs_paginated_grid_num_rows)

    private fun readClassicQqsRows(): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val portraitValue =
            Settings.System.getIntForUser(
                context.contentResolver,
                Settings.System.QQS_LAYOUT_ROWS_CLASSIC,
                0,
                UserHandle.USER_CURRENT,
            )
        val landscapeValue =
            Settings.System.getIntForUser(
                context.contentResolver,
                Settings.System.QQS_LAYOUT_ROWS_LANDSCAPE_CLASSIC,
                0,
                UserHandle.USER_CURRENT,
            )
        val value =
            if (isLandscape && landscapeValue > 0) {
                landscapeValue
            } else if (portraitValue > 0) {
                portraitValue
            } else {
                defaultClassicQqsRows
            }
        return value.coerceAtLeast(1)
    }
}

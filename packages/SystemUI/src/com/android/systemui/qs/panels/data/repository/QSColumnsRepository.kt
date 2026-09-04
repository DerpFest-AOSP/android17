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
import androidx.annotation.IntegerRes
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
class QSColumnsRepository
@Inject
constructor(
    @Application private val context: Context,
    @ShadeDisplayAware private val resources: Resources,
    @ShadeDisplayAware private val configurationRepository: ConfigurationRepository,
) {
    private fun settingsChanges(vararg keys: String): Flow<Unit> = callbackFlow {
        val observer =
            object : ContentObserver(null) {
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

    private fun readColumnsWithDefault(@IntegerRes defaultResId: Int): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val key =
            if (isLandscape) Settings.System.QS_LAYOUT_COLUMNS_LANDSCAPE
            else Settings.System.QS_LAYOUT_COLUMNS
        val def = resources.getInteger(defaultResId)
        return Settings.System.getIntForUser(
                context.contentResolver,
                key,
                def,
                UserHandle.USER_CURRENT,
            )
            .coerceAtLeast(1)
    }

    private fun reactiveColumns(@IntegerRes defaultResId: Int): Flow<Int> =
        merge(
                configurationRepository.onConfigurationChange,
                settingsChanges(
                    Settings.System.QS_LAYOUT_COLUMNS,
                    Settings.System.QS_LAYOUT_COLUMNS_LANDSCAPE,
                ),
            )
            .emitOnStart()
            .mapDirect { readColumnsWithDefault(defaultResId) }

    private fun resourceColumns(@IntegerRes resId: Int): Flow<Int> =
        configurationRepository.onConfigurationChange.emitOnStart().mapDirect {
            resources.getInteger(resId)
        }

    val columns: Flow<Int> = reactiveColumns(R.integer.quick_settings_infinite_grid_num_columns)

    // The layout setting only applies to combined quick settings. Separate and split shade panels
    // are sized for their own narrower containers, so they stay on their configured values.
    val splitShadeColumns: Flow<Int> =
        resourceColumns(R.integer.quick_settings_split_shade_num_columns)

    val dualShadeColumns: Flow<Int> =
        resourceColumns(R.integer.quick_settings_dual_shade_num_columns)

    val defaultColumns: Int =
        resources.getInteger(R.integer.quick_settings_infinite_grid_num_columns)

    /**
     * Column count for classic circular QS (`qs_panel_style` = 1), with fallbacks to
     * [R.integer.quick_settings_num_columns_classic] /
     * [R.integer.quick_settings_num_columns_classic_landscape].
     */
    val classicColumns: Flow<Int> =
        classicColumnsFlow(
            Settings.System.QS_LAYOUT_COLUMNS_CLASSIC,
            Settings.System.QS_LAYOUT_COLUMNS_LANDSCAPE_CLASSIC,
        )

    /**
     * Column count for QQS when using classic circular tiles (separate from expanded QS columns).
     */
    val classicQqsColumns: Flow<Int> =
        classicColumnsFlow(
            Settings.System.QQS_LAYOUT_COLUMNS_CLASSIC,
            Settings.System.QQS_LAYOUT_COLUMNS_LANDSCAPE_CLASSIC,
        )

    val defaultClassicColumns: Int
        get() =
            resources.getInteger(
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    R.integer.quick_settings_num_columns_classic_landscape
                } else {
                    R.integer.quick_settings_num_columns_classic
                }
            )

    private fun classicColumnsFlow(portraitKey: String, landscapeKey: String): Flow<Int> =
        merge(
                configurationRepository.onConfigurationChange,
                settingsChanges(portraitKey, landscapeKey),
            )
            .emitOnStart()
            .mapDirect { readClassicColumns(portraitKey, landscapeKey) }
            .distinctUntilChanged()

    private fun readClassicColumns(portraitKey: String, landscapeKey: String): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val portraitValue =
            Settings.System.getIntForUser(
                context.contentResolver,
                portraitKey,
                0,
                UserHandle.USER_CURRENT,
            )
        val landscapeValue =
            Settings.System.getIntForUser(
                context.contentResolver,
                landscapeKey,
                0,
                UserHandle.USER_CURRENT,
            )
        val value =
            if (isLandscape && landscapeValue > 0) {
                landscapeValue
            } else if (portraitValue > 0) {
                portraitValue
            } else {
                defaultClassicColumns
            }
        return value.coerceAtLeast(1)
    }
}

/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
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
class QSPaginatedRowsRepository
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

    private fun readRows(): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val key =
            if (isLandscape) Settings.System.QS_LAYOUT_ROWS_LANDSCAPE
            else Settings.System.QS_LAYOUT_ROWS
        val def = resources.getInteger(R.integer.quick_settings_paginated_grid_num_rows)
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
                    Settings.System.QS_LAYOUT_ROWS,
                    Settings.System.QS_LAYOUT_ROWS_LANDSCAPE,
                ),
            )
            .emitOnStart()
            .mapDirect { readRows() }

    val defaultRows: Int = resources.getInteger(R.integer.quick_settings_paginated_grid_num_rows)

    /**
     * Rows per page for classic circular QS (`qs_panel_style` = 1), with fallbacks to
     * [R.integer.quick_settings_paginated_grid_num_rows_classic] /
     * [R.integer.quick_settings_paginated_grid_num_rows_classic_landscape].
     */
    val classicRows: Flow<Int> =
        merge(
                configurationRepository.onConfigurationChange,
                settingsChanges(
                    Settings.System.QS_LAYOUT_ROWS_CLASSIC,
                    Settings.System.QS_LAYOUT_ROWS_LANDSCAPE_CLASSIC,
                ),
            )
            .emitOnStart()
            .mapDirect { readClassicRows() }
            .distinctUntilChanged()

    val defaultClassicRows: Int
        get() =
            resources.getInteger(
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    R.integer.quick_settings_paginated_grid_num_rows_classic_landscape
                } else {
                    R.integer.quick_settings_paginated_grid_num_rows_classic
                }
            )

    private fun readClassicRows(): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val portraitValue =
            Settings.System.getIntForUser(
                context.contentResolver,
                Settings.System.QS_LAYOUT_ROWS_CLASSIC,
                0,
                UserHandle.USER_CURRENT,
            )
        val landscapeValue =
            Settings.System.getIntForUser(
                context.contentResolver,
                Settings.System.QS_LAYOUT_ROWS_LANDSCAPE_CLASSIC,
                0,
                UserHandle.USER_CURRENT,
            )
        val value =
            if (isLandscape && landscapeValue > 0) {
                landscapeValue
            } else if (portraitValue > 0) {
                portraitValue
            } else {
                defaultClassicRows
            }
        return value.coerceAtLeast(1)
    }
}

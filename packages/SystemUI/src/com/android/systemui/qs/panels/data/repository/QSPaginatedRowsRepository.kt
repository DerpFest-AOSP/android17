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
import kotlinx.coroutines.flow.merge

@SysUISingleton
class QSPaginatedRowsRepository
@Inject
constructor(
    @Application private val context: Context,
    @ShadeDisplayAware private val resources: Resources,
    @ShadeDisplayAware private val configurationRepository: ConfigurationRepository,
) {
    private fun settingsChanges(): Flow<Unit> = callbackFlow {
        val uris: List<Uri> =
            listOf(
                Settings.System.getUriFor(Settings.System.QS_LAYOUT_ROWS),
                Settings.System.getUriFor(Settings.System.QS_LAYOUT_ROWS_LANDSCAPE),
            )
        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    trySend(Unit)
                }
            }
        val cr = context.contentResolver
        uris.forEach {
            cr.registerContentObserver(
                it,
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
        merge(configurationRepository.onConfigurationChange, settingsChanges())
            .emitOnStart()
            .mapDirect { readRows() }

    val defaultRows: Int = resources.getInteger(R.integer.quick_settings_paginated_grid_num_rows)
}

/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.ims.data.repository

import android.content.Context
import android.database.ContentObserver
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Reads the user's choice of integrated vs dedicated IMS status bar indicators. */
interface DedicatedImsStyleRepository {
    /**
     * `true` when dedicated VoLTE / VoNR / VoWiFi bindable icons are active; `false` for integrated
     * HD + Wi-Fi row VoWiFi.
     */
    val isDedicatedImsIconStyle: StateFlow<Boolean>
}

@SysUISingleton
class DedicatedImsStyleRepositoryImpl
@Inject
constructor(
    @Application private val context: Context,
    @Application private val scope: CoroutineScope,
) : DedicatedImsStyleRepository {

    override val isDedicatedImsIconStyle: StateFlow<Boolean> =
        callbackFlow {
                fun readDedicated(): Boolean =
                    Settings.System.getIntForUser(
                        context.contentResolver,
                        KEY_STATUS_BAR_IMS_INDICATOR_STYLE,
                        STYLE_INTEGRATED,
                        UserHandle.USER_CURRENT,
                    ) == STYLE_DEDICATED

                val observer =
                    object : ContentObserver(null) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(readDedicated())
                        }
                    }
                context.contentResolver.registerContentObserver(
                    Settings.System.getUriFor(KEY_STATUS_BAR_IMS_INDICATOR_STYLE),
                    false,
                    observer,
                    UserHandle.USER_ALL,
                )
                trySend(readDedicated())
                awaitClose { context.contentResolver.unregisterContentObserver(observer) }
            }
            .map { it }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    companion object {
        // Keep in sync with Settings.System.STATUS_BAR_IMS_INDICATOR_STYLE in Settings.java.
        private const val KEY_STATUS_BAR_IMS_INDICATOR_STYLE = "status_bar_ims_indicator_style"

        const val STYLE_INTEGRATED = 0
        const val STYLE_DEDICATED = 1
    }
}

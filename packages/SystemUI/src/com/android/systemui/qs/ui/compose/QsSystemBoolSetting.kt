/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.ui.compose

import android.database.ContentObserver
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Reads and observes a [Settings.System] boolean stored as 0/1.
 *
 * Scene-container QS can compose before Settings is ready, so this re-reads on subscribe and
 * updates when the setting changes.
 */
@Composable
fun rememberQsSystemBoolSetting(name: String, defaultValue: Boolean): Boolean {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val defaultInt = if (defaultValue) 1 else 0

    fun readValue(): Boolean {
        return try {
            Settings.System.getIntForUser(
                contentResolver,
                name,
                defaultInt,
                UserHandle.USER_CURRENT,
            ) == 1
        } catch (_: Throwable) {
            defaultValue
        }
    }

    var value by remember(name, defaultValue) { mutableStateOf(readValue()) }

    DisposableEffect(contentResolver, name, defaultValue) {
        // Scene-container QS can compose before Settings is ready; re-read on subscribe.
        value = readValue()
        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    context.mainExecutor.execute { value = readValue() }
                }
            }

        contentResolver.registerContentObserver(
            Settings.System.getUriFor(name),
            false,
            observer,
            UserHandle.USER_ALL,
        )

        onDispose { contentResolver.unregisterContentObserver(observer) }
    }

    return value
}

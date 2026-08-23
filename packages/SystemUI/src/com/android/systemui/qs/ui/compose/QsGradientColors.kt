/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.ui.compose

import android.database.ContentObserver
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.android.systemui.res.R

data class QsGradientColors(
    val start: Color,
    val end: Color,
)

/**
 * Resolved gradient start/end for tiles and sliders. Uses [Settings.System.GRADIENT_START_COLOR] /
 * [Settings.System.GRADIENT_END_COLOR] when set (non-zero ARGB); otherwise the theme defaults.
 */
@Composable
fun rememberQsGradientColors(): QsGradientColors {
    val (customStart, customEnd) = rememberQsGradientCustomColors()
    val context = LocalContext.current
    val resources = LocalResources.current
    val isDark = isSystemInDarkTheme()
    val defaultStart =
        remember(isDark, resources, context.theme) {
            val id =
                if (isDark) {
                    R.color.derpfestui_color_gradient_start_dark
                } else {
                    R.color.derpfestui_color_gradient_start_light
                }
            Color(resources.getColor(id, context.theme))
        }
    val defaultEnd =
        remember(isDark, resources, context.theme) {
            val id =
                if (isDark) {
                    R.color.derpfestui_color_gradient_end_dark
                } else {
                    R.color.derpfestui_color_gradient_end_light
                }
            Color(resources.getColor(id, context.theme))
        }
    return QsGradientColors(
        start = customStart ?: defaultStart,
        end = customEnd ?: defaultEnd,
    )
}

/** User-chosen gradient start/end. Null means fall back to the theme default. */
@Composable
fun rememberQsGradientCustomColors(): Pair<Color?, Color?> {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    fun readStart(): Int {
        return try {
            Settings.System.getIntForUser(
                contentResolver,
                Settings.System.GRADIENT_START_COLOR,
                0,
                UserHandle.USER_CURRENT,
            )
        } catch (_: Throwable) {
            0
        }
    }

    fun readEnd(): Int {
        return try {
            Settings.System.getIntForUser(
                contentResolver,
                Settings.System.GRADIENT_END_COLOR,
                0,
                UserHandle.USER_CURRENT,
            )
        } catch (_: Throwable) {
            0
        }
    }

    var startArgb by remember { mutableIntStateOf(readStart()) }
    var endArgb by remember { mutableIntStateOf(readEnd()) }

    DisposableEffect(contentResolver) {
        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    context.mainExecutor.execute {
                        startArgb = readStart()
                        endArgb = readEnd()
                    }
                }
            }
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.GRADIENT_START_COLOR),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.GRADIENT_END_COLOR),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        onDispose { contentResolver.unregisterContentObserver(observer) }
    }

    return Pair(
        if (startArgb == 0) null else gradientSettingArgbToColor(startArgb),
        if (endArgb == 0) null else gradientSettingArgbToColor(endArgb),
    )
}

/** Treats 0x00RRGGBB as opaque so color-picker values without alpha still paint fully. */
private fun gradientSettingArgbToColor(argb: Int): Color {
    val a = (argb shr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val alpha = if (a == 0) 1f else a / 255f
    return Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = alpha)
}

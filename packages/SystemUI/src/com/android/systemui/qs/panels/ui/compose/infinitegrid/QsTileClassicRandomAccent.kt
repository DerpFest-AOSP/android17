/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import com.android.internal.graphics.ColorUtils
import java.util.Random

/** Stable per-tile random accent for classic circular QS when [Settings.System.QS_TILES_CLASSIC_RANDOM_ACCENT] is on. */
internal object QsTileClassicRandomAccent {
    fun argb(isDark: Boolean, tileSpec: String): Int {
        val r = Random(tileSpec.hashCode().toLong())
        val hsl = FloatArray(3)
        hsl[0] = r.nextInt(360).toFloat()
        hsl[1] = 0.5f + r.nextFloat() * 0.5f
        hsl[2] = (if (isDark) 0.575f else 0.3f) + r.nextFloat() * 0.125f
        return ColorUtils.HSLToColor(hsl)
    }
}

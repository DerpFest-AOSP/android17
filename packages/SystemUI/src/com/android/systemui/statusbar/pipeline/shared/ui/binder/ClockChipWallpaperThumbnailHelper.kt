/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.shared.ui.binder

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap

/**
 * Builds the wallpaper bitmap fill for the status bar clock chip. The **stadium (pill) shape** is
 * applied by [HomeStatusBarViewBinder] via [android.view.ViewOutlineProvider], not here.
 *
 * Uses the home static image wallpaper when available; other live wallpapers fall back to solid
 * black.
 */
object ClockChipWallpaperThumbnailHelper {

    private const val SYSTEMUI_PACKAGE = "com.android.systemui"
    private const val IMAGE_WALLPAPER_SERVICE = "ImageWallpaper"

    data class ChipBackground(
        val drawable: Drawable,
        /** ARGB sample used with [com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors.textColorOnBackground]. */
        val contrastSampleArgb: Int,
    )

    /**
     * Returns a drawable and a representative color for text contrast.
     * Must be called from a background thread when loading the wallpaper bitmap.
     */
    fun loadChipBackground(context: Context): ChipBackground {
        val wm = WallpaperManager.getInstance(context)
        if (!wm.isWallpaperSupported) {
            return ChipBackground(ColorDrawable(Color.BLACK), Color.BLACK)
        }
        if (!isHomeWallpaperBitmapBacked(wm)) {
            return ChipBackground(ColorDrawable(Color.BLACK), Color.BLACK)
        }
        return try {
            val full = wm.getDrawable(WallpaperManager.FLAG_SYSTEM)
            if (full == null) {
                return ChipBackground(ColorDrawable(Color.BLACK), Color.BLACK)
            }
            val maxPx =
                (MAX_THUMB_EDGE_DP * context.resources.displayMetrics.density)
                    .toInt()
                    .coerceAtLeast(32)
            val w = full.intrinsicWidth
            val h = full.intrinsicHeight
            val bitmap: Bitmap =
                if (w > 0 && h > 0) {
                    val scale = minOf(maxPx.toFloat() / w, maxPx.toFloat() / h)
                    val nw = (w * scale).toInt().coerceAtLeast(1)
                    val nh = (h * scale).toInt().coerceAtLeast(1)
                    full.toBitmap(nw, nh)
                } else {
                    full.toBitmap(maxPx, maxPx)
                }
            val sample = sampleCenterArgb(bitmap)
            ChipBackground(BitmapDrawable(context.resources, bitmap), sample)
        } catch (_: Exception) {
            ChipBackground(ColorDrawable(Color.BLACK), Color.BLACK)
        }
    }

    private fun isHomeWallpaperBitmapBacked(wm: WallpaperManager): Boolean {
        val info = wm.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM) ?: return true
        val cn = info.component
        return cn.packageName == SYSTEMUI_PACKAGE && cn.className.endsWith(IMAGE_WALLPAPER_SERVICE)
    }

    private fun sampleCenterArgb(bitmap: Bitmap): Int {
        val x = (bitmap.width / 2).coerceIn(0, bitmap.width - 1)
        val y = (bitmap.height / 2).coerceIn(0, bitmap.height - 1)
        return bitmap.getPixel(x, y)
    }

    private const val MAX_THUMB_EDGE_DP = 64f
}

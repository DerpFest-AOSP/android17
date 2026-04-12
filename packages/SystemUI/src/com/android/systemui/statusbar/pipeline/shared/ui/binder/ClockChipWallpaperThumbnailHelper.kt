/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.shared.ui.binder

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Builds the wallpaper bitmap fill for the status bar clock chip. The **rounded chip outline**
 * matches other styles via [HomeStatusBarViewBinder] and `chip_corner_radius`.
 *
 * Uses a **cropped horizontal band** of the home wallpaper (not the whole image squeezed into a
 * tiny bitmap), then scales that region for a clearer strip. Other live wallpapers fall back to
 * solid black.
 */
object ClockChipWallpaperThumbnailHelper {

    private const val SYSTEMUI_PACKAGE = "com.android.systemui"
    private const val IMAGE_WALLPAPER_SERVICE = "ImageWallpaper"

    /** Target width:height of the region we show (wide strip, similar to the clock chip). */
    private const val CROP_ASPECT_WIDTH_OVER_HEIGHT = 2.85f

    /** Max edge when decoding wallpaper before crop (px) — enough detail to crop sharply. */
    private const val DECODE_MAX_EDGE_PX = 1024

    /** Max edge of the final bitmap (dp); layout still follows text ([NoIntrinsicSizeBitmapDrawable]). */
    private const val OUTPUT_MAX_EDGE_DP = 128f

    /**
     * Avoids sizing the [com.android.systemui.statusbar.policy.Clock] to a square from large
     * intrinsic bitmap dimensions; layout should follow text + padding like [R.drawable.sb_date_bg]
     * chips.
     */
    private class NoIntrinsicSizeBitmapDrawable(res: Resources, bitmap: Bitmap) :
        BitmapDrawable(res, bitmap) {
        override fun getIntrinsicWidth(): Int = -1

        override fun getIntrinsicHeight(): Int = -1
    }

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
            val density = context.resources.displayMetrics.density
            val outMaxPx = (OUTPUT_MAX_EDGE_DP * density).toInt().coerceAtLeast(48)

            val decoded = decodeWallpaperWorkBitmap(full)
            val cropped = cropToChipAspect(decoded, CROP_ASPECT_WIDTH_OVER_HEIGHT, alignCropTop = true)
            if (cropped !== decoded && !decoded.isRecycled) {
                decoded.recycle()
            }

            val finalBmp = scaleToMaxEdgePreservingAspect(cropped, outMaxPx)
            if (finalBmp !== cropped && !cropped.isRecycled) {
                cropped.recycle()
            }

            val sample = sampleCenterArgb(finalBmp)
            ChipBackground(NoIntrinsicSizeBitmapDrawable(context.resources, finalBmp), sample)
        } catch (_: Exception) {
            ChipBackground(ColorDrawable(Color.BLACK), Color.BLACK)
        }
    }

    /** Decode wallpaper to a moderately large bitmap for cropping (not the final chip size). */
    private fun decodeWallpaperWorkBitmap(full: Drawable): Bitmap {
        val w = full.intrinsicWidth
        val h = full.intrinsicHeight
        if (w <= 0 || h <= 0) {
            val tw = DECODE_MAX_EDGE_PX
            val th = (tw / CROP_ASPECT_WIDTH_OVER_HEIGHT).toInt().coerceAtLeast(8)
            return full.toBitmap(tw, th)
        }
        val scale = min(DECODE_MAX_EDGE_PX.toFloat() / w, DECODE_MAX_EDGE_PX.toFloat() / h)
        val dw = max(1, (w * scale).toInt())
        val dh = max(1, (h * scale).toInt())
        return full.toBitmap(dw, dh)
    }

    /**
     * Crops to a wide strip of aspect [aspectWidthOverHeight]. When the image is taller than that
     * aspect, keeps the **top** band (closer to what sits behind the status bar). When wider, crops
     * the sides (centered).
     */
    private fun cropToChipAspect(
        src: Bitmap,
        aspectWidthOverHeight: Float,
        alignCropTop: Boolean,
    ): Bitmap {
        val W = src.width
        val H = src.height
        if (W <= 0 || H <= 0) return src

        val srcAspect = W.toFloat() / H.toFloat()
        val cropW: Int
        val cropH: Int
        val left: Int
        val top: Int

        if (srcAspect > aspectWidthOverHeight) {
            // Too wide — take center horizontal slice.
            cropH = H
            cropW = (H * aspectWidthOverHeight).toInt().coerceIn(1, W)
            left = (W - cropW) / 2
            top = 0
        } else {
            // Too tall — take a horizontal band; prefer upper part of the wallpaper.
            cropW = W
            cropH = (W / aspectWidthOverHeight).toInt().coerceIn(1, H)
            left = 0
            top =
                if (alignCropTop) {
                    0
                } else {
                    ((H - cropH) / 2).coerceAtLeast(0)
                }
        }

        val safeW = cropW.coerceAtMost(W - left)
        val safeH = cropH.coerceAtMost(H - top)
        if (safeW <= 0 || safeH <= 0) return src

        return try {
            Bitmap.createBitmap(src, left, top, safeW, safeH)
        } catch (_: IllegalArgumentException) {
            src
        }
    }

    private fun scaleToMaxEdgePreservingAspect(bm: Bitmap, maxEdgePx: Int): Bitmap {
        val w = bm.width
        val h = bm.height
        if (w <= 0 || h <= 0) return bm
        val longEdge = max(w, h)
        if (longEdge <= maxEdgePx) return bm
        val scale = maxEdgePx.toFloat() / longEdge
        val nw = max(1, (w * scale).toInt())
        val nh = max(1, (h * scale).toInt())
        return Bitmap.createScaledBitmap(bm, nw, nh, true)
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
}

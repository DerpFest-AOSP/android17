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
 * Uses a cropped horizontal band of the home wallpaper, then scales it. Decoded bitmaps are moved to
 * **ARGB_8888** so cropping, scaling, and contrast sampling work (hardware bitmaps forbid
 * [Bitmap.getPixel] and subset [Bitmap.createBitmap] in many cases). Other live wallpapers fall
 * back to solid black.
 */
object ClockChipWallpaperThumbnailHelper {

    private const val SYSTEMUI_PACKAGE = "com.android.systemui"
    private const val IMAGE_WALLPAPER_SERVICE = "ImageWallpaper"

    /** Target width:height of the cropped region (wide strip, like the chip). */
    private const val CROP_ASPECT_WIDTH_OVER_HEIGHT = 2.85f

    /** Max edge when decoding wallpaper before crop (px). */
    private const val DECODE_MAX_EDGE_PX = 1024

    /** Max edge of the final bitmap (dp). */
    private const val OUTPUT_MAX_EDGE_DP = 128f

    private class NoIntrinsicSizeBitmapDrawable(res: Resources, bitmap: Bitmap) :
        BitmapDrawable(res, bitmap) {
        override fun getIntrinsicWidth(): Int = -1

        override fun getIntrinsicHeight(): Int = -1
    }

    data class ChipBackground(
        val drawable: Drawable,
        val contrastSampleArgb: Int,
    )

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

            var decoded = decodeWallpaperWorkBitmap(full)
            decoded = ensureArgb8888(decoded)

            var cropped = cropToChipAspect(decoded, CROP_ASPECT_WIDTH_OVER_HEIGHT, alignCropTop = true)
            if (cropped !== decoded && !decoded.isRecycled) {
                decoded.recycle()
            }

            var finalBmp = scaleToMaxEdgePreservingAspect(cropped, outMaxPx)
            if (finalBmp !== cropped && !cropped.isRecycled) {
                cropped.recycle()
            }

            finalBmp = ensureArgb8888(finalBmp)

            val sample = sampleCenterArgbSafe(finalBmp)
            ChipBackground(NoIntrinsicSizeBitmapDrawable(context.resources, finalBmp), sample)
        } catch (_: Exception) {
            ChipBackground(ColorDrawable(Color.BLACK), Color.BLACK)
        }
    }

    /**
     * Hardware (and some RGBA) bitmaps cannot be subset-cropped or sampled with [Bitmap.getPixel]
     * reliably; copy to ARGB for a stable path.
     */
    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.HARDWARE) {
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            if (copy != null) {
                if (copy !== bitmap && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
                return copy
            }
        }
        return bitmap
    }

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
            cropH = H
            cropW = (H * aspectWidthOverHeight).toInt().coerceIn(1, W)
            left = (W - cropW) / 2
            top = 0
        } else {
            cropW = W
            cropH = (W / aspectWidthOverHeight).toInt().coerceIn(1, H)
            left = 0
            top = if (alignCropTop) 0 else ((H - cropH) / 2).coerceAtLeast(0)
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

    /** Must not recycle [bitmap] (it is still used by the drawable). */
    private fun sampleCenterArgbSafe(bitmap: Bitmap): Int {
        return try {
            val x = (bitmap.width / 2).coerceIn(0, bitmap.width - 1)
            val y = (bitmap.height / 2).coerceIn(0, bitmap.height - 1)
            bitmap.getPixel(x, y)
        } catch (_: Exception) {
            Color.DKGRAY
        }
    }
}

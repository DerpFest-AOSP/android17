/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
/*
 * Multi-layer waveform using the same gradient palette as Quick Settings tiles:
 * [Settings.System.GRADIENT_START_COLOR] / [GRADIENT_END_COLOR], falling back to
 * [R.color.derpfestui_color_gradient_*] with the same dark/light rules as
 * [com.android.systemui.qs.panels.ui.compose.infinitegrid.Tile].
 */
package com.android.systemui.pulse

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.Flags
import com.android.systemui.res.R
import kotlin.math.max

internal class QsGradientLayeredWaveformStyleRenderer(
    private val settings: PulseSettingsRepository,
    private val appContext: Context
) : PulseStyleRenderer {

    private companion object {
        /** Same layering intent as QS tile horizontal gradient: stacked depths of the same curve. */
        const val NUM_LAYERS = 3
        val LAYER_ALPHA = floatArrayOf(0.42f, 0.68f, 0.92f)
        val LAYER_SMOOTHING = floatArrayOf(0.10f, 0.16f, 0.22f)
        val LAYER_STROKE_SCALE = floatArrayOf(0.78f, 0.9f, 1f)
        /** Extra lift from the baseline for back layers (dp). */
        val LAYER_BASE_OFFSET_DP = floatArrayOf(12f, 6f, 0f)
    }

    private val strokePaints = Array(NUM_LAYERS) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }
    private val fillPaints = Array(NUM_LAYERS) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    private val waveformPaths = Array(NUM_LAYERS) { Path() }
    private val fillPaths = Array(NUM_LAYERS) { Path() }

    private var barStepPx = 0f
    private var waveformBarCount = 0

    private var targetHeights = FloatArray(0)
    private val currentHeights = Array(NUM_LAYERS) { FloatArray(0) }

    private var viewWidthPx = 0
    private var viewHeightPx = 0

    /** Unused for drawing; pulse still calls [onColor]. */
    private var lastPulseColor = 0

    override fun onSizeChanged(viewWidth: Int, viewHeight: Int) {
        viewWidthPx = viewWidth
        viewHeightPx = viewHeight

        val count = settings.getBarCount()
        waveformBarCount = count

        for (layer in 0 until NUM_LAYERS) {
            if (currentHeights[layer].size != count) {
                currentHeights[layer] = FloatArray(count) { 2f }
            }
        }
        if (targetHeights.size != count) {
            targetHeights = FloatArray(count) { 2f }
        }

        val gap = settings.getBarGapPx()
        val totalGap = (count - 1).coerceAtLeast(0) * gap
        val barWidth = if (count > 0) max(1f, (viewWidth - totalGap) / count) else 0f
        barStepPx = barWidth + gap

        val cap = if (settings.isRoundedBarsEnabled()) Paint.Cap.ROUND else Paint.Cap.BUTT
        val baseStroke = max(3f * settings.displayDensity(), viewHeight * 0.003f)
        for (layer in 0 until NUM_LAYERS) {
            strokePaints[layer].strokeCap = cap
            strokePaints[layer].strokeWidth = baseStroke * LAYER_STROKE_SCALE[layer]
        }
        refreshGradientShaders()
    }

    override fun onColor(color: Int) {
        lastPulseColor = color
        refreshGradientShaders()
    }

    override fun onData(heights: FloatArray) {
        if (heights.size != targetHeights.size) {
            targetHeights = FloatArray(heights.size) { 2f }
            for (layer in 0 until NUM_LAYERS) {
                currentHeights[layer] = FloatArray(heights.size) { 2f }
            }
        }
        System.arraycopy(heights, 0, targetHeights, 0, heights.size)
    }

    override fun draw(canvas: Canvas, viewWidth: Int, viewHeight: Int) {
        val count = waveformBarCount
        if (count == 0 || viewHeight <= 0) return

        val bottom = viewHeight.toFloat()
        val right = viewWidth.toFloat()
        val density = settings.displayDensity()

        for (layer in 0 until NUM_LAYERS) {
            val wavePath = waveformPaths[layer]
            val areaPath = fillPaths[layer]
            wavePath.reset()
            areaPath.reset()

            val baseOffsetPx = LAYER_BASE_OFFSET_DP[layer] * density

            val y0 = bottom - baseOffsetPx - smoothedLift(layer, 0, bottom - baseOffsetPx)
            var yTopRight = y0

            wavePath.moveTo(0f, y0)
            areaPath.moveTo(0f, bottom)
            areaPath.lineTo(0f, y0)

            for (i in 1 until count) {
                val x = i * barStepPx
                val y = bottom - baseOffsetPx - smoothedLift(layer, i, bottom - baseOffsetPx)
                wavePath.lineTo(x, y)
                areaPath.lineTo(x, y)
                yTopRight = y
            }

            wavePath.lineTo(right, yTopRight)
            areaPath.lineTo(right, yTopRight)
            areaPath.lineTo(right, bottom)
            areaPath.close()

            canvas.drawPath(areaPath, fillPaints[layer])
            if (count >= 2) {
                canvas.drawPath(wavePath, strokePaints[layer])
            } else if (count == 1) {
                canvas.drawLine(0f, yTopRight, right, yTopRight, strokePaints[layer])
            }
        }
    }

    private fun smoothedLift(layer: Int, i: Int, effectiveBottom: Float): Float {
        val target = targetHeights.getOrElse(i) { 2f }
        val cur = currentHeights[layer].getOrElse(i) { 2f }
        val smoothing = LAYER_SMOOTHING[layer]
        var h = cur + smoothing * (target - cur)
        if (h < 2f) h = 2f
        if (h > effectiveBottom) h = effectiveBottom
        currentHeights[layer][i] = h
        return h
    }

    override fun cleanup() {
        waveformBarCount = 0
        barStepPx = 0f
        targetHeights = FloatArray(0)
        for (layer in 0 until NUM_LAYERS) {
            currentHeights[layer] = FloatArray(0)
            waveformPaths[layer].reset()
            fillPaths[layer].reset()
            strokePaints[layer].shader = null
            fillPaints[layer].shader = null
        }
        viewWidthPx = 0
        viewHeightPx = 0
        lastPulseColor = 0
    }

    /**
     * Mirrors [Tile.tileGradientBrushOrNull]: custom ARGB when non-zero, else theme defaults.
     * Uses the same dark/light resolution as QS Compose tiles (including blur flag).
     */
    private fun resolveQsGradientEndpoints(): Pair<Int, Int> {
        val startSetting = readSystemInt(Settings.System.GRADIENT_START_COLOR)
        val endSetting = readSystemInt(Settings.System.GRADIENT_END_COLOR)

        val useDark = qsGradientDefaultsUseDarkTheme()
        val startFallback =
            if (useDark) R.color.derpfestui_color_gradient_start_dark
            else R.color.derpfestui_color_gradient_start_light
        val endFallback =
            if (useDark) R.color.derpfestui_color_gradient_end_dark
            else R.color.derpfestui_color_gradient_end_light

        val startArgb =
            if (startSetting != 0) startSetting else appContext.getColor(startFallback)
        val endArgb =
            if (endSetting != 0) endSetting else appContext.getColor(endFallback)

        return Pair(startArgb, endArgb)
    }

    private fun qsGradientDefaultsUseDarkTheme(): Boolean {
        return if (Flags.notificationShadeBlur()) {
            (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        } else {
            true
        }
    }

    private fun readSystemInt(key: String): Int {
        return try {
            Settings.System.getIntForUser(
                appContext.contentResolver,
                key,
                0,
                UserHandle.USER_CURRENT
            )
        } catch (_: Throwable) {
            0
        }
    }

    private fun refreshGradientShaders() {
        val w = viewWidthPx.toFloat().coerceAtLeast(1f)
        val (startRgb, endRgb) = resolveQsGradientEndpoints()

        for (layer in 0 until NUM_LAYERS) {
            val a = LAYER_ALPHA[layer]
            val strokeStart = multiplyAlpha(startRgb, a)
            val strokeEnd = multiplyAlpha(endRgb, a)
            val strokeShader = LinearGradient(
                0f,
                0f,
                w,
                0f,
                strokeStart,
                strokeEnd,
                Shader.TileMode.CLAMP
            )
            strokePaints[layer].shader = strokeShader

            val fillStart = multiplyAlpha(startRgb, a * 0.35f)
            val fillEnd = multiplyAlpha(endRgb, a * 0.28f)
            fillPaints[layer].shader =
                LinearGradient(0f, 0f, w, 0f, fillStart, fillEnd, Shader.TileMode.CLAMP)
        }
    }

    private fun multiplyAlpha(rgb: Int, alphaFactor: Float): Int {
        val oldA = Color.alpha(rgb)
        val base = if (oldA == 0) 255 else oldA
        val a = (base * alphaFactor).toInt().coerceIn(0, 255)
        return (a shl 24) or (rgb and 0x00FFFFFF)
    }
}

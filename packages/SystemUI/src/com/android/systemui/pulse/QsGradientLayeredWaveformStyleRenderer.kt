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
import kotlin.math.min

internal class QsGradientLayeredWaveformStyleRenderer(
    private val settings: PulseSettingsRepository,
    private val appContext: Context
) : PulseStyleRenderer {

    private companion object {
        /** Stacked waveforms; larger vertical gaps so all three read clearly. */
        const val NUM_LAYERS = 3
        /** Catmull–Rom → cubic tension divisor (higher = gentler curves). */
        const val CURVE_TENSION = 6f
        val LAYER_ALPHA = floatArrayOf(0.52f, 0.74f, 0.96f)
        val LAYER_SMOOTHING = floatArrayOf(0.08f, 0.14f, 0.22f)
        val LAYER_STROKE_SCALE = floatArrayOf(0.72f, 0.88f, 1f)
        /** Vertical gap between layer baselines (~14dp between neighbors). */
        val LAYER_BASE_OFFSET_DP = floatArrayOf(28f, 14f, 0f)
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

    /** Per-frame sampled Y (top of wave); avoids reallocating in [draw]. */
    private val scratchY = Array(NUM_LAYERS) { FloatArray(0) }

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
        for (layer in 0 until NUM_LAYERS) {
            if (scratchY[layer].size < count) {
                scratchY[layer] = FloatArray(count)
            }
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
        val need = maxOf(heights.size, waveformBarCount)
        for (layer in 0 until NUM_LAYERS) {
            if (scratchY[layer].size < need) {
                scratchY[layer] = FloatArray(need)
            }
        }
    }

    override fun draw(canvas: Canvas, viewWidth: Int, viewHeight: Int) {
        val count = waveformBarCount
        if (count == 0 || viewHeight <= 0) return

        val n = minOf(count, targetHeights.size).coerceAtLeast(0)
        if (n == 0) return

        val bottom = viewHeight.toFloat()
        val right = viewWidth.toFloat()
        val density = settings.displayDensity()

        for (layer in 0 until NUM_LAYERS) {
            val wavePath = waveformPaths[layer]
            val areaPath = fillPaths[layer]
            wavePath.reset()
            areaPath.reset()

            val baseOffsetPx = LAYER_BASE_OFFSET_DP[layer] * density
            val effectiveBottom = bottom - baseOffsetPx

            val ys = scratchY[layer]
            for (i in 0 until n) {
                ys[i] = bottom - baseOffsetPx - smoothedLift(layer, i, effectiveBottom)
            }
            val yTopRight = ys[n - 1]

            appendSmoothedWaveStroke(wavePath, ys, n, right)
            // Filled area only on the front layer so rear/mid strokes stay visible.
            if (layer == NUM_LAYERS - 1) {
                appendSmoothedWaveFillTop(areaPath, ys, n, right, bottom)
                areaPath.lineTo(right, bottom)
                areaPath.close()
                canvas.drawPath(areaPath, fillPaints[layer])
            }

            if (n >= 2) {
                canvas.drawPath(wavePath, strokePaints[layer])
            } else if (n == 1) {
                canvas.drawLine(0f, yTopRight, right, yTopRight, strokePaints[layer])
            }
        }
    }

    /**
     * Smooth cubic curve through sample points (Catmull–Rom style), closed with a flat run to [right].
     */
    private fun appendSmoothedWaveStroke(path: Path, ys: FloatArray, count: Int, right: Float) {
        val step = barStepPx
        path.moveTo(0f, ys[0])
        if (count == 1) {
            path.lineTo(right, ys[0])
            return
        }
        fun xAt(i: Int) = i * step
        fun yAt(i: Int) = ys[i.coerceIn(0, count - 1)]

        for (i in 0 until count - 1) {
            val p0y = if (i > 0) yAt(i - 1) else 2f * yAt(0) - yAt(1)
            val p1x = xAt(i)
            val p1y = yAt(i)
            val p2x = xAt(i + 1)
            val p2y = yAt(i + 1)
            val p3y = if (i + 2 < count) yAt(i + 2) else 2f * yAt(count - 1) - yAt(count - 2)

            val p0x = xAt(i - 1)
            val p3x = xAt(i + 2)

            val cp1x = p1x + (p2x - p0x) / CURVE_TENSION
            val cp1y = p1y + (p2y - p0y) / CURVE_TENSION
            val cp2x = p2x - (p3x - p1x) / CURVE_TENSION
            val cp2y = p2y - (p3y - p1y) / CURVE_TENSION
            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y)
        }
        path.lineTo(right, ys[count - 1])
    }

    /** Top edge of the filled region (same curve as stroke), starting at (0, [ys][0]). */
    private fun appendSmoothedWaveFillTop(
        path: Path,
        ys: FloatArray,
        count: Int,
        right: Float,
        bottom: Float
    ) {
        path.moveTo(0f, bottom)
        path.lineTo(0f, ys[0])
        if (count == 1) {
            path.lineTo(right, ys[0])
            return
        }
        val step = barStepPx
        fun xAt(i: Int) = i * step
        fun yAt(i: Int) = ys[i.coerceIn(0, count - 1)]

        for (i in 0 until count - 1) {
            val p0y = if (i > 0) yAt(i - 1) else 2f * yAt(0) - yAt(1)
            val p1x = xAt(i)
            val p1y = yAt(i)
            val p2x = xAt(i + 1)
            val p2y = yAt(i + 1)
            val p3y = if (i + 2 < count) yAt(i + 2) else 2f * yAt(count - 1) - yAt(count - 2)
            val p0x = xAt(i - 1)
            val p3x = xAt(i + 2)
            val cp1x = p1x + (p2x - p0x) / CURVE_TENSION
            val cp1y = p1y + (p2y - p0y) / CURVE_TENSION
            val cp2x = p2x - (p3x - p1x) / CURVE_TENSION
            val cp2y = p2y - (p3y - p1y) / CURVE_TENSION
            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y)
        }
        path.lineTo(right, ys[count - 1])
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
            scratchY[layer] = FloatArray(0)
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

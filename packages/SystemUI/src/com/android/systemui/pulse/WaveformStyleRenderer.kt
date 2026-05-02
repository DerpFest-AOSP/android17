/*
 * Copyright (C) 2016 The DirtyUnicorns Project
 * Copyright (C) 2025 DerpFest AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Derived from WaveformRenderer (navigation pulse); ported to PulseStyleRenderer.
 */
package com.android.systemui.pulse

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.max

internal class WaveformStyleRenderer(
    private val settings: PulseSettingsRepository
) : PulseStyleRenderer {

    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val waveformPath = Path()
    private val fillPath = Path()

    /** Distance between consecutive bar **left** edges (`barWidth + gap`). */
    private var barStepPx = 0f
    private var waveformBarCount = 0

    private var currentHeights = FloatArray(0)
    private var targetHeights = FloatArray(0)
    private var lastColor = 0

    /** Legacy defaults: filled area plus stroke outline. */
    private var showFill = true
    private var showOutline = true

    private val smoothing = 0.2f

    override fun onSizeChanged(viewWidth: Int, viewHeight: Int) {
        val count = settings.getBarCount()
        waveformBarCount = count

        if (currentHeights.size != count) {
            currentHeights = FloatArray(count) { 2f }
            targetHeights = FloatArray(count) { 2f }
        }

        val gap = settings.getBarGapPx()
        val totalGap = (count - 1).coerceAtLeast(0) * gap
        val barWidth = if (count > 0) max(1f, (viewWidth - totalGap) / count) else 0f
        barStepPx = barWidth + gap

        waveformPaint.strokeCap =
            if (settings.isRoundedBarsEnabled()) Paint.Cap.ROUND else Paint.Cap.BUTT
        waveformPaint.strokeWidth =
            max(3f * settings.displayDensity(), viewHeight * 0.003f)

        strokeColorFromPulseColor()
        fillPaint.color = fadeFillFromOutline(lastColor)
    }

    override fun onColor(color: Int) {
        if (color != lastColor) {
            lastColor = color
            strokeColorFromPulseColor()
            fillPaint.color = fadeFillFromOutline(color)
        }
    }

    private fun strokeColorFromPulseColor() {
        waveformPaint.color = lastColor
    }

    private fun fadeFillFromOutline(color: Int): Int {
        val a = Color.alpha(color)
        val fillA = max(36, minOf(110, (a / 2.5f).toInt()))
        return (fillA shl 24) or (color and 0x00FFFFFF)
    }

    override fun onData(heights: FloatArray) {
        if (heights.size != targetHeights.size) {
            targetHeights = FloatArray(heights.size)
            currentHeights = FloatArray(heights.size) { 2f }
        }
        System.arraycopy(heights, 0, targetHeights, 0, heights.size)
    }

    override fun draw(canvas: Canvas, viewWidth: Int, viewHeight: Int) {
        val count = waveformBarCount
        if (count == 0 || viewHeight <= 0) return

        val bottom = viewHeight.toFloat()
        val right = viewWidth.toFloat()
        waveformPath.reset()
        fillPath.reset()

        // Sample at each bar's **left** edge (x = i * step), then close the polyline at x = right
        // so the wave fills the full width. Using bar centers left half-bar gaps on both sides.
        val y0 = bottom - smoothedLift(0, bottom)
        var yTopRight = y0

        waveformPath.moveTo(0f, y0)
        fillPath.moveTo(0f, bottom)
        fillPath.lineTo(0f, y0)

        for (i in 1 until count) {
            val x = i * barStepPx
            val y = bottom - smoothedLift(i, bottom)
            waveformPath.lineTo(x, y)
            fillPath.lineTo(x, y)
            yTopRight = y
        }

        waveformPath.lineTo(right, yTopRight)
        fillPath.lineTo(right, yTopRight)
        fillPath.lineTo(right, bottom)
        fillPath.close()

        if (showFill) {
            canvas.drawPath(fillPath, fillPaint)
        }
        if (showOutline && count >= 2) {
            canvas.drawPath(waveformPath, waveformPaint)
        } else if (showOutline && count == 1) {
            canvas.drawLine(0f, yTopRight, right, yTopRight, waveformPaint)
        }
    }

    private fun smoothedLift(i: Int, bottom: Float): Float {
        val target = targetHeights.getOrElse(i) { 2f }
        val cur = currentHeights.getOrElse(i) { 2f }
        var h = cur + smoothing * (target - cur)
        if (h < 2f) h = 2f
        if (h > bottom) h = bottom
        currentHeights[i] = h
        return h
    }

    override fun cleanup() {
        waveformBarCount = 0
        barStepPx = 0f
        currentHeights = FloatArray(0)
        targetHeights = FloatArray(0)
        waveformPath.reset()
        fillPath.reset()
        lastColor = 0
    }
}

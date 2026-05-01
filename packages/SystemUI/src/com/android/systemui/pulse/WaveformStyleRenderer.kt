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

    private var barCenters = FloatArray(0)
    private var currentHeights = FloatArray(0)
    private var targetHeights = FloatArray(0)
    private var lastColor = 0

    /** Legacy defaults: filled area plus stroke outline. */
    private var showFill = true
    private var showOutline = true

    private val smoothing = 0.2f

    override fun onSizeChanged(viewWidth: Int, viewHeight: Int) {
        val count = settings.getBarCount()

        barCenters = FloatArray(count)
        if (currentHeights.size != count) {
            currentHeights = FloatArray(count) { 2f }
            targetHeights = FloatArray(count) { 2f }
        }

        val gap = settings.getBarGapPx()
        val totalGap = (count - 1).coerceAtLeast(0) * gap
        val barWidth = if (count > 0) max(1f, (viewWidth - totalGap) / count) else 0f
        val step = barWidth + gap

        waveformPaint.strokeCap =
            if (settings.isRoundedBarsEnabled()) Paint.Cap.ROUND else Paint.Cap.BUTT
        waveformPaint.strokeWidth =
            max(3f * settings.displayDensity(), viewHeight * 0.003f)

        for (i in 0 until count) {
            barCenters[i] = i * step + barWidth * 0.5f
        }

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
        val count = barCenters.size
        if (count == 0 || viewHeight <= 0) return

        val bottom = viewHeight.toFloat()
        waveformPath.reset()
        fillPath.reset()

        val firstLift = smoothedLift(0, bottom)
        waveformPath.moveTo(barCenters[0], bottom - firstLift)
        fillPath.moveTo(barCenters[0], bottom)
        fillPath.lineTo(barCenters[0], bottom - firstLift)

        for (i in 1 until count) {
            val lift = smoothedLift(i, bottom)
            val x = barCenters[i]
            val y = bottom - lift
            waveformPath.lineTo(x, y)
            fillPath.lineTo(x, y)
        }

        val lastCenter = barCenters[count - 1]
        fillPath.lineTo(lastCenter, bottom)
        fillPath.close()

        if (showFill) {
            canvas.drawPath(fillPath, fillPaint)
        }
        if (showOutline && count >= 2) {
            canvas.drawPath(waveformPath, waveformPaint)
        } else if (showOutline && count == 1) {
            canvas.drawLine(
                barCenters[0],
                bottom,
                barCenters[0],
                bottom - firstLift,
                waveformPaint
            )
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
        barCenters = FloatArray(0)
        currentHeights = FloatArray(0)
        targetHeights = FloatArray(0)
        waveformPath.reset()
        fillPath.reset()
        lastColor = 0
    }
}

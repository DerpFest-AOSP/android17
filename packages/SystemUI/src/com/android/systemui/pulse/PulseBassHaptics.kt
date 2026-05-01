/*
 * Copyright (C) 2026 The AxionAOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Optional light haptics on bass transients; rate-limited and opt-in via settings.
 */
package com.android.systemui.pulse

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.sqrt

internal class PulseBassHaptics(
    context: Context,
    private val settings: PulseSettingsRepository
) {
    private val vibrator: Vibrator? =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    /** Low-pass of bass bin energy (updated each frame). */
    private var smoothedBass = 0f

    private var lastVibrateUptimeMs = 0L

    /** Skip transient detection until we have a baseline energy. */
    private var primed = false

    fun reset() {
        smoothedBass = 0f
        lastVibrateUptimeMs = 0L
        primed = false
    }

    fun onFft(fft: ByteArray?) {
        if (fft == null || fft.size < BASS_BINS * 2 + 2) return
        if (!settings.isPulseHapticsEnabled()) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        val bass = bassEnergy(fft)
        if (!primed) {
            smoothedBass = bass
            primed = true
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastVibrateUptimeMs < MIN_INTERVAL_MS) {
            updateSmoothingOnly(bass)
            return
        }

        val prevSmoothed = smoothedBass
        smoothedBass = prevSmoothed * SMOOTH_ALPHA + bass * (1f - SMOOTH_ALPHA)

        if (bass > prevSmoothed * SPIKE_RATIO && bass > ABSOLUTE_FLOOR) {
            lastVibrateUptimeMs = now
            vibrateTick(v)
        }
    }

    private fun updateSmoothingOnly(bass: Float) {
        smoothedBass = smoothedBass * SMOOTH_ALPHA + bass * (1f - SMOOTH_ALPHA)
    }

    private fun bassEnergy(fft: ByteArray): Float {
        var sum = 0f
        for (i in 0 until BASS_BINS) {
            val idx = i * 2 + 2
            if (idx + 1 >= fft.size) break
            val r = fft[idx].toInt()
            val im = fft[idx + 1].toInt()
            sum += sqrt((r * r + im * im).toFloat())
        }
        return sum / BASS_BINS
    }

    @SuppressLint("MissingPermission")
    private fun vibrateTick(v: Vibrator) {
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    v.vibrate(
                        VibrationEffect.createOneShot(
                            TICK_DURATION_MS,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                }
                else -> {
                    @Suppress("DEPRECATION")
                    v.vibrate(TICK_DURATION_MS.toLong())
                }
            }
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val BASS_BINS = 8
        const val MIN_INTERVAL_MS = 220L
        const val SPIKE_RATIO = 1.42f
        const val ABSOLUTE_FLOOR = 18f
        const val SMOOTH_ALPHA = 0.88f
        const val TICK_DURATION_MS = 12L
    }
}

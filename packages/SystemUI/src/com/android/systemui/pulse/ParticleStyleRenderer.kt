/*
 * Copyright (C) 2016 The DirtyUnicorns Project
 * Copyright (C) 2025 DerpFest AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Derived from ParticleSystemRenderer (navigation pulse); ported to PulseStyleRenderer.
 */
package com.android.systemui.pulse

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

private const val PARTICLE_SPAWN_DEFAULT = 0.42f
private const val PARTICLE_SPAWN_SMOOTHING = 0.55f

internal class ParticleStyleRenderer(
    private val settings: PulseSettingsRepository
) : PulseStyleRenderer {

    private companion object {
        const val MAX_PARTICLES = 300
        const val BASE_PARTICLE_SIZE_DP = 3f
        const val PHYSICS_TICK = 0.012f
        val RAINBOW_COLORS = intArrayOf(
            0xFFFF0000.toInt(),
            0xFFFF7F00.toInt(),
            0xFFFFFF00.toInt(),
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt(),
            0xFF4B0082.toInt(),
            0xFF9400D3.toInt()
        )
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * settings.displayDensity()
    }

    private val particles = ArrayList<Particle>(MAX_PARTICLES)
    private val random = Random.Default

    private var viewW = 0
    private var viewH = 0

    /** Normalized intensities derived from FFT bar heights each frame */
    private var bassIntensity = 0f
    private var midIntensity = 0f
    private var trebleIntensity = 0f
    private var audioIntensity = 0f

    private var baseColorArgb = Color.WHITE

    /** Lavalamp rotates hue externally; rainbow map when album color is lava-like is unused */
    private var useRainbowVariation = false

    private val gravity = 0.22f * settings.displayDensity()
    private val damping = 0.98f

    /** Legacy-style motion blur lines */
    private var showTrails = true

    override fun onSizeChanged(viewWidth: Int, viewHeight: Int) {
        viewW = viewWidth
        viewH = viewHeight
        particles.clear()
        repeat(MAX_PARTICLES / 2) { spawnParticle() }
        trailPaint.strokeWidth = 1f * settings.displayDensity()
    }

    override fun onColor(color: Int) {
        baseColorArgb = color
        useRainbowVariation = false
        for (particle in particles) {
            particle.color = particleColorFromBase(particle)
        }
        trailArgbFromBase()
    }

    private fun trailArgbFromBase() {
        trailPaint.color = Color.argb(
            50,
            Color.red(baseColorArgb),
            Color.green(baseColorArgb),
            Color.blue(baseColorArgb)
        )
    }

    override fun onData(heights: FloatArray) {
        if (heights.isEmpty()) return
        analyze(heights)

        val spawnRate =
            if (settings.isPulseFftSmoothingEnabled()) PARTICLE_SPAWN_SMOOTHING else PARTICLE_SPAWN_DEFAULT

        if (audioIntensity > 0.08f &&
            random.nextFloat() < spawnRate * audioIntensity &&
            particles.size < MAX_PARTICLES
        ) {
            spawnParticle()
        }
    }

    private fun analyze(heights: FloatArray) {
        val n = heights.size
        if (n < 4) return
        val bassEnd = max(1, min(n / 4, n))
        val midEnd = max(bassEnd + 1, min(n * 3 / 4, n))

        var peak = 1f
        for (v in heights) {
            if (v > peak) peak = v
        }

        fun bandMean(start: Int, endExclusive: Int): Float {
            var s = 0f
            var c = 0
            val e = min(endExclusive, n)
            for (i in start until e) {
                s += heights[i]
                c++
            }
            return if (c > 0) (s / c) / peak else 0f
        }

        bassIntensity = bandMean(0, bassEnd).coerceIn(0f, 2f).coerceAtMost(1f)
        midIntensity = bandMean(bassEnd, midEnd).coerceIn(0f, 2f).coerceAtMost(1f)
        trebleIntensity = bandMean(midEnd, n).coerceIn(0f, 2f).coerceAtMost(1f)
        audioIntensity = ((bassIntensity + midIntensity + trebleIntensity) / 3f).coerceIn(0f, 1f)
    }

    private fun spawnParticle() {
        val w = viewW
        val h = viewH
        if (w <= 0 || h <= 0) return
        if (particles.size >= MAX_PARTICLES) return

        val baseSizePx = BASE_PARTICLE_SIZE_DP * settings.displayDensity()
        val p = Particle(
            x = random.nextFloat() * w,
            y = random.nextFloat() * h,
            vx = (random.nextFloat() - 0.5f) * 4f,
            vy = (random.nextFloat() - 0.5f) * 4f,
            size = baseSizePx + random.nextFloat() * 2f,
            life = 1f,
            color = Color.WHITE,
            bassReactive = random.nextFloat() < 0.3f,
            midReactive = random.nextFloat() < 0.4f,
            trebleReactive = random.nextFloat() < 0.3f,
        ).also { particle ->
            particle.color = particleColorFromBase(particle)
        }
        particles.add(p)
    }

    override fun draw(canvas: Canvas, viewWidth: Int, viewHeight: Int) {
        if (particles.isEmpty()) return

        evolvePhysics()
        cullParticles()

        if (showTrails) {
            for (particle in particles) {
                if (particle.life > 0.5f) {
                    val spd = sqrt(particle.vx * particle.vx + particle.vy * particle.vy)
                    val trailLength = min(20f, spd * 2f)
                    val endX = particle.x - particle.vx * trailLength
                    val endY = particle.y - particle.vy * trailLength
                    trailPaint.color = Color.argb(
                        50,
                        Color.red(particle.color),
                        Color.green(particle.color),
                        Color.blue(particle.color)
                    )
                    canvas.drawLine(particle.x, particle.y, endX, endY, trailPaint)
                }
            }
        }

        for (particle in particles) {
            if (particle.life <= 0f || particle.alpha <= 0) continue
            val a = particle.alpha.coerceIn(0, 255)
            particlePaint.color = particle.color
            particlePaint.alpha = a
            canvas.drawCircle(particle.x, particle.y, particle.size, particlePaint)
            if (particle.size > 4f * settings.displayDensity()) {
                particlePaint.alpha = a / 3
                canvas.drawCircle(particle.x, particle.y, particle.size * 1.5f, particlePaint)
            }
            particlePaint.alpha = 255
        }
    }

    private fun evolvePhysics() {
        for (particle in particles) {
            particle.vy += gravity

            if (particle.bassReactive && bassIntensity > 0.08f) {
                particle.vy -= bassIntensity * 2f
                particle.vx += (random.nextFloat() - 0.5f) * bassIntensity
            }
            if (particle.midReactive && midIntensity > 0.08f) {
                particle.vx += (random.nextFloat() - 0.5f) * midIntensity
                particle.vy += (random.nextFloat() - 0.5f) * midIntensity
            }
            if (particle.trebleReactive && trebleIntensity > 0.08f) {
                particle.vx *= 1f + trebleIntensity * 0.1f
                particle.vy *= 1f + trebleIntensity * 0.1f
            }

            particle.vx *= damping
            particle.vy *= damping
            particle.x += particle.vx
            particle.y += particle.vy

            particle.life -= PHYSICS_TICK
            particle.alpha = (255 * particle.life).toInt().coerceIn(0, 255)

            if (particle.x < 0 || particle.x > viewW) {
                particle.vx *= -0.8f
                particle.x = particle.x.coerceIn(0f, viewW.toFloat())
            }
            if (particle.y < 0 || particle.y > viewH) {
                particle.vy *= -0.8f
                particle.y = particle.y.coerceIn(0f, viewH.toFloat())
            }
        }
    }

    private fun cullParticles() {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (p.life <= 0f || p.alpha <= 0) it.remove()
        }
    }

    private fun particleColorFromBase(particle: Particle): Int {
        if (useRainbowVariation && viewW > 0) {
            val n = RAINBOW_COLORS.size
            val idx = ((particle.x / viewW) * (n - 1)).toInt().coerceIn(0, n - 1)
            return RAINBOW_COLORS[idx]
        }
        val r = Color.red(baseColorArgb)
        val g = Color.green(baseColorArgb)
        val b = Color.blue(baseColorArgb)
        return when {
            particle.bassReactive ->
                Color.argb(255, min(255, r + 50), g, max(0, b - 30))
            particle.midReactive ->
                Color.argb(255, r, min(255, g + 30), b)
            particle.trebleReactive ->
                Color.argb(255, max(0, r - 30), g, min(255, b + 50))
            else -> baseColorArgb
        }
    }

    override fun cleanup() {
        particles.clear()
        particlePaint.maskFilter = null
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val size: Float,
        var life: Float,
        var color: Int,
        val bassReactive: Boolean,
        val midReactive: Boolean,
        val trebleReactive: Boolean,
        var alpha: Int = 255,
    )
}

/*
 * Copyright (C) 2014-2026 The BlissRoms Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import android.service.quicksettings.Tile
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.compose.theme.LocalAndroidColorScheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val STYLE_NONE = 0
private const val STYLE_BOUNCE = 1
private const val STYLE_RIPPLE = 2
private const val STYLE_FLIP = 3
private const val STYLE_FADE = 4
private const val STYLE_PULSE = 5
private const val STYLE_SHAKE = 6
private const val STYLE_WOBBLE = 7
private const val STYLE_SPIN = 8
private const val STYLE_SQUISH = 9
private const val STYLE_TILT = 10
private const val STYLE_HEARTBEAT = 11
private const val STYLE_SWING = 12

fun Modifier.tileToggleAnimation(
    currentState: Int,
    style: Int,
): Modifier = composed {
    if (style == STYLE_NONE) return@composed this

    var toggleCount by remember { mutableIntStateOf(0) }
    var previousState by remember { mutableIntStateOf(currentState) }
    var toActive by remember { mutableIntStateOf(currentState) }

    if (previousState != currentState &&
        (currentState == Tile.STATE_ACTIVE || currentState == Tile.STATE_INACTIVE) &&
        (previousState == Tile.STATE_ACTIVE || previousState == Tile.STATE_INACTIVE)
    ) {
        toActive = currentState
        previousState = currentState
        toggleCount++
    } else if (previousState != currentState) {
        previousState = currentState
    }

    when (style) {
        STYLE_BOUNCE -> bounceAnimation(toggleCount)
        STYLE_RIPPLE -> rippleAnimation(toggleCount, toActive == Tile.STATE_ACTIVE)
        STYLE_FLIP -> flipAnimation(toggleCount)
        STYLE_FADE -> fadeAnimation(toggleCount)
        STYLE_PULSE -> pulseAnimation(toggleCount, toActive == Tile.STATE_ACTIVE)
        STYLE_SHAKE -> shakeAnimation(toggleCount, toActive == Tile.STATE_ACTIVE)
        STYLE_WOBBLE -> wobbleAnimation(toggleCount)
        STYLE_SPIN -> spinAnimation(toggleCount)
        STYLE_SQUISH -> squishAnimation(toggleCount)
        STYLE_TILT -> tiltAnimation(toggleCount)
        STYLE_HEARTBEAT -> heartbeatAnimation(toggleCount)
        STYLE_SWING -> swingAnimation(toggleCount)
        else -> this
    }
}

@Composable
private fun Modifier.bounceAnimation(toggleCount: Int): Modifier {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(toggleCount) {
        if (toggleCount > 0) {
            scale.snapTo(1f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 500
                    1.18f at 160 using FastOutSlowInEasing
                    0.92f at 320 using FastOutSlowInEasing
                    1f at 500
                },
            )
        }
    }

    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
private fun Modifier.rippleAnimation(toggleCount: Int, toActive: Boolean): Modifier {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(toggleCount) {
        if (toggleCount > 0) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(500, easing = FastOutSlowInEasing),
            )
        }
    }

    val rippleAlpha = if (progress.value in 0.01f..0.99f) {
        (1f - progress.value).coerceIn(0f, 0.5f)
    } else 0f

    val rippleColor =
        if (toActive) MaterialTheme.colorScheme.primary
        else LocalAndroidColorScheme.current.surfaceEffect1

    return drawWithContent {
        drawContent()
        if (rippleAlpha > 0f) {
            val radius = size.maxDimension * progress.value
            drawCircle(
                color = rippleColor.copy(alpha = rippleAlpha),
                radius = radius,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
    }
}

@Composable
private fun Modifier.flipAnimation(toggleCount: Int): Modifier {
    val rotation = remember { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(toggleCount) {
        if (toggleCount > 0) {
            rotation.snapTo(0f)
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = tween(550, easing = FastOutSlowInEasing),
            )
            rotation.snapTo(0f)
        }
    }

    val angle = rotation.value
    val alpha = if (angle in 80f..280f) 0f else 1f

    return graphicsLayer {
        rotationY = angle
        this.alpha = alpha
        cameraDistance = 12f * density.density
    }
}

@Composable
private fun Modifier.fadeAnimation(toggleCount: Int): Modifier {
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(toggleCount) {
        if (toggleCount > 0) {
            alpha.snapTo(1f)
            scale.snapTo(1f)
            alpha.animateTo(0f, tween(200))
            scale.snapTo(0.93f)
            alpha.animateTo(1f, tween(200))
            scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
        }
    }

    return graphicsLayer {
        this.alpha = alpha.value
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
private fun Modifier.pulseAnimation(toggleCount: Int, toActive: Boolean): Modifier {
    val glowProgress = remember { Animatable(0f) }

    LaunchedEffect(toggleCount) {
        if (toggleCount > 0) {
            glowProgress.snapTo(0f)
            glowProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(500, easing = FastOutSlowInEasing),
            )
        }
    }

    val glowAlpha = if (glowProgress.value in 0.01f..0.99f) {
        val peak = if (glowProgress.value < 0.4f) glowProgress.value / 0.4f
        else (1f - glowProgress.value) / 0.6f
        peak * 0.45f
    } else 0f

    return drawWithContent {
        drawContent()
        if (glowAlpha > 0f) {
            val color = if (toActive) Color(0xFF4FC3F7) else Color(0xFFEF5350)
            val radius = size.maxDimension * 0.6f * (0.8f + glowProgress.value * 0.4f)
            drawCircle(
                color = color.copy(alpha = glowAlpha),
                radius = radius,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
    }
}

@Composable
private fun Modifier.shakeAnimation(toggleCount: Int, toActive: Boolean): Modifier {
    val offsetX = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val density = LocalDensity.current
    val shakePx = with(density) { 4.dp.toPx() }

    LaunchedEffect(toggleCount) {
        if (toggleCount > 0) {
            if (toActive) {
                scale.snapTo(1f)
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = 450
                        1.15f at 180 using FastOutSlowInEasing
                        0.95f at 300
                        1f at 450
                    },
                )
            } else {
                offsetX.snapTo(0f)
                offsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 450
                        shakePx at 75
                        -shakePx at 150
                        shakePx at 225
                        -shakePx at 300
                        shakePx * 0.5f at 375
                        0f at 450
                    },
                )
            }
        }
    }

    return graphicsLayer {
        translationX = offsetX.value
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
private fun Modifier.wobbleAnimation(toggleCount: Int): Modifier {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(toggleCount) {
        if (toggleCount > 0) {
            rotation.snapTo(0f)
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 450
                    8f at 75 using FastOutSlowInEasing
                    -8f at 150
                    6f at 225
                    -6f at 300
                    3f at 375
                    0f at 450
                },
            )
        }
    }

    return graphicsLayer { rotationZ = rotation.value }
}

@Composable
private fun Modifier.spinAnimation(toggleCount: Int): Modifier {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(toggleCount) {
        if (toggleCount > 0) {
            rotation.snapTo(0f)
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            )
            rotation.snapTo(0f)
        }
    }

    return graphicsLayer { rotationZ = rotation.value }
}

@Composable
private fun Modifier.squishAnimation(toggleCount: Int): Modifier {
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }

    LaunchedEffect(toggleCount) {
        if (toggleCount > 0) {
            scaleX.snapTo(1f)
            scaleY.snapTo(1f)
            coroutineScope {
                launch {
                    scaleX.animateTo(
                        targetValue = 1f,
                        animationSpec = keyframes {
                            durationMillis = 500
                            1.18f at 150 using FastOutSlowInEasing
                            0.92f at 300
                            1f at 500
                        },
                    )
                }
                scaleY.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = 500
                        0.85f at 150 using FastOutSlowInEasing
                        1.08f at 300
                        1f at 500
                    },
                )
            }
        }
    }

    return graphicsLayer {
        this.scaleX = scaleX.value
        this.scaleY = scaleY.value
    }
}

@Composable
private fun Modifier.tiltAnimation(toggleCount: Int): Modifier {
    val rotation = remember { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(toggleCount) {
        if (toggleCount > 0) {
            rotation.snapTo(0f)
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 500
                    25f at 150 using FastOutSlowInEasing
                    -10f at 300
                    0f at 500
                },
            )
        }
    }

    return graphicsLayer {
        rotationX = rotation.value
        cameraDistance = 12f * density.density
    }
}

@Composable
private fun Modifier.heartbeatAnimation(toggleCount: Int): Modifier {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(toggleCount) {
        if (toggleCount > 0) {
            scale.snapTo(1f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 600
                    1.15f at 100 using FastOutSlowInEasing
                    1f at 200
                    1.12f at 320 using FastOutSlowInEasing
                    1f at 450
                    1f at 600
                },
            )
        }
    }

    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
private fun Modifier.swingAnimation(toggleCount: Int): Modifier {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(toggleCount) {
        if (toggleCount > 0) {
            rotation.snapTo(0f)
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 600
                    15f at 120 using FastOutSlowInEasing
                    -12f at 240 using FastOutSlowInEasing
                    8f at 360
                    -5f at 480
                    0f at 600
                },
            )
        }
    }

    return graphicsLayer {
        rotationZ = rotation.value
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
}

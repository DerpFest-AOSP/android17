/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.ui.compose

import android.content.Context
import android.database.ContentObserver
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.brightness.ui.compose.AnimationSpecs.IconAppearSpec
import com.android.systemui.brightness.ui.compose.AnimationSpecs.IconDisappearSpec
import com.android.systemui.brightness.ui.compose.Dimensions.IconPadding
import com.android.systemui.brightness.ui.compose.Dimensions.IconSize
import com.android.systemui.brightness.ui.compose.Dimensions.ThumbTrackGapSize
import com.android.systemui.brightness.ui.compose.Dimensions.TrackHeight
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors
import kotlinx.coroutines.coroutineScope
import lineageos.providers.LineageSettings

data class QsSliderGradient(val brush: Brush, val endColor: Color)

enum class QsSliderIconAlignment {
    /** Icon at the start of the track. */
    Start,
    /** Icon at the end of the track (brightness and QS volume). */
    End,
}

@Composable
fun rememberSliderShapeMode(): Int {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    fun readShapeMode(): Int {
        return try {
            Settings.System.getIntForUser(
                contentResolver,
                Settings.System.QS_BRIGHTNESS_SLIDER_SHAPE,
                0,
                UserHandle.USER_CURRENT,
            )
        } catch (_: Throwable) {
            0
        }
    }

    var shapeMode by remember { mutableIntStateOf(readShapeMode()) }

    DisposableEffect(contentResolver) {
        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    context.mainExecutor.execute { shapeMode = readShapeMode() }
                }
            }

        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.QS_BRIGHTNESS_SLIDER_SHAPE),
            false,
            observer,
            UserHandle.USER_ALL,
        )

        onDispose { contentResolver.unregisterContentObserver(observer) }
    }

    return shapeMode
}

@Composable
fun rememberQsAutoBrightnessButtonVisible(): Boolean {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val hasAutoBrightness =
        remember(context) {
            context.resources.getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available
            )
        }

    fun readShowAutoBrightness(): Boolean =
        try {
            LineageSettings.Secure.getIntForUser(
                contentResolver,
                LineageSettings.Secure.QS_SHOW_AUTO_BRIGHTNESS,
                1,
                UserHandle.USER_CURRENT,
            ) != 0
        } catch (_: Throwable) {
            false
        }

    var showAutoBrightness by remember { mutableStateOf(readShowAutoBrightness()) }

    DisposableEffect(contentResolver, hasAutoBrightness) {
        if (hasAutoBrightness) {
            val observer =
                object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        context.mainExecutor.execute {
                            showAutoBrightness = readShowAutoBrightness()
                        }
                    }
                }

            contentResolver.registerContentObserver(
                LineageSettings.Secure.getUriFor(LineageSettings.Secure.QS_SHOW_AUTO_BRIGHTNESS),
                false,
                observer,
                UserHandle.USER_ALL,
            )

            onDispose { contentResolver.unregisterContentObserver(observer) }
        } else {
            onDispose {}
        }
    }

    return hasAutoBrightness && showAutoBrightness
}

fun qsSliderTrackCornerDp(shapeMode: Int): Dp =
    when (shapeMode) {
        1 -> 24.dp /* Circle */
        2 -> 12.dp /* Rounded Square */
        3 -> 0.dp /* Square */
        else -> 12.dp
    }

@Composable
fun rememberQsSliderGradient(): QsSliderGradient? {
    val gradientEnabled = rememberQsSliderGradientEnabled()
    if (!gradientEnabled) {
        return null
    }
    val (customStart, customEnd) = rememberQsGradientCustomColors()
    val context = LocalContext.current
    val resources = LocalResources.current
    val isDark = isSystemInDarkTheme()

    val defaultStart =
        remember(isDark, resources, context.theme) {
            val id =
                if (isDark) R.color.derpfestui_color_gradient_start_dark
                else R.color.derpfestui_color_gradient_start_light
            Color(resources.getColor(id, context.theme))
        }
    val defaultEnd =
        remember(isDark, resources, context.theme) {
            val id =
                if (isDark) R.color.derpfestui_color_gradient_end_dark
                else R.color.derpfestui_color_gradient_end_light
            Color(resources.getColor(id, context.theme))
        }
    val start = customStart ?: defaultStart
    val end = customEnd ?: defaultEnd
    val startOpaque = start.copy(alpha = 1f)
    val endOpaque = end.copy(alpha = 1f)

    val colors =
        remember(startOpaque, endOpaque) {
            if (startOpaque == endOpaque) {
                listOf(startOpaque.lighten(0.2f), startOpaque, startOpaque.darken(0.2f))
            } else {
                listOf(startOpaque, endOpaque)
            }
        }

    val brush = remember(colors) { Brush.linearGradient(colors) }

    return remember(brush, endOpaque) { QsSliderGradient(brush = brush, endColor = endOpaque) }
}

@Composable
fun rememberQsSliderColors(gradient: QsSliderGradient?): SliderColors {
    val gradientBrush = gradient?.brush
    val useNewTint = rememberQsSliderUseNewTint()
    return qsSliderColors(gradientBrush != null, gradient?.endColor, useNewTint)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QsHorizontalSliderTrack(
    sliderState: SliderState,
    colors: SliderColors,
    trackCornerDp: Dp,
    gradientBrush: Brush?,
    iconAlignment: QsSliderIconAlignment,
    iconPainter: Painter?,
    modifier: Modifier = Modifier,
) {
    var showIconActive by remember { mutableStateOf(true) }
    val iconActiveAlphaAnimatable =
        remember {
            Animatable(
                initialValue = 1f,
                typeConverter = Float.VectorConverter,
                label = "qsSliderIconActiveAlpha",
            )
        }
    val iconInactiveAlphaAnimatable =
        remember {
            Animatable(
                initialValue = 0f,
                typeConverter = Float.VectorConverter,
                label = "qsSliderIconInactiveAlpha",
            )
        }

    LaunchedEffect(iconActiveAlphaAnimatable, iconInactiveAlphaAnimatable, showIconActive) {
        coroutineScope {
            if (showIconActive) {
                launch { iconActiveAlphaAnimatable.animateTo(1f, IconAppearSpec) }
                launch { iconInactiveAlphaAnimatable.animateTo(0f, IconDisappearSpec) }
            } else {
                launch { iconActiveAlphaAnimatable.animateTo(0f, IconDisappearSpec) }
                launch { iconInactiveAlphaAnimatable.animateTo(1f, IconAppearSpec) }
            }
        }
    }

    val activeIconColor = colors.activeTickColor
    val inactiveIconColor = colors.inactiveTickColor
    val trackIcon: DrawScope.(Offset, Color, Float) -> Unit =
        remember(iconPainter) {
            val painter = iconPainter
            if (painter == null) {
                { _, _, _ -> }
            } else {
                { offset, color, alpha ->
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    scale(if (rtl) -1f else 1f, 1f) {
                        translate(offset.x, offset.y) {
                            with(painter) {
                                draw(
                                    IconSize.toSize(),
                                    colorFilter = ColorFilter.tint(color),
                                    alpha = alpha,
                                )
                            }
                        }
                    }
                }
            }
        }

    SliderDefaults.Track(
        sliderState = sliderState,
        modifier =
            modifier
                .height(TrackHeight)
                .drawWithContent {
                    val trackCornerPx = trackCornerDp.toPx()
                    val sliderFraction = sliderState.coercedValueAsFraction
                    val gapPx = ThumbTrackGapSize.toPx()
                    val activeTrackStart = 0f
                    val activeTrackEnd =
                        (size.width * sliderFraction - gapPx).coerceIn(0f, size.width)
                    val inactiveTrackStart =
                        (activeTrackEnd + gapPx * 2).coerceIn(0f, size.width)
                    val inactiveTrackEnd = size.width

                    if (gradientBrush != null) {
                        if (activeTrackEnd > activeTrackStart) {
                            clipRect(
                                left = activeTrackStart,
                                top = 0f,
                                right = activeTrackEnd,
                                bottom = size.height,
                            ) {
                                drawRoundRect(
                                    brush = gradientBrush,
                                    size = size,
                                    cornerRadius = CornerRadius(trackCornerPx, trackCornerPx),
                                )
                            }
                        }
                        if (inactiveTrackStart < inactiveTrackEnd) {
                            clipRect(
                                left = inactiveTrackStart,
                                top = 0f,
                                right = inactiveTrackEnd,
                                bottom = size.height,
                            ) {
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = 0.35f),
                                    size = size,
                                    cornerRadius = CornerRadius(trackCornerPx, trackCornerPx),
                                )
                            }
                        }
                    }

                    drawContent()

                    if (iconPainter == null) {
                        return@drawWithContent
                    }

                    val yOffset = size.height / 2 - IconSize.toSize().height / 2
                    val activeTrackWidth = activeTrackEnd - activeTrackStart
                    val inactiveTrackWidth = inactiveTrackEnd - inactiveTrackStart
                    val iconWidth = IconSize.toSize().width
                    val iconPadding = IconPadding.toPx()

                    when (iconAlignment) {
                        QsSliderIconAlignment.End -> {
                            if (iconWidth < inactiveTrackWidth - iconPadding * 2) {
                                showIconActive = false
                                trackIcon(
                                    Offset(inactiveTrackEnd - iconWidth - iconPadding, yOffset),
                                    inactiveIconColor,
                                    iconInactiveAlphaAnimatable.value,
                                )
                            } else if (iconWidth < activeTrackWidth - iconPadding * 2) {
                                showIconActive = true
                                trackIcon(
                                    Offset(activeTrackEnd - iconWidth - iconPadding, yOffset),
                                    activeIconColor,
                                    iconActiveAlphaAnimatable.value,
                                )
                            }
                        }
                        QsSliderIconAlignment.Start -> {
                            val inactiveLeftEnd =
                                (size.width * (1f - sliderFraction) - gapPx * 2)
                                    .coerceIn(0f, size.width)
                            val inactiveLeftWidth = inactiveLeftEnd
                            if (iconWidth < inactiveLeftWidth - iconPadding * 2) {
                                showIconActive = false
                                trackIcon(
                                    Offset(iconPadding, yOffset),
                                    inactiveIconColor,
                                    iconInactiveAlphaAnimatable.value,
                                )
                            } else if (iconWidth < activeTrackWidth - iconPadding * 2) {
                                showIconActive = true
                                trackIcon(
                                    Offset(iconPadding, yOffset),
                                    activeIconColor,
                                    iconActiveAlphaAnimatable.value,
                                )
                            }
                        }
                    }
                },
        trackCornerSize = trackCornerDp,
        trackInsideCornerSize = 2.dp,
        drawStopIndicator = null,
        thumbTrackGapSize = ThumbTrackGapSize,
        colors = colors,
    )
}

@Composable
private fun rememberQsSliderGradientEnabled(): Boolean {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    fun readGradientEnabled(): Boolean {
        return try {
            Settings.System.getIntForUser(
                contentResolver,
                Settings.System.QS_BRIGHTNESS_GRADIENT_ENABLED,
                1,
                UserHandle.USER_CURRENT,
            ) == 1
        } catch (_: Throwable) {
            true
        }
    }

    var gradientEnabled by remember { mutableStateOf(readGradientEnabled()) }

    DisposableEffect(contentResolver) {
        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    context.mainExecutor.execute { gradientEnabled = readGradientEnabled() }
                }
            }

        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.QS_BRIGHTNESS_GRADIENT_ENABLED),
            false,
            observer,
            UserHandle.USER_ALL,
        )

        onDispose { contentResolver.unregisterContentObserver(observer) }
    }

    return gradientEnabled
}

@Composable
fun rememberQsSliderUseNewTint(): Boolean {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    fun read(): Int =
        try {
            Settings.System.getIntForUser(
                contentResolver,
                Settings.System.QS_BRIGHTNESS_USE_NEW_TINT,
                0,
                UserHandle.USER_CURRENT,
            )
        } catch (_: Throwable) {
            0
        }
    var value by remember { mutableIntStateOf(read()) }
    DisposableEffect(contentResolver) {
        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    context.mainExecutor.execute { value = read() }
                }
            }
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.QS_BRIGHTNESS_USE_NEW_TINT),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        onDispose { contentResolver.unregisterContentObserver(observer) }
    }
    return value == 1
}

@Composable
private fun rememberQsGradientCustomColors(): Pair<Color?, Color?> {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    fun readStart(): Int {
        return try {
            Settings.System.getIntForUser(
                contentResolver,
                Settings.System.GRADIENT_START_COLOR,
                0,
                UserHandle.USER_CURRENT,
            )
        } catch (_: Throwable) {
            0
        }
    }

    fun readEnd(): Int {
        return try {
            Settings.System.getIntForUser(
                contentResolver,
                Settings.System.GRADIENT_END_COLOR,
                0,
                UserHandle.USER_CURRENT,
            )
        } catch (_: Throwable) {
            0
        }
    }

    var startArgb by remember { mutableIntStateOf(readStart()) }
    var endArgb by remember { mutableIntStateOf(readEnd()) }

    DisposableEffect(contentResolver) {
        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    context.mainExecutor.execute {
                        startArgb = readStart()
                        endArgb = readEnd()
                    }
                }
            }
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.GRADIENT_START_COLOR),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.GRADIENT_END_COLOR),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        onDispose { contentResolver.unregisterContentObserver(observer) }
    }

    return Pair(
        if (startArgb == 0) null else gradientSettingArgbToColor(startArgb),
        if (endArgb == 0) null else gradientSettingArgbToColor(endArgb),
    )
}

@Composable
private fun qsSliderColors(
    gradientEnabled: Boolean,
    gradientEndColor: Color?,
    useNewTint: Boolean,
): SliderColors {
    val context = LocalContext.current
    return if (gradientEnabled) {
        val tickColor =
            if (gradientEndColor != null) {
                Color(BatteryColors.textColorOnBackground(context, gradientEndColor.toArgb()))
            } else {
                MaterialTheme.colorScheme.onPrimary
            }
        SliderDefaults.colors()
            .copy(
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = tickColor,
                inactiveTickColor = tickColor,
            )
    } else if (useNewTint) {
        val primary = MaterialTheme.colorScheme.primary
        SliderDefaults.colors()
            .copy(
                thumbColor = primary,
                activeTrackColor = primary,
                inactiveTrackColor = primary.copy(alpha = 0.2f),
                activeTickColor = MaterialTheme.colorScheme.onPrimary,
                inactiveTickColor = MaterialTheme.colorScheme.onSurface,
            )
    } else {
        SliderDefaults.colors()
            .copy(
                inactiveTrackColor = LocalAndroidColorScheme.current.surfaceEffect1,
                activeTickColor = MaterialTheme.colorScheme.onPrimary,
                inactiveTickColor = MaterialTheme.colorScheme.onSurface,
            )
    }
}

private fun gradientSettingArgbToColor(argb: Int): Color {
    val a = (argb shr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val alpha = if (a == 0) 1f else a / 255f
    return Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = alpha)
}

private fun Color.lighten(amount: Float): Color = blendWith(Color.White, amount)

private fun Color.darken(amount: Float): Color = blendWith(Color.Black, amount)

private fun Color.blendWith(other: Color, ratio: Float): Color {
    return Color(ColorUtils.blendARGB(this.toArgb(), other.toArgb(), ratio))
}

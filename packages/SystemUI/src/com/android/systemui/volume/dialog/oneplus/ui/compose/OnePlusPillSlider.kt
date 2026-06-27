/*
 * SPDX-FileCopyrightText: The BlissRoms Project
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.volume.dialog.oneplus.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogSliderViewModel

/**
 * Drives how the track/fill pick colors. [OnWallpaper] is for the single collapsed pill over the
 * live wallpaper, where a frosted-white pill is nearly invisible in light mode. [OnDarkScrim] is
 * for the expanded full-screen dim where light pills and labels are always correct.
 */
enum class OnePlusPillStyling {
    OnWallpaper,
    OnDarkScrim,
}

private data class OnePlusPillStyleColors(
    val track: Color,
    val fill: Color,
    val iconTint: Color,
    val label: Color,
)

@Composable
private fun onePlusPillStyleColors(styling: OnePlusPillStyling): OnePlusPillStyleColors {
    if (styling == OnePlusPillStyling.OnDarkScrim) {
        return OnePlusPillStyleColors(
            track = Color.White.copy(alpha = 0.15f),
            fill = Color.White,
            iconTint = Color.White,
            label = Color.White,
        )
    }
    val isDark = isSystemInDarkTheme()
    return if (isDark) {
        // Frosted light pill on a typically dark / busy background.
        OnePlusPillStyleColors(
            track = Color.White.copy(alpha = 0.2f),
            fill = Color.White,
            iconTint = Color(0.1f, 0.1f, 0.1f, 1f),
            label = MaterialTheme.colorScheme.onBackground,
        )
    } else {
        // Solid dark-surface pill: visible on bright wallpapers in light mode.
        val onSurface = MaterialTheme.colorScheme.onSurface
        OnePlusPillStyleColors(
            track = Color.Black.copy(alpha = 0.32f),
            fill = onSurface,
            iconTint = Color.White,
            label = onSurface,
        )
    }
}

@Composable
fun OnePlusPillSlider(
    viewModel: VolumeDialogSliderViewModel,
    sliderWidth: Dp = 64.dp,
    sliderHeight: Dp = 200.dp,
    modifier: Modifier = Modifier,
    styling: OnePlusPillStyling = OnePlusPillStyling.OnWallpaper,
) {
    val collectedState by viewModel.state.collectAsStateWithLifecycle(null)
    val state = collectedState ?: return

    val range = state.valueRange
    val fraction = if (range.endInclusive > range.start) {
        ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    } else 0f

    var isDragging by remember { mutableStateOf(false) }
    var currentFraction by remember { mutableFloatStateOf(fraction) }
    if (!isDragging) {
        currentFraction = fraction
    }

    val sliderShape = RoundedCornerShape(24.dp)
    val colors = onePlusPillStyleColors(styling)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .width(sliderWidth)
                .height(sliderHeight)
                .clip(sliderShape)
                .background(colors.track)
                .pointerInput(range) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            viewModel.onSliderDragStarted()
                        },
                        onDragEnd = {
                            isDragging = false
                            viewModel.onSliderDragFinished()
                        },
                        onDragCancel = {
                            isDragging = false
                            viewModel.onSliderDragFinished()
                        },
                    ) { _, dragAmount ->
                        val delta = -dragAmount / size.height.toFloat()
                        currentFraction = (currentFraction + delta).coerceIn(0f, 1f)
                        val newValue = range.start +
                            currentFraction * (range.endInclusive - range.start)
                        viewModel.setStreamVolume(
                            newValue.coerceIn(range.start, range.endInclusive),
                            true,
                        )
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sliderHeight * currentFraction)
                    .background(colors.fill),
            )

            Icon(
                icon = state.icon,
                tint = { colors.iconTint },
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(24.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.label,
            color = colors.label,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

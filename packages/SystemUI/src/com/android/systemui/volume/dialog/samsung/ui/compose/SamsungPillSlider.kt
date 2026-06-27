/*
 * SPDX-FileCopyrightText: The BlissRoms Project
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.volume.dialog.samsung.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogSliderViewModel

/**
 * [OnWallpaper] = collapsed single pill (needs contrast on bright wallpapers in light mode).
 * [InExpandedFrost] = sliders in the frosted card; keep the light frosted pill look.
 */
enum class SamsungPillStyling {
    OnWallpaper,
    InExpandedFrost,
}

private data class SamsungPillStyleColors(
    val track: Color,
    val fill: Color,
    val streamIconTint: Color,
    val moreVertTint: Color,
)

@Composable
private fun samsungPillStyleColors(styling: SamsungPillStyling): SamsungPillStyleColors {
    if (styling == SamsungPillStyling.InExpandedFrost) {
        return SamsungPillStyleColors(
            track = Color.White.copy(alpha = 0.15f),
            fill = Color.White.copy(alpha = 0.65f),
            streamIconTint = Color.White,
            moreVertTint = Color.White,
        )
    }
    val isDark = isSystemInDarkTheme()
    return if (isDark) {
        SamsungPillStyleColors(
            track = Color.White.copy(alpha = 0.15f),
            fill = Color.White.copy(alpha = 0.65f),
            streamIconTint = Color(0.1f, 0.1f, 0.1f, 1f),
            moreVertTint = Color(0.1f, 0.1f, 0.1f, 1f),
        )
    } else {
        val onSurface = MaterialTheme.colorScheme.onSurface
        SamsungPillStyleColors(
            track = Color.Black.copy(alpha = 0.32f),
            fill = onSurface.copy(alpha = 0.7f),
            streamIconTint = Color.White,
            moreVertTint = onSurface,
        )
    }
}

@Composable
fun SamsungPillSlider(
    viewModel: VolumeDialogSliderViewModel,
    sliderWidth: Dp = 56.dp,
    sliderHeight: Dp = 200.dp,
    showIcon: Boolean = true,
    onExpandClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    styling: SamsungPillStyling = SamsungPillStyling.OnWallpaper,
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

    val animatedFraction by animateFloatAsState(
        targetValue = currentFraction,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 250),
        label = "sliderFill",
    )

    val colors = samsungPillStyleColors(styling)
    val trackColor = colors.track
    val fillColor = colors.fill
    val pillShape = RoundedCornerShape(50)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .width(sliderWidth)
                .height(sliderHeight)
                .clip(pillShape)
                .background(trackColor)
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
                    .fillMaxSize()
                    .drawWithContent {
                        val fillTop = size.height * (1f - animatedFraction)
                        clipRect(
                            left = 0f,
                            top = fillTop,
                            right = size.width,
                            bottom = size.height,
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    .background(fillColor),
            )

            if (onExpandClicked != null) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = colors.moreVertTint,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .size(20.dp)
                        .clickable { onExpandClicked() },
                )
            }

            if (showIcon) {
                Icon(
                    icon = state.icon,
                    tint = { colors.streamIconTint },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .size(22.dp),
                )
            }
        }
    }
}

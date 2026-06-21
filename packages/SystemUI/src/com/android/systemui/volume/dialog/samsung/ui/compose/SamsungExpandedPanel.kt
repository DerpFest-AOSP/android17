/*
 * SPDX-FileCopyrightText: The BlissRoms Project
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.volume.dialog.samsung.ui.compose

import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import com.android.systemui.volume.dialog.sliders.dagger.VolumeDialogSliderComponent
import com.android.systemui.volume.dialog.ui.compose.rememberVolumePanelBlurDrawable
import com.android.systemui.volume.dialog.ui.compose.volumePanelBackgroundBlur
import com.android.systemui.volume.dialog.ui.compose.volumePanelBlurSurfaceColor

@Composable
fun SamsungExpandedPanel(
    sliderComponents: List<VolumeDialogSliderComponent>,
    activeLabel: String,
    ringerMode: Int,
    onMuteClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onDismiss: () -> Unit,
    isBlurSupported: Boolean,
    modifier: Modifier = Modifier,
) {
    val ringerIcon = when (ringerMode) {
        AudioManager.RINGER_MODE_VIBRATE -> Icons.Default.Vibration
        AudioManager.RINGER_MODE_SILENT -> Icons.Default.VolumeOff
        else -> Icons.Default.VolumeUp
    }

    val cardHorizontalPadding = dimensionResource(R.dimen.volume_dialog_samsung_card_horizontal_padding)
    val cardVerticalPadding = dimensionResource(R.dimen.volume_dialog_samsung_card_vertical_padding)
    val sliderHeight = dimensionResource(R.dimen.volume_dialog_samsung_expanded_slider_height)
    val sliderSpacing = dimensionResource(R.dimen.volume_dialog_samsung_slider_spacing)

    val isDark = isSystemInDarkTheme()
    val cardShape = RoundedCornerShape(28.dp)
    val cardBlurRadiusPx =
        androidx.compose.ui.platform.LocalContext.current.resources.getDimensionPixelSize(
            R.dimen.volume_dialog_samsung_blur_radius
        )
    val blurDrawable = rememberVolumePanelBlurDrawable(key = "samsung_expanded_card")
    val cardBackground =
        if (isBlurSupported) {
            volumePanelBlurSurfaceColor(isBlurSupported = true)
        } else if (isDark) {
            Color.White.copy(alpha = 0.25f)
        } else {
            Color.Black.copy(alpha = 0.4f)
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = cardHorizontalPadding, vertical = cardVerticalPadding)
                .volumePanelBackgroundBlur(
                    blurDrawable = blurDrawable,
                    isBlurSupported = isBlurSupported,
                    blurRadiusPx = cardBlurRadiusPx,
                    cornerRadius = 28.dp,
                )
                .clip(cardShape)
                .background(cardBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = ringerIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onMuteClicked() }
                        .padding(8.dp),
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = activeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onSettingsClicked() }
                        .padding(8.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(sliderSpacing),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (component in sliderComponents) {
                    SamsungPillSlider(
                        viewModel = component.sliderViewModel(),
                        sliderHeight = sliderHeight,
                        showIcon = true,
                        modifier = Modifier.weight(1f),
                        styling = SamsungPillStyling.InExpandedFrost,
                    )
                }
            }
        }
    }
}

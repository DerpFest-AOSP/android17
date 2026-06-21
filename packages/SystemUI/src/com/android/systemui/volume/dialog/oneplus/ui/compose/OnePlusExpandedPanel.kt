/*
 * SPDX-FileCopyrightText: The BlissRoms Project
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.volume.dialog.oneplus.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.res.R
import com.android.systemui.volume.dialog.sliders.dagger.VolumeDialogSliderComponent
import com.android.systemui.volume.dialog.ui.compose.rememberVolumePanelBlurDrawable
import com.android.systemui.volume.dialog.ui.compose.volumePanelBackgroundBlur

@Composable
fun OnePlusExpandedPanel(
    sliderComponents: List<VolumeDialogSliderComponent>,
    onSettingsClicked: () -> Unit,
    onDismiss: () -> Unit,
    isBlurSupported: Boolean,
    modifier: Modifier = Modifier,
) {
    val sliderHeight = dimensionResource(R.dimen.volume_dialog_oneplus_expanded_slider_height)
    val sliderSpacing = dimensionResource(R.dimen.volume_dialog_oneplus_slider_spacing)
    val horizontalPadding = dimensionResource(R.dimen.volume_dialog_oneplus_expanded_horizontal_padding)
    val expandedBlurRadiusPx =
        androidx.compose.ui.platform.LocalContext.current.resources.getDimensionPixelSize(
            R.dimen.volume_dialog_oneplus_blur_radius
        )
    val blurDrawable = rememberVolumePanelBlurDrawable(key = "oneplus_expanded")
    val scrimColor =
        if (isBlurSupported) {
            Color.Black.copy(alpha = 0.35f)
        } else {
            Color.Black.copy(alpha = 0.85f)
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .volumePanelBackgroundBlur(
                blurDrawable = blurDrawable,
                isBlurSupported = isBlurSupported,
                blurRadiusPx = expandedBlurRadiusPx,
                cornerRadius = 0.dp,
            )
            .background(scrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            Image(
                painter = painterResource(R.drawable.ic_speaker_on),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(48.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.volume_panel_system_volume),
                color = Color.White,
                fontSize = 18.sp,
            )

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(sliderSpacing),
                modifier = Modifier.padding(horizontal = horizontalPadding),
            ) {
                for (component in sliderComponents) {
                    OnePlusPillSlider(
                        viewModel = component.sliderViewModel(),
                        sliderHeight = sliderHeight,
                        modifier = Modifier.weight(1f),
                        styling = OnePlusPillStyling.OnDarkScrim,
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Image(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { onSettingsClicked() }
                    .padding(12.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.volume_panel_settings),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        }
    }
}

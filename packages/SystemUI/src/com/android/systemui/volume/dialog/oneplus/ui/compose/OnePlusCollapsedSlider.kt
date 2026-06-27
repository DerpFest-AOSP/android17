/*
 * SPDX-FileCopyrightText: The BlissRoms Project
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.volume.dialog.oneplus.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogSliderViewModel

@Composable
fun OnePlusCollapsedSlider(
    viewModel: VolumeDialogSliderViewModel,
    onExpandClicked: () -> Unit,
    isOnLeft: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sliderWidth = dimensionResource(R.dimen.volume_dialog_oneplus_collapsed_slider_width)
    val sliderHeight = dimensionResource(R.dimen.volume_dialog_oneplus_collapsed_slider_height)
    val verticalPadding = dimensionResource(R.dimen.volume_dialog_oneplus_collapsed_vertical_padding)

    val edgePadding = if (isOnLeft) {
        Modifier.padding(start = 16.dp, top = verticalPadding, bottom = verticalPadding)
    } else {
        Modifier.padding(end = 16.dp, top = verticalPadding, bottom = verticalPadding)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.then(edgePadding),
    ) {
        OnePlusPillSlider(
            viewModel = viewModel,
            sliderWidth = sliderWidth,
            sliderHeight = sliderHeight,
        )

        Spacer(modifier = Modifier.height(12.dp))

        val onBg = MaterialTheme.colorScheme.onBackground
        Image(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = null,
            colorFilter = ColorFilter.tint(onBg),
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(onBg.copy(alpha = 0.15f))
                .clickable { onExpandClicked() }
                .padding(6.dp),
        )
    }
}

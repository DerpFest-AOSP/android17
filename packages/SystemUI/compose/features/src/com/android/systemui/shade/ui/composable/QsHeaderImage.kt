/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.shade.ui.composable

import android.content.res.Configuration
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.derpfest.header.QsHeaderImageViewModel
import com.android.systemui.res.R
import com.bosphere.fadingedgelayout.FadingEdgeLayout
import kotlin.math.roundToInt

/** OmniStyle QS header image, shown behind tiles-shade content in portrait. */
@Composable
fun QsHeaderImage(viewModel: QsHeaderImageViewModel, modifier: Modifier = Modifier) {
    val drawable by viewModel.drawable.collectAsStateWithLifecycle()
    val expansion by viewModel.qsExpansion.collectAsStateWithLifecycle()
    val heightDp by viewModel.heightDp.collectAsStateWithLifecycle()
    val shadow by viewModel.shadow.collectAsStateWithLifecycle()
    val orientation = LocalConfiguration.current.orientation

    val image = drawable
    if (
        image == null ||
            expansion <= 0f ||
            orientation == Configuration.ORIENTATION_LANDSCAPE
    ) {
        return
    }

    val alpha = (expansion * (255 - shadow).coerceIn(0, 255)).roundToInt().coerceIn(0, 255)
    val context = LocalContext.current
    val fadePx =
        TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                heightDp * QsHeaderImageViewModel.FADE_FRACTION,
                context.resources.displayMetrics,
            )
            .roundToInt()

    AndroidView(
        factory = { viewContext ->
            LayoutInflater.from(viewContext)
                .inflate(R.layout.qs_header_image, null) as FadingEdgeLayout
        },
        update = { layout ->
            layout.setFadeEdges(false, false, true, false)
            layout.setFadeSizes(/* top= */ 0, /* left= */ 0, fadePx, /* right= */ 0)
            val imageView =
                layout.requireViewById<ImageView>(R.id.qs_header_image_view_compose)
            if (imageView.drawable !== image) {
                imageView.setImageDrawable(image)
            }
            imageView.imageAlpha = alpha
        },
        modifier = modifier.fillMaxWidth().height(heightDp.dp),
    )
}

/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.volume.dialog.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.systemui.res.R

/** Creates a per-composable [BackgroundBlurDrawable] for cross-window blur regions. */
@Composable
fun rememberVolumePanelBlurDrawable(key: Any? = Unit): BackgroundBlurDrawable? {
    val view = LocalView.current
    return remember(view, key) {
        view.viewRootImpl?.createBackgroundBlurDrawable()?.apply { setBlurRadius(0) }
    }
}

@Composable
fun volumePanelBlurSurfaceColor(isBlurSupported: Boolean): Color {
    return colorResource(
        if (isBlurSupported) {
            R.color.volume_dialog_view_background_blur
        } else {
            R.color.volume_dialog_view_background_blur_fallback
        }
    )
}

fun Modifier.volumePanelBackgroundBlur(
    blurDrawable: BackgroundBlurDrawable?,
    isBlurSupported: Boolean,
    blurRadiusPx: Int,
    cornerRadius: Dp,
    alpha: Float = 1f,
): Modifier =
    composed {
        val cornerRadiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
        volumePanelBackgroundBlur(
            blurDrawable = blurDrawable,
            isBlurSupported = isBlurSupported,
            blurRadiusPx = blurRadiusPx,
            cornerRadiusPx = cornerRadiusPx,
            alpha = alpha,
        )
    }

fun Modifier.volumePanelBackgroundBlur(
    blurDrawable: BackgroundBlurDrawable?,
    isBlurSupported: Boolean,
    blurRadiusPx: Int,
    cornerRadiusPx: Float,
    alpha: Float = 1f,
): Modifier =
    composed {
        val drawable = blurDrawable
        SideEffect {
            if (drawable != null && !isBlurSupported) {
                drawable.setBlurRadius(0)
                drawable.setVisible(false, false)
            }
        }
        if (drawable == null || !isBlurSupported || alpha <= 0f) {
            return@composed this
        }
        drawBehind {
            val effectiveAlpha = alpha.coerceIn(0f, 1f)
            drawable.apply {
                setBlurRadius(blurRadiusPx)
                setCornerRadius(cornerRadiusPx)
                this.alpha = (255 * effectiveAlpha).toInt()
                setVisible(effectiveAlpha > 0f, false)
                setBounds(0, 0, size.width.toInt(), size.height.toInt())
            }
            drawIntoCanvas { canvas -> drawable.draw(canvas.nativeCanvas) }
        }
    }

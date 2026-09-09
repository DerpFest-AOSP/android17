/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.volume.dialog.ringer.ui.viewmodel

import android.content.Context
import com.android.internal.R as internalR
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors

/** Models the UI state of ringer button */
data class RingerButtonUiModel(
    /** Icon color. */
    val tintColor: Int,
    val backgroundColor: Int,
    val cornerRadius: Int,
    /** When set, the selected background is a two-stop gradient ending at [backgroundColor]. */
    val gradientStartColor: Int? = null,
) {
    companion object {
        fun getUnselectedButton(context: Context): RingerButtonUiModel {
            return RingerButtonUiModel(
                tintColor = context.getColor(internalR.color.materialColorOnSurface),
                backgroundColor = context.getColor(
                    internalR.color.materialColorSurfaceContainerHighest),
                cornerRadius = context.resources.getDimensionPixelSize(
                    R.dimen.volume_dialog_background_square_corner_radius),
            )
        }

        fun getSelectedButton(
            context: Context,
            gradientColors: Pair<Int, Int>? = null,
        ): RingerButtonUiModel {
            val cornerRadius =
                context.resources.getDimensionPixelSize(
                    R.dimen.volume_dialog_ringer_selected_button_background_radius
                )
            return if (gradientColors != null) {
                RingerButtonUiModel(
                    tintColor =
                        BatteryColors.textColorOnBackground(context, gradientColors.second),
                    backgroundColor = gradientColors.second,
                    cornerRadius = cornerRadius,
                    gradientStartColor = gradientColors.first,
                )
            } else {
                RingerButtonUiModel(
                    tintColor = context.getColor(internalR.color.materialColorOnPrimary),
                    backgroundColor = context.getColor(internalR.color.materialColorPrimary),
                    cornerRadius = cornerRadius,
                )
            }
        }
    }
}

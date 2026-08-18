/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.ui.model

/**
 * When a QS slider (brightness or volume) should appear.
 *
 * Values match LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER and
 * LineageSettings.Secure.QS_SHOW_VOLUME_SLIDER:
 * - 0 = never (hidden)
 * - 1 = show when expanded
 * - 2 = show always (including QQS)
 */
enum class QsSliderVisibility(val value: Int) {
    HIDDEN(0),
    EXPANDED(1),
    ALWAYS(2);

    fun toInt(): Int = value

    /**
     * Dual shade has no QQS, so [ALWAYS] and [EXPANDED] behave the same. Collapse [ALWAYS] to
     * [EXPANDED] for display and selection.
     */
    fun displayed(isDualShade: Boolean): QsSliderVisibility =
        if (isDualShade && this == ALWAYS) EXPANDED else this

    companion object {
        /** Parses a stored int. Unknown values fall back to [EXPANDED]. */
        fun fromInt(value: Int): QsSliderVisibility =
            entries.firstOrNull { it.value == value } ?: EXPANDED

        /** Options that actually change behavior for the current shade mode. */
        fun available(isDualShade: Boolean): List<QsSliderVisibility> =
            if (isDualShade) listOf(EXPANDED, HIDDEN) else listOf(ALWAYS, EXPANDED, HIDDEN)
    }
}

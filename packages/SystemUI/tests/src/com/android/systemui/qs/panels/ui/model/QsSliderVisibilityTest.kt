/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.ui.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class QsSliderVisibilityTest : SysuiTestCase() {

    @Test
    fun fromInt_knownValues() {
        assertThat(QsSliderVisibility.fromInt(0)).isEqualTo(QsSliderVisibility.HIDDEN)
        assertThat(QsSliderVisibility.fromInt(1)).isEqualTo(QsSliderVisibility.EXPANDED)
        assertThat(QsSliderVisibility.fromInt(2)).isEqualTo(QsSliderVisibility.ALWAYS)
    }

    @Test
    fun fromInt_unknown_returnsExpanded() {
        assertThat(QsSliderVisibility.fromInt(-1)).isEqualTo(QsSliderVisibility.EXPANDED)
        assertThat(QsSliderVisibility.fromInt(3)).isEqualTo(QsSliderVisibility.EXPANDED)
    }

    @Test
    fun toInt_roundTrips() {
        QsSliderVisibility.entries.forEach { visibility ->
            assertThat(QsSliderVisibility.fromInt(visibility.toInt())).isEqualTo(visibility)
        }
    }

    @Test
    fun available_singleShade_includesAlways() {
        assertThat(QsSliderVisibility.available(isDualShade = false))
            .containsExactly(
                QsSliderVisibility.ALWAYS,
                QsSliderVisibility.EXPANDED,
                QsSliderVisibility.HIDDEN,
            )
            .inOrder()
    }

    @Test
    fun available_dualShade_omitsAlways() {
        assertThat(QsSliderVisibility.available(isDualShade = true))
            .containsExactly(QsSliderVisibility.EXPANDED, QsSliderVisibility.HIDDEN)
            .inOrder()
    }

    @Test
    fun displayed_dualShade_collapsesAlwaysToExpanded() {
        assertThat(QsSliderVisibility.ALWAYS.displayed(isDualShade = true))
            .isEqualTo(QsSliderVisibility.EXPANDED)
        assertThat(QsSliderVisibility.EXPANDED.displayed(isDualShade = true))
            .isEqualTo(QsSliderVisibility.EXPANDED)
        assertThat(QsSliderVisibility.HIDDEN.displayed(isDualShade = true))
            .isEqualTo(QsSliderVisibility.HIDDEN)
    }
}

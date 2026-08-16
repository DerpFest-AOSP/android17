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
class QsShadeComponentTest : SysuiTestCase() {

    @Test
    fun parse_nullOrBlank_returnsDefaultOrder() {
        assertThat(QsShadeComponent.parse(null)).isEqualTo(QsShadeComponent.DEFAULT_ORDER)
        assertThat(QsShadeComponent.parse("")).isEqualTo(QsShadeComponent.DEFAULT_ORDER)
    }

    @Test
    fun parse_legacyOrder_insertsVolumeAfterBrightness() {
        assertThat(QsShadeComponent.parse("BRIGHTNESS,TILES_GRID,MEDIA"))
            .containsExactly(
                QsShadeComponent.BRIGHTNESS,
                QsShadeComponent.VOLUME,
                QsShadeComponent.TILES_GRID,
                QsShadeComponent.MEDIA,
            )
            .inOrder()
    }

    @Test
    fun parse_legacyBrightnessLast_insertsVolumeAfterBrightness() {
        assertThat(QsShadeComponent.parse("TILES_GRID,MEDIA,BRIGHTNESS"))
            .containsExactly(
                QsShadeComponent.TILES_GRID,
                QsShadeComponent.MEDIA,
                QsShadeComponent.BRIGHTNESS,
                QsShadeComponent.VOLUME,
            )
            .inOrder()
    }

    @Test
    fun parse_completeOrder_isPreserved() {
        val order =
            listOf(
                QsShadeComponent.MEDIA,
                QsShadeComponent.VOLUME,
                QsShadeComponent.TILES_GRID,
                QsShadeComponent.BRIGHTNESS,
            )
        assertThat(QsShadeComponent.parse(QsShadeComponent.serialize(order))).isEqualTo(order)
    }

    @Test
    fun parse_unknown_returnsDefaultOrder() {
        assertThat(QsShadeComponent.parse("NOPE")).isEqualTo(QsShadeComponent.DEFAULT_ORDER)
    }
}

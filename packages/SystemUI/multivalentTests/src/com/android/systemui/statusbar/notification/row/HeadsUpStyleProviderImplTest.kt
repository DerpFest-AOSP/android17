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

package com.android.systemui.statusbar.notification.row

import android.os.SystemProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class HeadsUpStyleProviderImplTest : SysuiTestCase() {

    private val headsUpStyleProvider = HeadsUpStyleProviderImpl()

    @After
    fun tearDown() {
        SystemProperties.set(ALWAYS_SHOW_COMPACT_HUN_PROPERTY, "")
    }

    @Test
    fun shouldApplyCompactStyle_default_returnsFalse() {
        val result = headsUpStyleProvider.shouldApplyCompactStyle(DISPLAY_ID)

        assertThat(result).isFalse()
    }

    @Test
    fun shouldApplyCompactStyle_propertyDisabled_returnsFalse() {
        SystemProperties.set(ALWAYS_SHOW_COMPACT_HUN_PROPERTY, "false")

        val result = headsUpStyleProvider.shouldApplyCompactStyle(DISPLAY_ID)

        assertThat(result).isFalse()
    }

    @Test
    fun shouldApplyCompactStyle_propertyEnabled_returnsTrue() {
        SystemProperties.set(ALWAYS_SHOW_COMPACT_HUN_PROPERTY, "true")

        val result = headsUpStyleProvider.shouldApplyCompactStyle(DISPLAY_ID)

        assertThat(result).isTrue()
    }

    companion object {
        private const val DISPLAY_ID = 0
        private const val ALWAYS_SHOW_COMPACT_HUN_PROPERTY =
            "persist.sys.compact_heads_up_notification.always_show"
    }
}

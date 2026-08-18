/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.qs.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.mediaPipelineRepository
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.qs.flags.ExpandedAudioDetailedView
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.domain.interactor.fakeQsBrightnessSliderVisibilityInteractor
import com.android.systemui.qs.panels.domain.interactor.fakeQsVolumeSliderVisibilityInteractor
import com.android.systemui.qs.panels.ui.model.QsSliderVisibility
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class QuickSettingsContainerViewModelTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            usingMediaInComposeFragment = false // This is not for the compose fragment
        }
    private val testScope = kosmos.testScope

    private val underTest by lazy {
        kosmos.quickSettingsContainerViewModelFactory.create(supportsBrightnessMirroring = false)
    }

    @Before
    fun setUp() {
        underTest.activateIn(testScope)
        kosmos.setDisplayType(Display.DEFAULT_DISPLAY, Display.TYPE_INTERNAL)
    }

    @Test
    fun isBrightnessSliderVisible_defaultDisplay_isVisible() =
        with(kosmos) {
            testScope.runTest {
                fakeShadeDisplaysRepository.setPendingDisplayId(Display.DEFAULT_DISPLAY)

                assertThat(underTest.isBrightnessSliderVisible).isTrue()
            }
        }

    @Test
    fun addAndRemoveMedia_mediaVisibilityIsUpdated() =
        kosmos.runTest {
            val userMedia = MediaData(active = true)

            assertThat(underTest.showMedia).isFalse()

            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)

            assertThat(underTest.showMedia).isTrue()

            mediaPipelineRepository.removeCurrentUserMediaEntry(userMedia.instanceId)

            assertThat(underTest.showMedia).isFalse()
        }

    @Test
    fun addInactiveMedia_mediaVisibilityIsUpdated() =
        kosmos.runTest {
            val userMedia = MediaData(active = false)

            assertThat(underTest.showMedia).isFalse()

            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)

            assertThat(underTest.showMedia).isTrue()
        }

    @Test
    fun isBrightnessSliderVisible_externalDisplay_isInvisible() =
        with(kosmos) {
            testScope.runTest {
                setDisplayType(Display.DEFAULT_DISPLAY + 1, Display.TYPE_EXTERNAL)
                fakeShadeDisplaysRepository.setPendingDisplayId(
                    Display.DEFAULT_DISPLAY + 1
                ) // Not default.

                assertThat(underTest.isBrightnessSliderVisible).isFalse()
            }
        }

    @Test
    fun isBrightnessSliderVisible_defaultDisplay_internal_isVisible() =
        with(kosmos) {
            testScope.runTest {
                setDisplayType(Display.DEFAULT_DISPLAY, Display.TYPE_INTERNAL)
                fakeShadeDisplaysRepository.setPendingDisplayId(Display.DEFAULT_DISPLAY)

                assertThat(underTest.isBrightnessSliderVisible).isTrue()
            }
        }

    @Test
    fun isBrightnessSliderVisible_defaultDisplay_external_isInvisible() =
        with(kosmos) {
            testScope.runTest {
                setDisplayType(Display.DEFAULT_DISPLAY, Display.TYPE_EXTERNAL)
                fakeShadeDisplaysRepository.setPendingDisplayId(Display.DEFAULT_DISPLAY)

                assertThat(underTest.isBrightnessSliderVisible).isFalse()
            }
        }

    @Test
    fun isBrightnessSliderVisible_hidden_isInvisible() =
        with(kosmos) {
            testScope.runTest {
                fakeQsBrightnessSliderVisibilityInteractor.setVisibility(QsSliderVisibility.HIDDEN)

                assertThat(underTest.isBrightnessSliderVisible).isFalse()
                assertThat(underTest.isBrightnessSliderVisibleInQqs).isFalse()
            }
        }

    @Test
    fun isBrightnessSliderVisible_expanded_visibleInQsOnly() =
        with(kosmos) {
            testScope.runTest {
                fakeQsBrightnessSliderVisibilityInteractor.setVisibility(QsSliderVisibility.EXPANDED)

                assertThat(underTest.isBrightnessSliderVisible).isTrue()
                assertThat(underTest.isBrightnessSliderVisibleInQqs).isFalse()
            }
        }

    @Test
    fun isBrightnessSliderVisible_always_visibleInQsAndQqs() =
        with(kosmos) {
            testScope.runTest {
                fakeQsBrightnessSliderVisibilityInteractor.setVisibility(QsSliderVisibility.ALWAYS)

                assertThat(underTest.isBrightnessSliderVisible).isTrue()
                assertThat(underTest.isBrightnessSliderVisibleInQqs).isTrue()
            }
        }

    @Test
    fun isBrightnessSliderVisible_always_externalDisplay_isInvisible() =
        with(kosmos) {
            testScope.runTest {
                setDisplayType(Display.DEFAULT_DISPLAY + 1, Display.TYPE_EXTERNAL)
                fakeShadeDisplaysRepository.setPendingDisplayId(Display.DEFAULT_DISPLAY + 1)
                fakeQsBrightnessSliderVisibilityInteractor.setVisibility(QsSliderVisibility.ALWAYS)

                assertThat(underTest.isBrightnessSliderVisible).isFalse()
                assertThat(underTest.isBrightnessSliderVisibleInQqs).isFalse()
            }
        }

    @DisableFlags(QsDetailedView.FLAG_NAME)
    @Test
    fun isVolumeSliderVisible_featureDisabled_isInvisible() =
        with(kosmos) {
            testScope.runTest {
                fakeQsVolumeSliderVisibilityInteractor.setVisibility(QsSliderVisibility.ALWAYS)

                assertThat(underTest.isVolumeSliderVisible).isFalse()
                assertThat(underTest.isVolumeSliderVisibleInQqs).isFalse()
            }
        }

    @EnableFlags(QsDetailedView.FLAG_NAME, ExpandedAudioDetailedView.FLAG_NAME)
    @Test
    fun isVolumeSliderVisible_hidden_isInvisible() =
        with(kosmos) {
            testScope.runTest {
                fakeQsVolumeSliderVisibilityInteractor.setVisibility(QsSliderVisibility.HIDDEN)

                assertThat(underTest.isVolumeSliderVisible).isFalse()
                assertThat(underTest.isVolumeSliderVisibleInQqs).isFalse()
            }
        }

    @EnableFlags(QsDetailedView.FLAG_NAME, ExpandedAudioDetailedView.FLAG_NAME)
    @Test
    fun isVolumeSliderVisible_expanded_visibleInQsOnly() =
        with(kosmos) {
            testScope.runTest {
                fakeQsVolumeSliderVisibilityInteractor.setVisibility(QsSliderVisibility.EXPANDED)

                assertThat(underTest.isVolumeSliderVisible).isTrue()
                assertThat(underTest.isVolumeSliderVisibleInQqs).isFalse()
            }
        }

    @EnableFlags(QsDetailedView.FLAG_NAME, ExpandedAudioDetailedView.FLAG_NAME)
    @Test
    fun isVolumeSliderVisible_always_visibleInQsAndQqs() =
        with(kosmos) {
            testScope.runTest {
                fakeQsVolumeSliderVisibilityInteractor.setVisibility(QsSliderVisibility.ALWAYS)

                assertThat(underTest.isVolumeSliderVisible).isTrue()
                assertThat(underTest.isVolumeSliderVisibleInQqs).isTrue()
            }
        }

    private fun Kosmos.setDisplayType(displayId: Int, type: Int) {
        runBlocking {
            displayRepository.removeDisplay(displayId)
            displayRepository.addDisplay(displayId, type = type)
            displayRepository.emitDisplayChangeEvent(displayId)
        }
    }
}

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

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.android.systemui.haptics.msdl.tileHapticsViewModelFactory
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.qs.panels.domain.interactor.qsPreferencesInteractor
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeTabs.EditModeTabsColors
import com.android.systemui.qs.panels.ui.model.QsShadeComponent
import com.android.systemui.qs.panels.ui.model.QsShadeComponent.BRIGHTNESS
import com.android.systemui.qs.panels.ui.model.QsShadeComponent.MEDIA
import com.android.systemui.qs.panels.ui.model.QsShadeComponent.TILES_GRID
import com.android.systemui.qs.panels.ui.viewmodel.EditModeLayoutTabViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditModeLayoutTabViewModel.DragState
import com.android.systemui.qs.panels.ui.viewmodel.EditModeTabsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.detailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.iconTilesViewModel
import com.android.systemui.qs.panels.ui.viewmodel.infiniteGridViewModelFactory
import com.android.systemui.qs.panels.ui.viewmodel.textFeedbackContentViewModelFactory

object NoOpEditModeTabs : EditModeTabs {
    @Composable
    override fun Content(
        viewModel: EditModeTabsViewModel,
        colors: EditModeTabsColors,
        modifier: Modifier,
    ) {}
}

object NoOpEditModeLayoutTab : EditModeLayoutTab {
    @Composable
    override fun Content(
        viewmodel: EditModeLayoutTabViewModel,
        brightness: @Composable () -> Unit,
        tilesGrid: @Composable () -> Unit,
        media: @Composable () -> Unit,
        modifier: Modifier,
    ) {}

    @Composable
    override fun DragShadow(
        viewmodel: EditModeLayoutTabViewModel,
        brightness: @Composable () -> Unit,
        tilesGrid: @Composable () -> Unit,
        media: @Composable () -> Unit,
        modifier: Modifier,
    ) {}
}

class FakeEditModeLayoutTabViewModel : EditModeLayoutTabViewModel {
    override val components: SnapshotStateList<QsShadeComponent> =
        mutableStateListOf(BRIGHTNESS, TILES_GRID, MEDIA)

    override var dragState: DragState? by mutableStateOf(null)

    override fun setComponents(components: List<QsShadeComponent>) {
        this.components.clear()
        this.components.addAll(components)
    }

    override fun onHover(source: QsShadeComponent, target: QsShadeComponent?) {}

    override fun onDragStart(component: QsShadeComponent, offset: Int) {
        dragState = DragState(component, offset)
    }

    override fun onDrag(dragAmount: Int, idleOffset: Int) {}

    override fun onDragEnd() {
        dragState = null
    }
}

val Kosmos.infiniteGridLayout by
    Kosmos.Fixture {
        InfiniteGridLayout(
            detailsViewModel,
            iconTilesViewModel,
            infiniteGridViewModelFactory,
            textFeedbackContentViewModelFactory,
            tileHapticsViewModelFactory,
            NoOpEditModeTabs,
            NoOpEditModeLayoutTab,
            FakeEditModeLayoutTabViewModel(),
            qsPreferencesInteractor,
        )
    }

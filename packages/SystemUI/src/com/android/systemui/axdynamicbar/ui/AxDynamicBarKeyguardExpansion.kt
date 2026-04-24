/*
 * Copyright 2025-2026 AxionOS
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

package com.android.systemui.axdynamicbar.ui

import com.android.systemui.axdynamicbar.domain.AxDynamicBarInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@SysUISingleton
class AxDynamicBarKeyguardExpansion
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val interactor: AxDynamicBarInteractor,
) {
    private val _intent = MutableStateFlow(false)
    @Volatile
    private var collapseOnNullJob: Job? = null

    private val hasChip =
        interactor.uiState
            .map { it.shouldShow && it.topEvent != null }
            .distinctUntilChanged()

    private val canShow: StateFlow<Boolean> =
        combine(
            combine(
                interactor.isOnKeyguard,
                interactor.isDozing,
                interactor.dozeAmount.map { it > 0f }.distinctUntilChanged(),
                interactor.qsExpansion.map { it > 0f }.distinctUntilChanged(),
                interactor.isPanelExpanded,
            ) { onKg, dozing, dozeAmt, qs, panel ->
                onKg && !dozing && !dozeAmt && !qs && !panel
            },
            interactor.isBouncerShowing,
            hasChip,
        ) { canShowBase, bouncer, chip -> canShowBase && !bouncer && chip }
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Eagerly, false)

    val isExpanded: StateFlow<Boolean> =
        combine(_intent, canShow) { intent, show -> intent && show }
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Lazily, false)

    private val _collapseSettled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val collapseSettled: SharedFlow<Unit> = _collapseSettled.asSharedFlow()

    fun notifyCollapseSettled() {
        _collapseSettled.tryEmit(Unit)
    }

    private fun clearIntent() {
        _intent.value = false
    }

    init {
        interactor.isOnKeyguard
            .onEach { if (!it) clearIntent() }
            .launchIn(applicationScope)

        interactor.isPanelExpanded
            .onEach { if (it) clearIntent() }
            .launchIn(applicationScope)

        interactor.qsExpansion
            .map { it > 0f }
            .distinctUntilChanged()
            .onEach { if (it) clearIntent() }
            .launchIn(applicationScope)

        interactor.isBouncerShowing
            .onEach { if (it) clearIntent() }
            .launchIn(applicationScope)

        combine(
            interactor.legacyShadeExpansion,
            interactor.isOnKeyguard
        ) { expansion, onKg -> onKg && expansion < 0.95f }
            .onEach { dismissing -> if (dismissing) clearIntent() }
            .launchIn(applicationScope)

        interactor.isDozing
            .drop(1)
            .onEach { clearIntent() }
            .launchIn(applicationScope)

        interactor.dozeAmount
            .map { it > 0f }
            .distinctUntilChanged()
            .onEach { if (it) clearIntent() }
            .launchIn(applicationScope)

        interactor.uiState
            .onEach { state ->
                if (!state.shouldShow || state.topEvent == null) {
                    collapseOnNullJob?.cancel()
                    collapseOnNullJob = applicationScope.launch {
                        delay(200)
                        val s = interactor.uiState.value
                        if (!s.shouldShow || s.topEvent == null) {
                            clearIntent()
                        }
                    }
                } else {
                    collapseOnNullJob?.cancel()
                    collapseOnNullJob = null
                }
            }
            .launchIn(applicationScope)
    }

    fun expand() {
        if (interactor.uiState.value.topEvent == null) return
        _intent.value = true
    }

    fun collapse() {
        _intent.value = false
    }

    fun toggle() {
        if (_intent.value) collapse() else expand()
    }
}

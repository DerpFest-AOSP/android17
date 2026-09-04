/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.qs.panels.data.repository.QSPaginatedRowsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class QSPaginatedRowsInteractor
@Inject
constructor(@Application private val scope: CoroutineScope, repo: QSPaginatedRowsRepository) {
    val rows: StateFlow<Int> =
        repo.rows.stateIn(scope, SharingStarted.WhileSubscribed(), repo.defaultRows)
}

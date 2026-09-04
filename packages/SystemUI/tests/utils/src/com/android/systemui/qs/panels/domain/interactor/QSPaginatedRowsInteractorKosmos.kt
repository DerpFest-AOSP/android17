/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.domain.interactor

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.qs.panels.data.repository.qsPaginatedRowsRepository

val Kosmos.qsPaginatedRowsInteractor by
    Kosmos.Fixture {
        QSPaginatedRowsInteractor(applicationCoroutineScope, qsPaginatedRowsRepository)
    }

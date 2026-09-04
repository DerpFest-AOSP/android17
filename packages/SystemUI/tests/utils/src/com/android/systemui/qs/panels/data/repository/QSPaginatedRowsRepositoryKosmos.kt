/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.data.repository

import android.content.applicationContext
import android.content.res.mainResources
import com.android.systemui.common.ui.data.repository.configurationRepository
import com.android.systemui.kosmos.Kosmos

val Kosmos.qsPaginatedRowsRepository by
    Kosmos.Fixture {
        QSPaginatedRowsRepository(applicationContext, mainResources, configurationRepository)
    }

/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.volume.panel.component.appvolume.domain.interactor

import com.android.systemui.kosmos.Kosmos

val Kosmos.appVolumePanelGlobalStateInteractor: AppVolumePanelGlobalStateInteractor by
    Kosmos.Fixture { AppVolumePanelGlobalStateInteractor() }

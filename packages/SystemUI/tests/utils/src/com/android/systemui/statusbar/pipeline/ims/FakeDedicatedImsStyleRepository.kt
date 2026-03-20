/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.ims

import com.android.systemui.statusbar.pipeline.ims.data.repository.DedicatedImsStyleRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** Test double: integrated style by default. */
class FakeDedicatedImsStyleRepository(
    override val isDedicatedImsIconStyle: MutableStateFlow<Boolean> = MutableStateFlow(false)
) : DedicatedImsStyleRepository

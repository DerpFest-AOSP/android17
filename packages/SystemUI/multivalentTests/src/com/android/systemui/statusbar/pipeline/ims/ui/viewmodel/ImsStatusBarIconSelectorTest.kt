/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.ims.ui.viewmodel

import android.telephony.SubscriptionManager.INVALID_SIM_SLOT_INDEX
import android.telephony.SubscriptionManager.PROFILE_CLASS_UNSET
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ImsStatusBarIconSelectorTest : SysuiTestCase() {

    private val iconSet =
        ImsIconSet(
            single = SINGLE,
            sim1 = SIM1,
            sim2 = SIM2,
            dual = DUAL,
        )

    @Test
    fun selectIcon_noActiveSubs_returnsNull() {
        val result =
            ImsStatusBarIconSelector.selectIcon(
                iconSet = iconSet,
                subscriptions = listOf(sub(id = 1, slot = 0)),
                activeSubIds = emptySet(),
            )

        assertThat(result).isNull()
    }

    @Test
    fun selectIcon_singleSubscription_returnsSingle() {
        val result =
            ImsStatusBarIconSelector.selectIcon(
                iconSet = iconSet,
                subscriptions = listOf(sub(id = 1, slot = 0)),
                activeSubIds = setOf(1),
            )

        assertThat(result).isEqualTo(SINGLE)
    }

    @Test
    fun selectIcon_dualSim_onlySlot1Active_returnsSim1() {
        val result =
            ImsStatusBarIconSelector.selectIcon(
                iconSet = iconSet,
                subscriptions =
                    listOf(
                        sub(id = 1, slot = 0),
                        sub(id = 2, slot = 1),
                    ),
                activeSubIds = setOf(1),
            )

        assertThat(result).isEqualTo(SIM1)
    }

    @Test
    fun selectIcon_dualSim_onlySlot2Active_returnsSim2() {
        val result =
            ImsStatusBarIconSelector.selectIcon(
                iconSet = iconSet,
                subscriptions =
                    listOf(
                        sub(id = 1, slot = 0),
                        sub(id = 2, slot = 1),
                    ),
                activeSubIds = setOf(2),
            )

        assertThat(result).isEqualTo(SIM2)
    }

    @Test
    fun selectIcon_dualSim_bothSlotsActive_returnsDual() {
        val result =
            ImsStatusBarIconSelector.selectIcon(
                iconSet = iconSet,
                subscriptions =
                    listOf(
                        sub(id = 1, slot = 0),
                        sub(id = 2, slot = 1),
                    ),
                activeSubIds = setOf(1, 2),
            )

        assertThat(result).isEqualTo(DUAL)
    }

    @Test
    fun selectIcon_dualSim_activeWithUnknownSlot_fallsBackToSingle() {
        val result =
            ImsStatusBarIconSelector.selectIcon(
                iconSet = iconSet,
                subscriptions =
                    listOf(
                        sub(id = 1, slot = INVALID_SIM_SLOT_INDEX),
                        sub(id = 2, slot = 1),
                    ),
                activeSubIds = setOf(1),
            )

        assertThat(result).isEqualTo(SINGLE)
    }

    private fun sub(id: Int, slot: Int): SubscriptionModel =
        SubscriptionModel(
            subscriptionId = id,
            carrierName = "Carrier $id",
            simSlotIndex = slot,
            profileClass = PROFILE_CLASS_UNSET,
        )

    companion object {
        private const val SINGLE = 10
        private const val SIM1 = 11
        private const val SIM2 = 12
        private const val DUAL = 13
    }
}

/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.ims.data.model

import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities
import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM
import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN
import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_LTE
import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_NONE
import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_NR
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ImsStateModelDedicatedAvailabilityTest : SysuiTestCase() {

    @Test
    fun dedicatedImsSlotAvailability_notRegistered_allFalse() {
        val state =
            ImsStateModel(
                registered = false,
                capabilities = voiceCapabilities(),
                registrationTech = REGISTRATION_TECH_LTE,
            )

        assertThat(state.dedicatedImsSlotAvailability())
            .isEqualTo(DedicatedImsSlotAvailability(false, false, false))
    }

    @Test
    fun dedicatedImsSlotAvailability_noVoiceCapability_allFalse() {
        val state =
            ImsStateModel(
                registered = true,
                capabilities = MmTelCapabilities(),
                registrationTech = REGISTRATION_TECH_LTE,
            )

        assertThat(state.dedicatedImsSlotAvailability())
            .isEqualTo(DedicatedImsSlotAvailability(false, false, false))
    }

    @Test
    fun dedicatedImsSlotAvailability_lte_onlyVoLte() {
        val state =
            ImsStateModel(
                registered = true,
                capabilities = voiceCapabilities(),
                registrationTech = REGISTRATION_TECH_LTE,
            )

        assertThat(state.dedicatedImsSlotAvailability())
            .isEqualTo(DedicatedImsSlotAvailability(voLte = true, voNr = false, voWifi = false))
    }

    @Test
    fun dedicatedImsSlotAvailability_nr_onlyVoNr_suppressesVoLte() {
        val state =
            ImsStateModel(
                registered = true,
                capabilities = voiceCapabilities(),
                registrationTech = REGISTRATION_TECH_NR,
            )

        assertThat(state.dedicatedImsSlotAvailability())
            .isEqualTo(DedicatedImsSlotAvailability(voLte = false, voNr = true, voWifi = false))
    }

    @Test
    fun dedicatedImsSlotAvailability_iwlan_onlyVoWifi_suppressesCellular() {
        val state =
            ImsStateModel(
                registered = true,
                capabilities = voiceCapabilities(),
                registrationTech = REGISTRATION_TECH_IWLAN,
            )

        assertThat(state.dedicatedImsSlotAvailability())
            .isEqualTo(DedicatedImsSlotAvailability(voLte = false, voNr = false, voWifi = true))
    }

    @Test
    fun dedicatedImsSlotAvailability_crossSim_onlyVoWifi_suppressesCellular() {
        val state =
            ImsStateModel(
                registered = true,
                capabilities = voiceCapabilities(),
                registrationTech = REGISTRATION_TECH_CROSS_SIM,
            )

        assertThat(state.dedicatedImsSlotAvailability())
            .isEqualTo(DedicatedImsSlotAvailability(voLte = false, voNr = false, voWifi = true))
    }

    @Test
    fun dedicatedImsSlotAvailability_none_allFalse() {
        val state =
            ImsStateModel(
                registered = true,
                capabilities = voiceCapabilities(),
                registrationTech = REGISTRATION_TECH_NONE,
            )

        assertThat(state.dedicatedImsSlotAvailability())
            .isEqualTo(DedicatedImsSlotAvailability(false, false, false))
    }

    private fun voiceCapabilities(): MmTelCapabilities =
        MmTelCapabilities(MmTelCapabilities.CAPABILITY_TYPE_VOICE)
}

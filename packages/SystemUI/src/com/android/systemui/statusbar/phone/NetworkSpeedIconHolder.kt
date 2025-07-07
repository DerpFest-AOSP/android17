/*
 * SPDX-FileCopyrightText: AxionOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.statusbar.phone

import com.android.systemui.statusbar.policy.networkspeed.NetworkSpeedIconState

class NetworkSpeedIconHolder : StatusBarIconHolder() {

    companion object {
        fun fromNetworkIconState(state: NetworkSpeedIconState): NetworkSpeedIconHolder {
            val holder = NetworkSpeedIconHolder()
            holder.mNetworkSpeedIconState = state
            holder.type = StatusBarIconHolder.TYPE_NETWORK_SPEED
            return holder
        }
    }

    private var mNetworkSpeedIconState: NetworkSpeedIconState? = null

    fun setNetworkSpeedIconState(state: NetworkSpeedIconState) {
        mNetworkSpeedIconState = state
    }

    fun getNetworkSpeedIconState(): NetworkSpeedIconState? {
        return mNetworkSpeedIconState
    }

    override var isVisible: Boolean
        get() = mNetworkSpeedIconState?.isVisible() ?: false
        set(value) {
            if (mNetworkSpeedIconState == null || isVisible == value) return
            mNetworkSpeedIconState?.setVisible(value)
        }

    override fun toString(): String {
        return mNetworkSpeedIconState?.toString() ?: "null"
    }
}

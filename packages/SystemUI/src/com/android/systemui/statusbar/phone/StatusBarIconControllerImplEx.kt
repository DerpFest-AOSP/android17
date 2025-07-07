/*
 * SPDX-FileCopyrightText: AxionOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.statusbar.phone

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.res.R
import com.android.systemui.statusbar.StatusIconDisplayable
import com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl
import com.android.systemui.statusbar.phone.ui.StatusBarIconList
import com.android.systemui.statusbar.policy.NetworkSpeedController
import com.android.systemui.statusbar.policy.networkspeed.NetworkSpeedIconState
import com.android.systemui.statusbar.policy.networkspeed.NetworkSpeedView
import dagger.Lazy
import javax.inject.Inject

@SysUISingleton
class StatusBarIconControllerImplEx @Inject constructor(
    private val context: Context,
    private val iconController: Lazy<StatusBarIconControllerImpl>,
    private val iconList: StatusBarIconList,
    private val networkSpeedController: Lazy<NetworkSpeedController>
) {

    fun setNetworkSpeedIcon(slot: String, state: NetworkSpeedIconState?) {
        if (state == null || !state.isVisible()) {
            iconController.get().removeIcon(slot, 0)
            return
        }

        val existingHolder = iconList.getIconHolder(slot, 0)

        if (existingHolder == null) {
            val newHolder = NetworkSpeedIconHolder.fromNetworkIconState(state)
            iconController.get().setIcon(slot, newHolder)
        } else if (existingHolder is NetworkSpeedIconHolder) {
            existingHolder.setNetworkSpeedIconState(state)
            iconController.get().handleSet(slot, existingHolder)
        }
    }

    fun addHolder(
        index: Int,
        slot: String,
        rootGroup: ViewGroup,
        holder: StatusBarIconHolder,
        blocked: Boolean
    ): StatusIconDisplayable? {
        return if (holder.type == StatusBarIconHolder.TYPE_NETWORK_SPEED &&
            holder is NetworkSpeedIconHolder
        ) {
            addNetworkSpeedIcon(index, slot, holder.getNetworkSpeedIconState(), rootGroup, blocked)
        } else {
            null
        }
    }

    private fun addNetworkSpeedIcon(
        index: Int,
        slot: String,
        state: NetworkSpeedIconState?,
        rootGroup: ViewGroup,
        blocked: Boolean
    ): NetworkSpeedView {
        val view = createNetworkSpeedView(slot, blocked)
        view.applyNetworkState(state)
        rootGroup.addView(view, index, createLayoutParams())
        return view
    }

    private fun updateNetworkSpeedIcon(
        viewIndex: Int,
        state: NetworkSpeedIconState?,
        rootGroup: ViewGroup
    ) {
        val view = rootGroup.getChildAt(viewIndex) as? NetworkSpeedView ?: return
        view.applyNetworkState(state)
    }

    private fun createNetworkSpeedView(slot: String, blocked: Boolean): NetworkSpeedView {
        return NetworkSpeedView.fromContext(context, slot, blocked, networkSpeedController.get())
    }

    private fun createLayoutParams(): LinearLayout.LayoutParams {
        val width = context.resources.getDimensionPixelSize(R.dimen.network_speed_view_width)
        return LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.MATCH_PARENT)
    }

    fun onSetIconHolder(viewIndex: Int, holder: StatusBarIconHolder, rootGroup: ViewGroup) {
        if (holder.type == StatusBarIconHolder.TYPE_NETWORK_SPEED &&
            holder is NetworkSpeedIconHolder
        ) {
            updateNetworkSpeedIcon(viewIndex, holder.getNetworkSpeedIconState(), rootGroup)
        }
    }
}

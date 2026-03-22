/*
 * SPDX-FileCopyrightText: RisingOS Revived Android Project
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.os.UserHandle
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.clocks.ClockStyle
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import com.android.systemui.shared.R as sharedR
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject

/**
 * When a custom [ClockStyle] is enabled, inflates [R.layout.keyguard_clock_style] into the
 * keyguard root and positions it. Barrier bottom is updated to include the clock so notifications
 * sit below the full status/clock area (Smartspace-only weather; no separate weather section).
 */
class KeyguardClockStyleSection
@Inject
constructor(
    private val context: Context,
    private val secureSettings: SecureSettings,
) : KeyguardSection() {

    private var clockStyleView: ClockStyle? = null
    private var hostLayout: ConstraintLayout? = null
    private var isCustomClockEnabled: Boolean = false

    override fun addViews(constraintLayout: ConstraintLayout) {
        hostLayout = constraintLayout

        val clockStyle =
            secureSettings.getIntForUser(
                ClockStyle.CLOCK_STYLE_KEY,
                0,
                UserHandle.USER_CURRENT,
            )
        isCustomClockEnabled = clockStyle != 0

        if (!isCustomClockEnabled) return

        constraintLayout.findViewById<View?>(R.id.clock_ls)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
        }

        val inflater = android.view.LayoutInflater.from(context)
        clockStyleView = inflater.inflate(R.layout.keyguard_clock_style, null) as ClockStyle
        clockStyleView?.apply {
            id = R.id.clock_ls
            layoutParams =
                ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                )
            visibility = View.VISIBLE
        }

        clockStyleView?.let { constraintLayout.addView(it) }
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        clockStyleView?.let { clockView ->
            clockView.onTimeChanged()
            clockView.requestLayout()
        }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!isCustomClockEnabled) return

        constraintSet.apply {
            connect(R.id.clock_ls, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(R.id.clock_ls, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

            val topMargin =
                (context.resources.getDimensionPixelSize(R.dimen.status_bar_height) * 1.25f).toInt()
            connect(
                R.id.clock_ls,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
                topMargin,
            )

            constrainHeight(R.id.clock_ls, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.clock_ls, ConstraintSet.MATCH_CONSTRAINT)
            setMargin(R.id.clock_ls, ConstraintSet.START, 0)
            setMargin(R.id.clock_ls, ConstraintSet.END, 0)
            setElevation(R.id.clock_ls, 1f)

            createUnifiedBarrierBottom(constraintSet)
        }
    }

    private fun createUnifiedBarrierBottom(constraintSet: ConstraintSet) {
        val host = hostLayout ?: return
        val refs = mutableListOf<Int>()
        fun addIfPresent(id: Int) {
            if (host.findViewById<View>(id) != null) {
                refs.add(id)
            }
        }
        addIfPresent(R.id.clock_ls)
        addIfPresent(R.id.keyguard_slice_view)
        addIfPresent(sharedR.id.bc_smartspace_view)
        addIfPresent(sharedR.id.date_smartspace_view)
        if (refs.isEmpty()) return
        constraintSet.createBarrier(
            R.id.smart_space_barrier_bottom,
            Barrier.BOTTOM,
            0,
            *refs.toIntArray(),
        )
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        clockStyleView?.let { clockView ->
            (clockView.parent as? ViewGroup)?.removeView(clockView)
        }
        clockStyleView = null
        hostLayout = null
    }
}

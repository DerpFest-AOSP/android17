/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.phone.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A FrameLayout that hides its single child and takes zero width when there isn't enough
 * horizontal space to show the child at its natural size. Used so the combined notification
 * counter is fully hidden instead of squashed when the ongoing progress chip is active.
 * Also hides entirely when the counter is force-hidden (e.g. on keyguard) to avoid overlap.
 */
public class HideWhenSquashedFrameLayout extends FrameLayout {

    /** When true, whole wrapper is GONE (e.g. keyguard); counter sets this via setForceHidden. */
    private boolean mForceHidden = false;

    public HideWhenSquashedFrameLayout(Context context) {
        super(context);
    }

    public HideWhenSquashedFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HideWhenSquashedFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** Called by CombinedNotificationCounter when it is force-hidden (keyguard/heads-up). */
    public void setForceHidden(boolean forceHidden) {
        if (mForceHidden != forceHidden) {
            mForceHidden = forceHidden;
            setVisibility(forceHidden ? GONE : VISIBLE);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mForceHidden) {
            setMeasuredDimension(0, 0);
            return;
        }
        if (getChildCount() != 1) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        View child = getChildAt(0);

        // When child is GONE (e.g. combined counter disabled or 0 notifications), take zero width to avoid gap
        if (child.getVisibility() == GONE) {
            setMeasuredDimension(0, resolveSize(0, heightMeasureSpec));
            return;
        }

        // Measure child with unrestricted width to get its desired width
        int unrestrictedWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        child.measure(unrestrictedWidthSpec, heightMeasureSpec);
        int desiredWidth = child.getMeasuredWidth();

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        if (widthMode == MeasureSpec.AT_MOST && widthSize < desiredWidth) {
            // Not enough space: hide child and take zero width
            child.setVisibility(GONE);
            setMeasuredDimension(0, resolveSize(child.getMeasuredHeight(), heightMeasureSpec));
        } else {
            // Enough space: measure child (leave visibility as-is so counter stays GONE at 0 count)
            child.measure(widthMeasureSpec, heightMeasureSpec);
            setMeasuredDimension(
                    child.getMeasuredWidth(),
                    resolveSize(child.getMeasuredHeight(), heightMeasureSpec));
        }
    }
}

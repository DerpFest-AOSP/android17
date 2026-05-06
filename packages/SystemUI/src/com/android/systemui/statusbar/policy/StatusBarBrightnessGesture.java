/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.policy;

import android.content.ContentResolver;
import android.os.UserHandle;
import android.provider.Settings;

/**
 * Convenience accessors for the slide-status-bar-to-adjust-brightness gesture.
 *
 * <p>The gesture itself is implemented by {@code CentralSurfacesImpl}; this
 * helper exists so every caller that decides whether to invoke or suppress
 * the gesture reads the same setting keys with the same semantics, instead
 * of duplicating {@link Settings.System#getIntForUser} calls inline.
 *
 * <p>Reads are cheap: {@code Settings.System} caches values in-process, so
 * calling these per-touch is fine.
 */
public final class StatusBarBrightnessGesture {

    /**
     * Sub-toggle gating the gesture on the keyguard. Default off — secure
     * default, since otherwise anyone could dim or brighten a locked screen.
     */
    public static final String SETTING_LOCKSCREEN =
            "status_bar_brightness_control_lockscreen";

    private StatusBarBrightnessGesture() {}

    /** Whether the user has enabled the brightness gesture at all. */
    public static boolean isEnabled(ContentResolver resolver) {
        return Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BRIGHTNESS_CONTROL,
                0,
                UserHandle.USER_CURRENT) != 0;
    }

    /** Whether the gesture is also allowed while the device is on the keyguard. */
    public static boolean isLockscreenAllowed(ContentResolver resolver) {
        return Settings.System.getIntForUser(resolver,
                SETTING_LOCKSCREEN,
                0,
                UserHandle.USER_CURRENT) != 0;
    }
}

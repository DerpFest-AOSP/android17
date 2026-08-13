/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.graphics.Color;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

/** Status bar icon tint: system default, accent, or user-chosen color. */
public final class StatusBarIconTintHelper {
    private StatusBarIconTintHelper() {}

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_ACCENT = 1;
    public static final int MODE_CUSTOM = 2;

    private static final int MODE_UNSET = -1;
    /** Sentinel for Settings.System integer color when unset (ColorPicker uses int ARGB). */
    private static final int CUSTOM_COLOR_UNSET = Integer.MIN_VALUE;

    /**
     * Returns {@link #MODE_SYSTEM}, {@link #MODE_ACCENT}, or {@link #MODE_CUSTOM}. If the new
     * setting was never written, falls back to {@link Settings.System#TINT_STATUSBAR_ICONS_WITH_ACCENT}.
     */
    public static int getMode(Context context) {
        int mode = Settings.System.getIntForUser(
                context.getContentResolver(),
                Settings.System.STATUSBAR_ICON_TINT_MODE,
                MODE_UNSET,
                UserHandle.USER_CURRENT);
        if (mode == MODE_UNSET) {
            boolean legacyAccent = Settings.System.getIntForUser(
                    context.getContentResolver(),
                    Settings.System.TINT_STATUSBAR_ICONS_WITH_ACCENT,
                    0,
                    UserHandle.USER_CURRENT) == 1;
            return legacyAccent ? MODE_ACCENT : MODE_SYSTEM;
        }
        return mode;
    }

    /**
     * ARGB color used when mode is {@link #MODE_CUSTOM}. Supports hex strings (e.g. {@code #FFFFFF})
     * and integer ARGB as stored by {@code ColorPickerSystemPreference}.
     */
    public static int getCustomColorArgb(Context context) {
        String raw = Settings.System.getStringForUser(
                context.getContentResolver(),
                Settings.System.STATUSBAR_ICON_TINT_CUSTOM_COLOR,
                UserHandle.USER_CURRENT);
        if (!TextUtils.isEmpty(raw) && raw.startsWith("#")) {
            try {
                return Color.parseColor(raw);
            } catch (IllegalArgumentException e) {
                return Color.WHITE;
            }
        }
        int asInt = Settings.System.getIntForUser(
                context.getContentResolver(),
                Settings.System.STATUSBAR_ICON_TINT_CUSTOM_COLOR,
                CUSTOM_COLOR_UNSET,
                UserHandle.USER_CURRENT);
        if (asInt != CUSTOM_COLOR_UNSET) {
            return asInt;
        }
        return Color.WHITE;
    }
}

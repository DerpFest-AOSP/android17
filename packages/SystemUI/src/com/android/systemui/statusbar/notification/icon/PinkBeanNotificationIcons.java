/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.notification.icon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Optional bundled launcher-style PNGs (from the Pink Bean theme pack) for status bar
 * notification icons, used when {@link android.provider.Settings.System#STATUSBAR_COLORED_ICONS}
 * and {@link android.provider.Settings.System#STATUSBAR_PINKBEAN_NOTIFICATION_ICONS} are enabled.
 * Packages without a bundled asset fall back to {@code PackageManager#getApplicationIcon}.
 */
public final class PinkBeanNotificationIcons {
    private static final String TAG = "PinkBeanNotifIcons";
    private static final String ASSET_DIR = "pinkbean_icons";

    private PinkBeanNotificationIcons() {}

    /**
     * @return A drawable for {@code packageName} if a matching PNG exists under {@code assets/},
     *         or null to use the normal colored-icon path.
     */
    public static @Nullable Drawable load(Context sysuiContext, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        final String path = ASSET_DIR + "/" + packageName + ".png";
        try (InputStream is = sysuiContext.getAssets().open(path)) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp == null) {
                return null;
            }
            // Match colored-icons path: launcher/adaptive icons read as circular; raw PNGs are square.
            RoundedBitmapDrawable drawable =
                    RoundedBitmapDrawableFactory.create(sysuiContext.getResources(), bmp);
            drawable.setCircular(true);
            drawable.setAntiAlias(true);
            return drawable;
        } catch (IOException e) {
            return null;
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "OOM loading " + path, e);
            return null;
        }
    }
}

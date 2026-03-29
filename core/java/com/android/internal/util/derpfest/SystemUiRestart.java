/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.util.derpfest;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.internal.statusbar.IStatusBarService;

/**
 * Restarts SystemUI via {@link IStatusBarService#restartSystemUI()} (in-process self-kill),
 * not {@link android.app.ActivityManager#forceStopPackage}, which does not stop persistent apps.
 *
 * @hide
 */
public final class SystemUiRestart {
    private SystemUiRestart() {}

    public static void restartSystemUI() {
        IStatusBarService bar = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        if (bar != null) {
            try {
                bar.restartSystemUI();
            } catch (RemoteException ignored) {
            }
        }
    }
}

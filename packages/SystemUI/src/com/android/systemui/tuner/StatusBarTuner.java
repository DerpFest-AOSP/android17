/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.tuner;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import com.android.systemui.res.R;

public class StatusBarTuner extends SettingsBasePreferenceFragment {

    // Same value as Settings.System.STATUS_BAR_IMS_INDICATOR_STYLE in frameworks/base Settings.java.
    private static final String KEY_STATUS_BAR_IMS_INDICATOR_STYLE = "status_bar_ims_indicator_style";

    private static final int IMS_STYLE_INTEGRATED = 0;
    private static final int IMS_STYLE_DEDICATED = 1;

    private MetricsLogger mMetricsLogger;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.status_bar_prefs, rootKey);
        if (!isVoiceCapable(requireContext())) {
            removeMobilePreferences();
        } else {
            setupImsIndicatorCategory();
        }
    }

    private void setupImsIndicatorCategory() {
        ListPreference stylePref = findPreference("status_bar_ims_indicator_style");
        if (stylePref == null) {
            return;
        }
        int current =
                Settings.System.getIntForUser(
                        requireContext().getContentResolver(),
                        KEY_STATUS_BAR_IMS_INDICATOR_STYLE,
                        IMS_STYLE_INTEGRATED,
                        UserHandle.USER_CURRENT);
        stylePref.setValue(String.valueOf(current));
        stylePref.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    int v = Integer.parseInt((String) newValue);
                    Settings.System.putIntForUser(
                            requireContext().getContentResolver(),
                            KEY_STATUS_BAR_IMS_INDICATOR_STYLE,
                            v,
                            UserHandle.USER_CURRENT);
                    updateImsToggleVisibility();
                    return true;
                });
        updateImsToggleVisibility();
    }

    private void updateImsToggleVisibility() {
        boolean dedicated =
                Settings.System.getIntForUser(
                                requireContext().getContentResolver(),
                                KEY_STATUS_BAR_IMS_INDICATOR_STYLE,
                                IMS_STYLE_INTEGRATED,
                                UserHandle.USER_CURRENT)
                        == IMS_STYLE_DEDICATED;
        Preference hdCalling = findPreference("hd_calling");
        if (hdCalling != null) {
            hdCalling.setVisible(!dedicated);
        }
        Preference volte = findPreference("volte");
        if (volte != null) {
            volte.setVisible(dedicated);
        }
        Preference vonr = findPreference("vonr");
        if (vonr != null) {
            vonr.setVisible(dedicated);
        }
        Preference vowifi = findPreference("vowifi");
        if (vowifi != null) {
            vowifi.setVisible(true);
            // Same slot ("vowifi"); match HD vs VoLTE/VoNR tuner visuals: vector with hd_calling when
            // integrated, stat-bar tuner bitmap with volte/vonr when dedicated.
            vowifi.setTitle(
                    getString(
                            dedicated
                                    ? R.string.status_bar_vowifi_icon_title
                                    : R.string.status_bar_vowifi));
            applyThemedPreferenceIcon(
                    vowifi,
                    dedicated
                            ? R.drawable.ic_statusbar_vowifi_tuner
                            : R.drawable.ic_statusbar_vowifi);
        }
    }

    /** Same tint as {@link StatusBarSwitch#setupTheme()} so icons stay visible after style changes. */
    private void applyThemedPreferenceIcon(Preference preference, int drawableResId) {
        Drawable icon = requireContext().getDrawable(drawableResId);
        if (icon == null) {
            return;
        }
        TypedArray a =
                requireContext()
                        .obtainStyledAttributes(new int[] {android.R.attr.textColorPrimary});
        int color = a.getColor(0, 0);
        a.recycle();
        Drawable wrapped = DrawableCompat.wrap(icon.mutate());
        DrawableCompat.setTint(wrapped, color);
        preference.setIcon(wrapped);
    }

    public static boolean isVoiceCapable(Context context) {
        TelephonyManager telephony =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephony != null && telephony.isVoiceCapable();
    }

    private void removeMobilePreferences() {
        String[] mobileKeys = new String[] {
                "mobile",
                "system:data_disabled_icon",
                "call_strength",
                "roaming",
                "system:show_fourg_icon",
                "ims_status_bar_category",
        };

        PreferenceScreen screen = getPreferenceScreen();
        for (String key : mobileKeys) {
            Preference pref = findPreference(key);
            if (pref != null) {
                screen.removePreference(pref);
            }
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMetricsLogger = new MetricsLogger();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMetricsLogger.visibility(MetricsEvent.TUNER, true);
        if (isVoiceCapable(requireContext())) {
            ListPreference stylePref = findPreference("status_bar_ims_indicator_style");
            if (stylePref != null) {
                int current =
                        Settings.System.getIntForUser(
                                requireContext().getContentResolver(),
                                KEY_STATUS_BAR_IMS_INDICATOR_STYLE,
                                IMS_STYLE_INTEGRATED,
                                UserHandle.USER_CURRENT);
                stylePref.setValue(String.valueOf(current));
            }
            updateImsToggleVisibility();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mMetricsLogger.visibility(MetricsEvent.TUNER, false);
    }
}

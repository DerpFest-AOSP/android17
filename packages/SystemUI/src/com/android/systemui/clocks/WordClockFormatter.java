/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.clocks;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.res.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Typographic word clock strings for {@link R.layout#keyguard_clock_word}, following the same
 * hour/minute phrasing and indexing as legacy AOSP {@code TypographicClock} (Bug 122301289):
 * {@link Calendar#HOUR} (0–11) maps to {@link R.array#word_clock_hours}; minutes use
 * {@link R.array#word_clock_minutes} (O\u2019Clock, O\u2019One, …).
 */
public final class WordClockFormatter {

    private WordClockFormatter() {}

    /** Hour line: Twelve / One / … / Eleven from {@link R.array#word_clock_hours}. */
    public static String typographicHourText(Context context, Calendar cal) {
        String[] hours = context.getResources().getStringArray(R.array.word_clock_hours);
        int idx = cal.get(Calendar.HOUR) % 12;
        if (idx < 0 || idx >= hours.length) {
            return "";
        }
        return hours[idx];
    }

    /** Minute line from {@link R.array#word_clock_minutes} (may contain line breaks). */
    public static String typographicMinuteText(Context context, Calendar cal) {
        String[] minutes = context.getResources().getStringArray(R.array.word_clock_minutes);
        int m = cal.get(Calendar.MINUTE);
        if (m < 0 || m >= minutes.length) {
            return "";
        }
        return minutes[m];
    }

    /**
     * Updates {@link R.id#word_clock} and {@link R.id#word_min} on {@link R.layout#keyguard_clock_word}.
     */
    public static void updateHourMinuteWordViews(Context context, View root) {
        if (root == null) {
            return;
        }
        TextView hourView = root.findViewById(R.id.word_clock);
        TextView minuteView = root.findViewById(R.id.word_min);
        if (hourView == null || minuteView == null) {
            return;
        }

        Calendar cal = Calendar.getInstance();
        hourView.setText(typographicHourText(context, cal));
        minuteView.setText(typographicMinuteText(context, cal));

        try {
            java.text.DateFormat tf = DateFormat.getTimeFormat(context);
            String pattern =
                    tf instanceof SimpleDateFormat
                            ? ((SimpleDateFormat) tf).toLocalizedPattern()
                            : "HH:mm";
            root.setContentDescription(DateFormat.format(pattern, cal));
        } catch (RuntimeException ignored) {
            root.setContentDescription(null);
        }
    }
}

/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.systemui.globalactions;

import android.annotation.NonNull;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ListAdapter;

import androidx.constraintlayout.helper.widget.Flow;

import com.android.systemui.res.R;
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor;

/**
 * Creates a customized Dialog for displaying the Shut Down and Restart actions.
 */
public class GlobalActionsPowerDialog {

    /**
     * Create a dialog for displaying Shut Down and Restart actions.
     */
    public static Dialog create(
            @NonNull Context context,
            ListAdapter adapter,
            @NonNull WindowRootViewBlurInteractor blurInteractor,
            boolean translucentPowerMenu) {
        ViewGroup listView = (ViewGroup) LayoutInflater.from(context).inflate(
                R.layout.global_actions_power_dialog_flow, null);

        Flow flow = listView.findViewById(R.id.power_flow);

        for (int i = 0; i < adapter.getCount(); i++) {
            View action = adapter.getView(i, null, listView);
            action.setId(View.generateViewId());
            listView.addView(action);
            flow.addView(action);
        }

        Resources res = context.getResources();

        int nElementsWrap = res.getInteger(R.integer.power_menu_lite_max_columns);
        int nChildren = listView.getChildCount() - 1; // don't count flow element

        // Avoid having just one action on the last row if there are more than 2 columns because
        // it looks unbalanced. Instead, bring the column size down to balance better.
        if (nChildren == nElementsWrap + 1 && nElementsWrap > 2) {
            nElementsWrap -= 1;
        }
        flow.setMaxElementsWrap(nElementsWrap);

        Dialog dialog = new Dialog(context, R.style.Theme_SystemUI_Dialog_GlobalActionsLite);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(listView);

        final boolean blurSupported = blurInteractor.isBlurCurrentlySupported().getValue();
        final boolean useBlurBackground = blurSupported && translucentPowerMenu;

        Window window = dialog.getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);
        window.setTitle(""); // prevent Talkback from speaking first item name twice
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        if (useBlurBackground) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.getAttributes().setBlurBehindRadius(
                    res.getDimensionPixelSize(R.dimen.global_actions_blur_radius));
            window.setBackgroundDrawable(null);

            GradientDrawable background = new GradientDrawable();
            background.setColor(context.getColor(R.color.global_actions_grid_background_blur));
            background.setCornerRadius(res.getDimension(R.dimen.global_actions_corner_radius));
            listView.setBackground(background);

            window.setDimAmount(0.54f);
        } else {
            window.setBackgroundDrawable(res.getDrawable(
                    R.drawable.global_actions_lite_background, context.getTheme()));
            window.setDimAmount(0.88f);
        }

        return dialog;
    }
}

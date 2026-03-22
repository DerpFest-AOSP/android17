/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.weather

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView

/** Keyguard clock weather text driven by Smartspace weather data (see [SmartspaceWeatherClockController]). */
class WeatherTextView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    TextView(context, attrs, defStyle) {

    private val controller =
        SmartspaceWeatherClockController(
            context = context,
            weatherIcon = null,
            weatherTemp = this,
        )

    init {
        visibility = View.GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller.init()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        controller.removeObserver()
    }
}

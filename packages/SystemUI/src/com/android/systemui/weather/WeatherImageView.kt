/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.weather

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec
import android.widget.ImageView
import com.android.systemui.res.R

/** Keyguard clock weather icon driven by Smartspace weather data (see [SmartspaceWeatherClockController]). */
class WeatherImageView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    ImageView(context, attrs, defStyle) {

    private val maxSizePx: Int =
        context.resources.getDimensionPixelSize(R.dimen.weather_image_max_size)

    private val controller =
        SmartspaceWeatherClockController(
            context = context,
            weatherIcon = this,
            weatherTemp = null,
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = maxSizePx.coerceAtMost(MeasureSpec.getSize(widthMeasureSpec))
        val height = maxSizePx.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(width, height)
    }
}

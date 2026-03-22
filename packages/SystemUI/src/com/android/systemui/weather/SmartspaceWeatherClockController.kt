/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.weather

import android.app.smartspace.SmartspaceAction
import android.app.smartspace.SmartspaceTarget
import android.app.smartspace.SmartspaceUtils
import android.app.smartspace.uitemplatedata.BaseTemplateData
import android.content.Context
import android.os.Parcelable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.android.systemui.plugins.BcSmartspaceDataPlugin
import com.android.systemui.plugins.BcSmartspaceDataPlugin.SmartspaceTargetListener
import com.google.android.systemui.smartspace.BcSmartSpaceUtil
import com.google.android.systemui.smartspace.BcSmartspaceTemplateDataUtils
import com.google.android.systemui.smartspace.DoubleShadowIconDrawable
import com.google.android.systemui.smartspace.logging.BcSmartspaceCardLoggerUtil
import com.google.android.systemui.smartspace.utils.ContentDescriptionUtil

private const val WEATHER_CLOCK_TAG = "SmartspaceWeatherClock"

/**
 * Subscribes to the same Smartspace weather stream as [com.google.android.systemui.smartspace.WeatherSmartspaceView]
 * and updates optional icon / text views used in keyguard clock layouts.
 */
class SmartspaceWeatherClockController(
    private val context: Context,
    private val weatherIcon: ImageView?,
    private val weatherTemp: TextView?,
) : SmartspaceTargetListener {

    private var dataProvider: BcSmartspaceDataPlugin? = null
    private val iconSize: Int =
        context.resources.getDimensionPixelSize(com.android.systemui.res.R.dimen.enhanced_smartspace_icon_size)
    private val iconInset: Int =
        context.resources.getDimensionPixelSize(com.android.systemui.res.R.dimen.enhanced_smartspace_icon_inset)
    private val shadowIconDrawable: DoubleShadowIconDrawable =
        DoubleShadowIconDrawable(iconSize, iconInset, context)

    fun init() {
        dataProvider = WeatherSmartspacePluginAccessor.getPlugin()
        dataProvider?.registerListener(this)
    }

    fun removeObserver() {
        dataProvider?.unregisterListener(this)
        dataProvider = null
        clearViews()
    }

    private fun clearViews() {
        weatherIcon?.apply {
            setImageDrawable(null)
            setOnClickListener(null)
            visibility = View.GONE
        }
        weatherTemp?.apply {
            text = ""
            setCompoundDrawablesRelative(null, null, null, null)
            setOnClickListener(null)
            visibility = View.GONE
        }
    }

    override fun onSmartspaceTargetsUpdated(targets: List<out Parcelable>?) {
        val smartspaceTargets = targets.orEmpty().mapNotNull { it as? SmartspaceTarget }
        if (smartspaceTargets.size > 1) {
            return
        }
        if (smartspaceTargets.isEmpty()) {
            clearViews()
            return
        }
        val target = smartspaceTargets[0]
        if (target.featureType != SmartspaceTarget.FEATURE_WEATHER) {
            return
        }
        val hasValidTemplate = BcSmartspaceCardLoggerUtil.containsValidTemplateType(target.templateData)
        if (!hasValidTemplate && target.headerAction == null) {
            return
        }
        if (!hasValidTemplate) {
            applyHeaderAction(target, target.headerAction ?: return)
        } else if (target.templateData != null) {
            applyTemplateData(target, target.templateData!!)
        }
    }

    private fun applyHeaderAction(target: SmartspaceTarget, headerAction: SmartspaceAction) {
        weatherTemp?.apply {
            text = headerAction.title?.toString().orEmpty()
            setCompoundDrawablesRelative(null, null, null, null)
            visibility = View.VISIBLE
            ContentDescriptionUtil.setFormattedContentDescription(
                WEATHER_CLOCK_TAG,
                this,
                headerAction.title,
                headerAction.contentDescription,
            )
            BcSmartSpaceUtil.setOnClickListener(
                this,
                target,
                headerAction,
                dataProvider?.getEventNotifier(),
                WEATHER_CLOCK_TAG,
                null,
                0,
            )
        }
        val icon = headerAction.icon
        if (weatherIcon != null) {
            if (icon != null) {
                shadowIconDrawable.setIcon(
                    BcSmartSpaceUtil.getIconDrawableWithCustomSize(icon, context, iconSize)
                )
                weatherIcon.setImageDrawable(shadowIconDrawable)
                weatherIcon.visibility = View.VISIBLE
                if (headerAction.contentDescription != null) {
                    weatherIcon.contentDescription = headerAction.contentDescription
                }
                BcSmartSpaceUtil.setOnClickListener(
                    weatherIcon,
                    target,
                    headerAction,
                    dataProvider?.getEventNotifier(),
                    WEATHER_CLOCK_TAG,
                    null,
                    0,
                )
            } else {
                weatherIcon.setImageDrawable(null)
                weatherIcon.setOnClickListener(null)
                weatherIcon.visibility = View.GONE
            }
        }
    }

    private fun applyTemplateData(target: SmartspaceTarget, templateData: BaseTemplateData) {
        val subItemInfo = templateData.subtitleItem ?: return
        weatherTemp?.let { tv ->
            BcSmartspaceTemplateDataUtils.setText(tv, subItemInfo.text)
            tv.setCompoundDrawablesRelative(null, null, null, null)
            if (subItemInfo.tapAction != null) {
                BcSmartSpaceUtil.setOnClickListener(
                    tv,
                    target,
                    subItemInfo.tapAction,
                    dataProvider?.getEventNotifier(),
                    WEATHER_CLOCK_TAG,
                    null,
                    0,
                )
            } else {
                tv.setOnClickListener(null)
            }
            ContentDescriptionUtil.setFormattedContentDescription(
                WEATHER_CLOCK_TAG,
                tv,
                if (SmartspaceUtils.isEmpty(subItemInfo.text)) "" else subItemInfo.text.text,
                subItemInfo.icon?.contentDescription,
            )
        }
        if (weatherIcon != null) {
            val subIcon = subItemInfo.icon
            if (subIcon != null) {
                shadowIconDrawable.setIcon(
                    BcSmartSpaceUtil.getIconDrawableWithCustomSize(subIcon.icon, context, iconSize)
                )
                weatherIcon.setImageDrawable(shadowIconDrawable)
                weatherIcon.visibility = View.VISIBLE
                if (subItemInfo.tapAction != null) {
                    BcSmartSpaceUtil.setOnClickListener(
                        weatherIcon,
                        target,
                        subItemInfo.tapAction,
                        dataProvider?.getEventNotifier(),
                        WEATHER_CLOCK_TAG,
                        null,
                        0,
                    )
                } else {
                    weatherIcon.setOnClickListener(null)
                }
                if (subIcon.contentDescription != null) {
                    weatherIcon.contentDescription = subIcon.contentDescription
                }
            } else {
                weatherIcon.setImageDrawable(null)
                weatherIcon.setOnClickListener(null)
                weatherIcon.visibility = View.GONE
            }
        }
    }
}

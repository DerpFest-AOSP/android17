/*
 * Copyright (C) 2025-2026 AxionOS
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

package com.android.systemui.statusbar.connectivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ThemeEngine
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.ImageView
import com.android.settingslib.R as SettingsLibR
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.res.R
import com.android.systemui.statusbar.core.NewStatusBarIcons
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeIconController {

    private val SIGNAL_4BAR_NAMES = arrayOf(
        "ic_signal_cellular_0_4_bar",
        "ic_signal_cellular_1_4_bar",
        "ic_signal_cellular_2_4_bar",
        "ic_signal_cellular_3_4_bar",
        "ic_signal_cellular_4_4_bar",
    )

    private val SIGNAL_5BAR_NAMES = arrayOf(
        "ic_signal_cellular_0_5_bar",
        "ic_signal_cellular_1_5_bar",
        "ic_signal_cellular_2_5_bar",
        "ic_signal_cellular_3_5_bar",
        "ic_signal_cellular_4_5_bar",
        "ic_signal_cellular_5_5_bar",
    )

    private val WIFI_ICON_NAMES = arrayOf(
        "ic_wifi_signal_0",
        "ic_wifi_signal_1",
        "ic_wifi_signal_2",
        "ic_wifi_signal_3",
        "ic_wifi_signal_4",
    )

    private val _themeVersion = MutableStateFlow(0L)
    val themeVersion: StateFlow<Long> = _themeVersion.asStateFlow()

    private val refreshCallbacks = CopyOnWriteArrayList<Runnable>()

    private val mainLooper = Looper.getMainLooper()
    private val mainHandler = Handler(mainLooper)

    private val globalResyncHooksInstalled = AtomicBoolean(false)

    private val themeEngineChangeListener = ThemeEngine.ThemeChangeListener { _: String? ->
        refreshStatusBarIconCallbacks()
    }

    private val themeEngineBroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshStatusBarIconCallbacks()
            }
        }

    private var themeEngineDataObserver: ContentObserver? = null

    private var pollHandler: Handler? = null
    private var pollGeneration: Int = 0

    private val pollRunnable =
        object : Runnable {
            override fun run() {
                val h = pollHandler ?: return
                refreshStatusBarIconCallbacks()
                pollGeneration--
                if (pollGeneration > 0) {
                    h.postDelayed(this, 400L)
                }
            }
        }

    @JvmStatic
    fun registerRefreshCallback(callback: Runnable) {
        refreshCallbacks.add(callback)
    }

    @JvmStatic
    fun unregisterRefreshCallback(callback: Runnable) {
        refreshCallbacks.remove(callback)
    }

    /**
     * Re-run Wi‑Fi / mobile / RAT [refreshCallbacks] and bump [themeVersion] for Compose, without
     * clearing QS tile icon caches. Call from the same boot / dark-intensity path as
     * [ConfigurationController.notifyThemeChanged] so status bar icons match tint timing when
     * [ThemeEngine] has not fired yet.
     */
    @JvmStatic
    fun refreshStatusBarIconCallbacks() {
        if (Looper.myLooper() != mainLooper) {
            mainHandler.post { refreshStatusBarIconCallbacks() }
            return
        }
        doRefreshStatusBarIconCallbacks()
    }

    private fun doRefreshStatusBarIconCallbacks() {
        _themeVersion.value++
        for (cb in refreshCallbacks) {
            cb.run()
        }
    }

    /**
     * Extra posts after boot: [ThemeEngine] binder / target caches can lag, and Wi‑Fi/mobile
     * binders register after [DarkIconDispatcherImpl]'s first posts.
     */
    @JvmStatic
    fun scheduleDeferredStatusBarIconResyncs(handler: Handler) {
        val delays =
            longArrayOf(
                100L,
                250L,
                500L,
                750L,
                1200L,
                2000L,
                3000L,
                5000L,
                8000L,
                12000L,
                20000L,
            )
        for (delayMs in delays) {
            handler.postDelayed({ refreshStatusBarIconCallbacks() }, delayMs)
        }
    }

    /**
     * One-time hooks so status bar icons resync when [ThemeEngine] becomes ready — without
     * waiting for a new telephony / Wi‑Fi sample (binder + Secure settings often lag first paint).
     */
    @JvmStatic
    fun installGlobalThemeIconResyncHooks(context: Context, handler: Handler) {
        if (!globalResyncHooksInstalled.compareAndSet(false, true)) {
            return
        }
        val app = context.applicationContext

        ThemeEngine.getInstance(app)?.addThemeChangeListener(themeEngineChangeListener)

        val filter = IntentFilter(ThemeEngine.ACTION_THEME_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(themeEngineBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            app.registerReceiver(themeEngineBroadcastReceiver, filter)
        }

        themeEngineDataObserver =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    refreshStatusBarIconCallbacks()
                }
            }
        app.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(ThemeEngine.SETTINGS_THEME_ENGINE_DATA),
            false,
            themeEngineDataObserver!!,
            UserHandle.USER_ALL,
        )

        scheduleDeferredStatusBarIconResyncs(handler)

        pollHandler = handler
        pollGeneration = 45
        handler.post(pollRunnable)
    }

    /** True when the active system theme lists this Wi‑Fi level, even if [getThemedWifiIcon] is null. */
    @JvmStatic
    fun hasThemedWifiIconForResource(context: Context, resId: Int): Boolean {
        val level = mapWifiResIdToLevel(resId)
        if (level < 0) return false
        val engine = ThemeEngine.getInstance(context) ?: return false
        return engine.isTargetedResource(WIFI_ICON_NAMES[level])
    }

    /** True when the active system theme lists this signal level (see [getThemedSignalIcon]). */
    @JvmStatic
    fun hasThemedSignalIconForLevel(context: Context, level: Int, numLevels: Int): Boolean {
        val names = if (numLevels > 5) SIGNAL_5BAR_NAMES else SIGNAL_4BAR_NAMES
        if (level < 0 || level >= names.size) return false
        val engine = ThemeEngine.getInstance(context) ?: return false
        return engine.isTargetedResource(names[level])
    }

    /** True when [getThemedMobileDataIcon] may apply (target list or classic `*mobiledata*` names). */
    @JvmStatic
    fun hasThemedMobileDataIconForResource(context: Context, resId: Int): Boolean {
        if (resId == 0) return false
        val name =
            try {
                context.resources.getResourceEntryName(resId)
            } catch (_: Exception) {
                return false
            }
        val engine = ThemeEngine.getInstance(context) ?: return false
        return engine.isTargetedResource(name)
    }

    /**
     * True when a Wi‑Fi or signal icon RRO is still selected in the theme config ([categoryThemes]).
     *
     * [ThemeEngine.isTargetedResource] and [getThemed*] can return false or null while caches reload
     * (e.g. only [ThemeEngine.CATEGORY_BATTERY_STYLE] changed), which used to make status bar
     * binders run [resetWifiIconSizing] / [resetSignalIconSizing] in their refresh `else` branch.
     */
    @JvmStatic
    fun hasWifiIconThemeEnabledInConfig(context: Context): Boolean =
        hasAnyEnabledThemeEngineCategory(
            context,
            ThemeEngine.CATEGORY_STATUSBAR_WIFI,
            "android.theme.customization.wifi_icon",
            "wifi",
        )

    @JvmStatic
    fun hasSignalIconThemeEnabledInConfig(context: Context): Boolean =
        hasAnyEnabledThemeEngineCategory(
            context,
            ThemeEngine.CATEGORY_STATUSBAR_SIGNAL,
            "android.theme.customization.signal_icon",
            "signal",
        )

    /**
     * RAT / data-type row often ships in the same overlay as cellular bars; keep slot height
     * consistent with [hasSignalIconThemeEnabledInConfig].
     */
    @JvmStatic
    fun hasMobileTypeIconThemingEnabledInConfig(context: Context): Boolean =
        hasSignalIconThemeEnabledInConfig(context)

    private fun hasAnyEnabledThemeEngineCategory(
        context: Context,
        vararg categoryKeys: String,
    ): Boolean {
        val engine = ThemeEngine.getInstance(context) ?: return false
        for (key in categoryKeys) {
            val pkg = engine.getEnabledPackage(key) ?: continue
            if (pkg.isNotEmpty()) return true
        }
        return false
    }

    @JvmStatic
    fun onThemeChanged(tiles: Collection<com.android.systemui.plugins.qs.QSTile>) {
        if (Looper.myLooper() != mainLooper) {
            val snapshot = tiles.toList()
            mainHandler.post { onThemeChanged(snapshot) }
            return
        }
        QSTileImpl.ResourceIcon.clearCache()
        for (tile in tiles) {
            tile.refreshState()
        }
        refreshStatusBarIconCallbacks()
    }

    @JvmStatic
    fun getThemedSignalIcon(context: Context, level: Int, numLevels: Int): Drawable? {
        val engine = ThemeEngine.getInstance(context) ?: return null
        val names = if (numLevels > 5) SIGNAL_5BAR_NAMES else SIGNAL_4BAR_NAMES
        if (level < 0 || level >= names.size) return null
        val d = engine.getSystemThemeIconDrawable(names[level]) ?: return null
        return scaleDrawable(
            context,
            d,
            R.dimen.status_bar_signal_overlay_width,
            R.dimen.status_bar_signal_overlay_height,
        )
    }

    @JvmStatic
    fun hasThemedSignalIcons(context: Context): Boolean {
        val engine = ThemeEngine.getInstance(context) ?: return false
        return engine.isTargetedResource(SIGNAL_4BAR_NAMES[0])
    }

    @JvmStatic
    fun getThemedWifiIcon(context: Context, resId: Int): Drawable? {
        val level = mapWifiResIdToLevel(resId)
        if (level < 0) return null
        val engine = ThemeEngine.getInstance(context) ?: return null
        val d = engine.getSystemThemeIconDrawable(WIFI_ICON_NAMES[level]) ?: return null
        return scaleDrawable(
            context,
            d,
            R.dimen.status_bar_wifi_overlay_width,
            R.dimen.status_bar_wifi_overlay_height,
        )
    }

    /**
     * RAT / mobile-data-type icons (e.g. `ic_lte_mobiledata`, or any name listed in the active
     * system theme target arrays / [android.customization.sb_data]).
     */
    @JvmStatic
    fun getThemedMobileDataIcon(context: Context, resId: Int): Drawable? {
        if (resId == 0) return null
        val name =
            try {
                context.resources.getResourceEntryName(resId)
            } catch (_: Exception) {
                return null
            }
        val engine = ThemeEngine.getInstance(context) ?: return null
        val inTargetList = engine.isTargetedResource(name)
        val legacyMobileDataName = name.contains("mobiledata", ignoreCase = true)
        if (!inTargetList && !legacyMobileDataName) return null
        val d = engine.getSystemThemeIconDrawable(name) ?: return null
        return scaleDrawable(
            context,
            d,
            R.dimen.status_bar_mobile_data_overlay_width,
            R.dimen.status_bar_mobile_data_overlay_height,
        )
    }

    /**
     * Enlarge the status bar slot so themed bitmaps are not clamped to the default 12sp
     * NewStatusBarIcons pipeline height ([R.dimen.status_bar_mobile_signal_size_updated]).
     */
    @JvmStatic
    fun applyThemedSignalIconSizing(iconView: ImageView) {
        val lp = iconView.layoutParams
        lp.height =
            iconView.resources.getDimensionPixelSize(R.dimen.status_bar_themed_icon_slot_height)
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        iconView.adjustViewBounds = true
        iconView.layoutParams = lp
        iconView.requestLayout()
    }

    @JvmStatic
    fun resetSignalIconSizing(iconView: ImageView) {
        val res = iconView.resources
        val h =
            if (NewStatusBarIcons.isEnabled) {
                res.getDimensionPixelSize(R.dimen.status_bar_mobile_signal_size_updated)
            } else {
                res.getDimensionPixelSize(R.dimen.status_bar_mobile_signal_size)
            }
        val lp = iconView.layoutParams
        lp.height = h
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        iconView.adjustViewBounds = true
        iconView.layoutParams = lp
        iconView.requestLayout()
    }

    /**
     * Same idea as [applyThemedSignalIconSizing] for Wi‑Fi ([R.dimen.status_bar_wifi_signal_height_updated]).
     */
    @JvmStatic
    fun applyThemedWifiIconSizing(iconView: ImageView) {
        val lp = iconView.layoutParams
        lp.height =
            iconView.resources.getDimensionPixelSize(R.dimen.status_bar_themed_icon_slot_height)
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        iconView.adjustViewBounds = true
        iconView.layoutParams = lp
        iconView.requestLayout()
    }

    @JvmStatic
    fun resetWifiIconSizing(iconView: ImageView) {
        val res = iconView.resources
        val h =
            if (NewStatusBarIcons.isEnabled) {
                res.getDimensionPixelSize(R.dimen.status_bar_wifi_signal_height_updated)
            } else {
                res.getDimensionPixelSize(R.dimen.status_bar_wifi_signal_size)
            }
        val lp = iconView.layoutParams
        lp.height = h
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        iconView.adjustViewBounds = true
        iconView.layoutParams = lp
        iconView.requestLayout()
    }

    /** Align with signal / Wi‑Fi themed row; RAT view defaults to [status_bar_mobile_type_size(_updated)]. */
    @JvmStatic
    fun applyThemedMobileDataIconSizing(iconView: ImageView) {
        val lp = iconView.layoutParams
        lp.height =
            iconView.resources.getDimensionPixelSize(R.dimen.status_bar_themed_icon_slot_height)
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        iconView.adjustViewBounds = true
        iconView.layoutParams = lp
        iconView.requestLayout()
    }

    @JvmStatic
    fun resetMobileDataIconSizing(iconView: ImageView) {
        val res = iconView.resources
        val h =
            if (NewStatusBarIcons.isEnabled) {
                res.getDimensionPixelSize(R.dimen.status_bar_mobile_type_size_updated)
            } else {
                res.getDimensionPixelSize(R.dimen.status_bar_mobile_type_size)
            }
        val lp = iconView.layoutParams
        lp.height = h
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        iconView.adjustViewBounds = true
        iconView.layoutParams = lp
        iconView.requestLayout()
    }

    @JvmStatic
    fun applyThemedSignalIcon(
        context: Context, iconView: ImageView, parentGroup: android.view.ViewGroup,
        level: Int, numLevels: Int, fallback: Runnable
    ) {
        val themed = getThemedSignalIcon(context, level, numLevels)
        if (themed != null) {
            iconView.setImageDrawable(themed)
        } else {
            fallback.run()
        }
        parentGroup.invalidate()
    }

    /**
     * Maps the drawable used for the status bar / QS Wi‑Fi arc to a 0–4 level for theme lookup
     * ([WIFI_ICON_NAMES] → `ic_wifi_signal_*` in the active icon pack).
     *
     * New status bar icons ([com.android.systemui.statusbar.connectivity.WifiIcons]) use
     * SettingsLib `ic_wifi_*` / `ic_wifi_*_error` drawables, not [com.android.internal.R] ids.
     */
    private fun mapWifiResIdToLevel(resId: Int): Int {
        when (resId) {
            com.android.internal.R.drawable.ic_wifi_signal_0 -> return 0
            com.android.internal.R.drawable.ic_wifi_signal_1 -> return 1
            com.android.internal.R.drawable.ic_wifi_signal_2 -> return 2
            com.android.internal.R.drawable.ic_wifi_signal_3 -> return 3
            com.android.internal.R.drawable.ic_wifi_signal_4 -> return 4
        }
        when (resId) {
            SettingsLibR.drawable.ic_wifi_0 -> return 0
            SettingsLibR.drawable.ic_wifi_1 -> return 1
            SettingsLibR.drawable.ic_wifi_2 -> return 2
            SettingsLibR.drawable.ic_wifi_3 -> return 4
            SettingsLibR.drawable.ic_wifi_0_error -> return 0
            SettingsLibR.drawable.ic_wifi_1_error -> return 1
            SettingsLibR.drawable.ic_wifi_2_error -> return 2
            SettingsLibR.drawable.ic_wifi_3_error -> return 4
        }
        when (resId) {
            SettingsLibR.drawable.ic_no_internet_wifi_signal_0 -> return 0
            SettingsLibR.drawable.ic_no_internet_wifi_signal_1 -> return 1
            SettingsLibR.drawable.ic_no_internet_wifi_signal_2 -> return 2
            SettingsLibR.drawable.ic_no_internet_wifi_signal_3 -> return 3
            SettingsLibR.drawable.ic_no_internet_wifi_signal_4 -> return 4
        }
        return -1
    }

    private fun scaleDrawable(
        context: Context,
        d: Drawable,
        widthDimenRes: Int,
        heightDimenRes: Int,
    ): Drawable {
        val res = context.resources
        val w = res.getDimensionPixelSize(widthDimenRes)
        val h = res.getDimensionPixelSize(heightDimenRes)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        d.setBounds(0, 0, w, h)
        d.draw(canvas)
        return BitmapDrawable(res, bitmap)
    }
}

/*
 * Copyright (C) 2025 The AxionAOSP Project
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
package com.android.systemui.pulse

import android.content.Context
import android.media.session.PlaybackState
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.media.MediaSessionManager
import com.android.systemui.util.ScrimUtils
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@SysUISingleton
class PulseViewController @Inject constructor(
    private val context: Context
) : PulseAudioDataProcessor.DataListener,
    MediaSessionManager.MediaDataListener,
    ScrimUtils.ScrimEventListener {

    private val mainScope = MainScope()
    private var listenersRegistered = false

    private var isMediaPlaying = false
    private var bouncerShowingOrKeyguardDismissing = false
    private var keyguardShowing = false
    private var isDozing = false

    private val settingsRepository: PulseSettingsRepository =
        PulseSettingsRepository(context)

    private val bassHaptics: PulseBassHaptics =
        PulseBassHaptics(context, settingsRepository)

    /** Lock screen / shade overlay (see [CentralSurfacesImpl.attachCustomOverlays]). */
    private val view: PulseView =
        PulseView(context)

    /** Optional duplicate visualizer drawn behind nav bar buttons. */
    private val navbarPulseView: PulseView =
        PulseView(context)

    private val audioProcessor: PulseAudioDataProcessor =
        PulseAudioDataProcessor(context).apply {
            setDataListener(this@PulseViewController)
        }

    val pulseEnabled: Boolean
        get() = settingsRepository.isPulseEnabled()

    val ambientEnabled: Boolean
        get() = settingsRepository.isPulseAmbientEnabled()

    val pulseQsEnabled: Boolean
        get() = settingsRepository.isPulseQsEnabled()

    val pulseNavbarEnabled: Boolean
        get() = settingsRepository.isPulseNavbarEnabled()

    private val isCollapsed: Boolean
        get() = ScrimUtils.get().isPanelFullyCollapsed()

    var pulseRunning: Boolean = false
        private set

    init {
        INSTANCE = this

        view.initialize(settingsRepository)
        navbarPulseView.initialize(settingsRepository)
        settingsRepository.setOnSettingsChangedListener { onSettingsChanged() }
        settingsRepository.startObserving()
        onSettingsChanged()
    }

    fun getPulseView(): PulseView = view

    fun getNavbarPulseView(): PulseView = navbarPulseView

    /** Shade / lockscreen overlay: collapsed panel + keyguard / ambient rules or QS mode. */
    private fun overlayWantsPulse(): Boolean {
        if (!isMediaPlaying) return false
        if (pulseQsEnabled) return true
        return !bouncerShowingOrKeyguardDismissing
                && isCollapsed
                && ((keyguardShowing && !isDozing)
                || (isDozing && ambientEnabled))
    }

    /** Nav bar uses only media + [pulse_navbar_enabled] (independent of lockscreen visibility). */
    private fun navbarWantsPulse(): Boolean {
        return isMediaPlaying && settingsRepository.isPulseNavbarEnabled()
    }

    private fun updateState() {
        if (!pulseEnabled) {
            pulseRunning = false
            bassHaptics.reset()
            mainScope.launch {
                view.setVisibility(false)
                navbarPulseView.setVisibility(false)
                audioProcessor.stopCapture()
            }
            return
        }
        val overlay = overlayWantsPulse()
        val navbar = navbarWantsPulse()
        val run = overlay || navbar
        pulseRunning = run
        mainScope.launch {
            view.setVisibility(overlay && run)
            navbarPulseView.setVisibility(navbar && run)
            if (run) {
                audioProcessor.startCapture()
            } else {
                bassHaptics.reset()
                audioProcessor.stopCapture()
            }
        }
    }

    private fun onSettingsChanged() {
        val enabled = pulseEnabled
        if (enabled && !listenersRegistered) {
            ScrimUtils.get().addListener(this)
            MediaSessionManager.get().addListener(this)
            listenersRegistered = true
        } else if (!enabled && listenersRegistered) {
            ScrimUtils.get().removeListener(this)
            MediaSessionManager.get().removeListener(this)
            listenersRegistered = false
            pulseRunning = false
            bassHaptics.reset()
            mainScope.launch {
                view.setVisibility(false)
                navbarPulseView.setVisibility(false)
                audioProcessor.stopCapture()
            }
        }
        updateState()
    }

    override fun onDataUpdate(data: PulseData) {
        if (pulseRunning) {
            mainScope.launch {
                if (data.isDataValid) {
                    bassHaptics.onFft(data.fftBytes)
                }
                view.updateVisualizerData(data)
                navbarPulseView.updateVisualizerData(data)
            }
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        isMediaPlaying = state == PlaybackState.STATE_PLAYING
        updateState()
    }

    override fun onMediaColorsChanged(color: Int) {
        if (pulseEnabled) {
            view.onMediaColorsChanged(color)
            navbarPulseView.onMediaColorsChanged(color)
        }
    }

    override fun onKeyguardShowingChanged(showing: Boolean) {
        keyguardShowing = showing
        updateState()
    }

    override fun onDozingChanged(dozing: Boolean) {
        isDozing = dozing
        updateState()
    }

    override fun onExpandedFractionChanged(expandedFraction: Float) {
        updateState()
    }

    override fun onBarStateChanged(state: Int) {
        updateState()
    }

    override fun onQsVisibilityChanged(visible: Boolean) {
        updateState()
    }

    override fun onKeyguardFadingAwayChanged(fadingAway: Boolean) {
        bouncerShowingOrKeyguardDismissing = fadingAway
        updateState()
    }

    override fun onKeyguardGoingAwayChanged(goingAway: Boolean) {
        bouncerShowingOrKeyguardDismissing = goingAway
        updateState()
    }

    override fun onPrimaryBouncerShowingChanged(showing: Boolean) {
        bouncerShowingOrKeyguardDismissing = showing
        updateState()
    }

    override fun onScreenTurnedOff() {
        pulseRunning = false
        bassHaptics.reset()
        mainScope.launch {
            view.setVisibility(false)
            navbarPulseView.setVisibility(false)
            audioProcessor.stopCapture()
        }
    }

    override fun onStartedWakingUp() {
        updateState()
    }

    override fun onUserChanged() {
        settingsRepository.invalidateCache()
        updateState()
    }

    fun destroy() {
        pulseRunning = false
        bassHaptics.reset()
        settingsRepository.stopObserving()
        if (listenersRegistered) {
            ScrimUtils.get().removeListener(this)
            MediaSessionManager.get().removeListener(this)
            listenersRegistered = false
        }
        audioProcessor.cleanup()
        mainScope.cancel()
    }

    companion object {
        private const val TAG = "PulseViewController"

        @Volatile
        private var INSTANCE: PulseViewController? = null

        @JvmStatic
        fun get(context: Context): PulseViewController {
            return INSTANCE ?: throw IllegalStateException(
                "PulseViewController not initialized"
            )
        }
    }
}

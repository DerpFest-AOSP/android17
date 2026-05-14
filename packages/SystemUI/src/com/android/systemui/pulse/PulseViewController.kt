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
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import com.android.systemui.statusbar.notification.headsup.OnHeadsUpChangedListener
import com.android.systemui.statusbar.notification.headsup.OnHeadsUpPhoneListenerChange
import com.android.systemui.util.ScrimUtils
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@SysUISingleton
class PulseViewController @Inject constructor(
    private val context: Context,
    private val headsUpManager: HeadsUpManager,
) : PulseAudioDataProcessor.DataListener,
    MediaSessionManager.MediaDataListener,
    ScrimUtils.ScrimEventListener {

    private val mainScope = MainScope()
    private var listenersRegistered = false
    /** [HeadsUpManager] has no remove for phone listeners; register at most once. */
    private var headsUpPhoneListenerRegistered = false

    private var isMediaPlaying = false
    private var bouncerShowingOrKeyguardDismissing = false
    private var keyguardShowing = false
    private var isDozing = false
    private var isScreenOff = false

    private val settingsRepository: PulseSettingsRepository =
        PulseSettingsRepository(context)

    private val bassHaptics: PulseBassHaptics =
        PulseBassHaptics(context, settingsRepository)

    private val view: PulseView =
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

    private val isCollapsed: Boolean
        get() = ScrimUtils.get().isPanelFullyCollapsed()

    private val hapticsMode: Int
        get() = settingsRepository.getPulseHapticsMode()

    private val headsUpChangedListener =
        object : OnHeadsUpChangedListener {
            override fun onHeadsUpPinnedModeChanged(inPinnedMode: Boolean) {
                updateState()
            }

            override fun onHeadsUpPinned(entry: NotificationEntry) {
                updateState()
            }

            override fun onHeadsUpUnPinned(entry: NotificationEntry) {
                updateState()
            }

            override fun onHeadsUpStateChanged(entry: NotificationEntry, isHeadsUp: Boolean) {
                updateState()
            }

            override fun onHeadsUpAnimatingAwayEnded(entry: NotificationEntry) {
                updateState()
            }
        }

    private val headsUpPhoneListener =
        object : OnHeadsUpPhoneListenerChange {
            override fun onHeadsUpAnimatingAwayStateChanged(headsUpAnimatingAway: Boolean) {
                updateState()
            }
        }

    /** True while a heads-up is on screen or finishing its exit animation. */
    private fun isHeadsUpShowing(): Boolean =
        headsUpManager.hasPinnedHeadsUp() ||
            headsUpManager.isHeadsUpAnimatingAwayValue() ||
            headsUpManager.getTopEntry() != null

    var pulseRunning: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            updatePulse(value)
        }

    init {
        INSTANCE = this

        view.initialize(settingsRepository)
        settingsRepository.setOnSettingsChangedListener { onSettingsChanged() }
        settingsRepository.startObserving()
        onSettingsChanged()
    }

    fun getPulseView(): PulseView = view

    private fun updateState() {
        if (!pulseEnabled) {
            pulseRunning = false
            bassHaptics.reset()
            return
        }
        if (pulseQsEnabled) {
            pulseRunning =
                isMediaPlaying && !isScreenOff && !isHeadsUpShowing()
        } else {
            pulseRunning = isMediaPlaying
                    && !bouncerShowingOrKeyguardDismissing
                    && isCollapsed
                    && !isScreenOff
                    && !isHeadsUpShowing()
                    && ((keyguardShowing && !isDozing)
                    || (isDozing && ambientEnabled))
        }
    }

    private fun updatePulse(show: Boolean) {
        mainScope.launch {
            view.setVisibility(show)
            // Never run FFT capture while the screen is off (saves power; avoids haptics-only
            // mode keeping the visualizer pipeline awake in pocket / sleep).
            val wantCapture =
                pulseEnabled && !isScreenOff && (show || hapticsMode > 1)
            if (wantCapture) {
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
            headsUpManager.addListener(headsUpChangedListener)
            if (!headsUpPhoneListenerRegistered) {
                headsUpManager.addHeadsUpPhoneListener(headsUpPhoneListener)
                headsUpPhoneListenerRegistered = true
            }
            listenersRegistered = true
        } else if (!enabled && listenersRegistered) {
            ScrimUtils.get().removeListener(this)
            MediaSessionManager.get().removeListener(this)
            headsUpManager.removeListener(headsUpChangedListener)
            listenersRegistered = false
            pulseRunning = false
            bassHaptics.reset()
            mainScope.launch {
                view.setVisibility(false)
                audioProcessor.stopCapture()
            }
        }
        updateState()
        // Re-apply capture / visibility when haptics mode etc. changes without pulseRunning toggling.
        updatePulse(pulseRunning)
    }

    override fun onDataUpdate(data: PulseData) {
        if (hapticsMode > 0 && data.isDataValid) {
            bassHaptics.onFft(data.fftBytes)
        }
        if (pulseRunning) {
            mainScope.launch {
                view.updateVisualizerData(data)
            }
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        isMediaPlaying = state == PlaybackState.STATE_PLAYING
        updateState()
    }

    override fun onMediaColorsChanged(color: Int) {
        if (pulseEnabled) view.onMediaColorsChanged(color)
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
        isScreenOff = true
        updateState()
        updatePulse(pulseRunning)
    }

    override fun onStartedWakingUp() {
        isScreenOff = false
        updateState()
        updatePulse(pulseRunning)
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
            headsUpManager.removeListener(headsUpChangedListener)
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

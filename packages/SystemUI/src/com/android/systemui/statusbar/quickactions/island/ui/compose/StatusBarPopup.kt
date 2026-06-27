/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.quickactions.island.ui.compose

import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.IslandActions
import com.android.systemui.axdynamicbar.ui.compose.CallExpanded
import com.android.systemui.axdynamicbar.ui.compose.MediaCard
import com.android.systemui.axdynamicbar.ui.compose.PrimaryCard
import com.android.systemui.axdynamicbar.ui.compose.PromotedOngoingExpanded
import com.android.systemui.axdynamicbar.ui.compose.SportsExpanded
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.media.MediaSessionManager
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.island.alarm.ui.compose.AlarmPopup
import com.android.systemui.statusbar.quickactions.island.flashlight.ui.compose.FlashlightPopup
import com.android.systemui.statusbar.quickactions.island.livescore.ui.compose.LiveScorePopup
import com.android.systemui.statusbar.quickactions.island.media.ui.compose.LyricsCard
import com.android.systemui.statusbar.quickactions.island.ui.model.PopupChipModel
import com.android.systemui.statusbar.quickactions.island.ui.model.PopupContentModel
import com.android.systemui.statusbar.quickactions.island.screenrecord.ui.compose.ScreenRecordPopup
import com.android.systemui.statusbar.quickactions.island.stopwatch.ui.compose.StopwatchPopup

/**
 * Displays a popup in the status bar area. The offset is calculated to draw the popup below the
 * status bar.
 */
@Composable
fun StatusBarPopup(
    viewModel: PopupChipModel.Shown,
    isVisible: Boolean,
    islandActions: IslandActions,
) {
    val density = Density(LocalContext.current)
    Popup(
        alignment = Alignment.TopCenter,
        properties =
            PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        offset =
            IntOffset(
                x = 0,
                y = with(density) { dimensionResource(R.dimen.status_bar_height).roundToPx() },
            ),
        onDismissRequest = { viewModel.hidePopup() },
    ) {
        val popupView = LocalView.current
        var mediaColor by remember { mutableIntStateOf(0) }
        DisposableEffect(viewModel.popupContent) {
            val listener =
                object : MediaSessionManager.MediaDataListener {
                    override fun onMediaColorsChanged(color: Int) {
                        mediaColor = color
                    }
                }
            MediaSessionManager.get().addListener(listener)
            onDispose { MediaSessionManager.get().removeListener(listener) }
        }
        DisposableEffect(popupView) {
            val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (!hasFocus) {
                    viewModel.hidePopup()
                }
            }
            popupView.viewTreeObserver.addOnWindowFocusChangeListener(listener)
            onDispose {
                popupView.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter =
                fadeIn(animationSpec = tween(180)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(220)) +
                    slideInVertically(
                        initialOffsetY = { fullHeight -> -fullHeight / 6 },
                        animationSpec = tween(220),
                    ),
            exit =
                fadeOut(animationSpec = tween(160)) +
                    scaleOut(targetScale = 0.92f, animationSpec = tween(180)) +
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> -fullHeight / 8 },
                        animationSpec = tween(180),
                    ),
        ) {
            Box(modifier = Modifier.padding(8.dp).wrapContentSize()) {
                when (val popupContent = viewModel.popupContent) {
                    is PopupContentModel.Media -> {
                        val model = popupContent.model
                        val eventMedia =
                            remember(model, mediaColor) {
                                val albumArtDrawable =
                                    (model.artworkIcon as? Icon.Loaded)?.drawable
                                val appIconDrawable = (model.appIcon as? Icon.Loaded)?.drawable
                                IslandEvent.Media(
                                    track = model.songName?.toString().orEmpty(),
                                    artist = model.artistName?.toString().orEmpty(),
                                    isPlaying = model.isPlaying,
                                    albumArt = albumArtDrawable,
                                    progress =
                                        if (model.durationMs > 0L) {
                                            model.positionMs.toFloat() / model.durationMs
                                        } else {
                                            0f
                                        },
                                    duration = model.durationMs,
                                    position = model.positionMs,
                                    packageName = model.packageName.orEmpty(),
                                    appIcon = appIconDrawable,
                                    mediaColor = mediaColor,
                                )
                            }
                        val hasLyrics =
                            model.isDynamicIslandLyricsEnabled &&
                                (!model.lyrics.isNullOrBlank() ||
                                    !model.syncedLyrics.isNullOrBlank())
                        if (hasLyrics) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(modifier = Modifier.widthIn(min = 320.dp, max = 400.dp)) {
                                    MediaCard(event = eventMedia, interactor = islandActions)
                                }
                                LyricsCard(model = model)
                            }
                        } else {
                            Box(modifier = Modifier.widthIn(min = 320.dp, max = 400.dp)) {
                                MediaCard(event = eventMedia, interactor = islandActions)
                            }
                        }
                    }
                    is PopupContentModel.ScreenRecord -> ScreenRecordPopup(model = popupContent.model)
                    is PopupContentModel.LiveScore -> {
                        val model = popupContent.model
                        val eventSports =
                            remember(model) {
                                val titleParts =
                                    model.title?.split(
                                        Regex("\\s*vs\\s*|\\s*-\\s*|\\s*@\\s*"),
                                        limit = 2,
                                    ) ?: emptyList()
                                val scoreParts =
                                    model.score.split(Regex("\\s*-\\s*|\\s*:\\s*"), limit = 2)
                                IslandEvent.Sports(
                                    team1Name = titleParts.getOrNull(0) ?: model.title ?: model.appName,
                                    team2Name = titleParts.getOrNull(1) ?: "",
                                    score1 = scoreParts.getOrNull(0) ?: model.score,
                                    score2 = scoreParts.getOrNull(1) ?: "",
                                    team1Icon = (model.icon as? Icon.Loaded)?.drawable,
                                    statusDetail = model.subtitle.orEmpty(),
                                    league = model.appName,
                                    key = model.key,
                                )
                            }
                        Box(modifier = Modifier.widthIn(min = 280.dp, max = 340.dp)) {
                            PrimaryCard {
                                SportsExpanded(event = eventSports, interactor = islandActions)
                            }
                        }
                    }
                    is PopupContentModel.Flashlight -> FlashlightPopup(model = popupContent.model)
                    is PopupContentModel.Stopwatch -> StopwatchPopup(model = popupContent.model)
                    is PopupContentModel.Alarm -> AlarmPopup(model = popupContent.model)
                    is PopupContentModel.PromotedOngoing -> {
                        Box(modifier = Modifier.widthIn(min = 320.dp, max = 400.dp)) {
                            PrimaryCard {
                                PromotedOngoingExpanded(
                                    event = popupContent.event,
                                    interactor = islandActions,
                                )
                            }
                        }
                    }
                    is PopupContentModel.OngoingCall -> {
                        Box(modifier = Modifier.widthIn(min = 320.dp, max = 400.dp)) {
                            PrimaryCard {
                                CallExpanded(event = popupContent.event, interactor = islandActions)
                            }
                        }
                    }
                    PopupContentModel.None -> Unit
                }
            }
        }
    }
}

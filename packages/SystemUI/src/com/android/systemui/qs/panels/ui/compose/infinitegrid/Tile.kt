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

@file:OptIn(ExperimentalFoundationApi::class)

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import android.content.Context
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.os.Trace
import android.os.UserHandle
import android.provider.Settings
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.Tile.STATE_UNAVAILABLE
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.trace
import androidx.core.graphics.ColorUtils
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.Expandable
import com.android.compose.animation.bounceable
import com.android.compose.animation.rememberExpandableController
import com.android.compose.animation.scene.ContentScope
import com.android.compose.modifiers.thenIf
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.mechanics.compose.modifier.verticalFadeContentReveal
import com.android.mechanics.compose.modifier.verticalTactileSurfaceReveal
import com.android.systemui.Flags
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModel
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModelFactoryProvider
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.compose.BounceableInfo
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.ActiveTileCornerRadius
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.ClassicCircleSize
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.ClassicTileHeight
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.InactiveCornerRadius
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.TileHeight
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.longPressLabelMoreDetails
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.longPressLabelSettings
import com.android.systemui.qs.tiles.impl.ringer.QSTileRingerSlider
import com.android.systemui.qs.panels.ui.viewmodel.AccessibilityUiState
import com.android.systemui.qs.panels.ui.viewmodel.BounceableTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.IconProvider
import com.android.systemui.qs.panels.ui.viewmodel.TileUiState
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import com.android.systemui.qs.ui.compose.borderOnFocus
import com.android.systemui.res.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation

val LocalQSPanelStyle = compositionLocalOf { 0 }

val LocalQSTileLabelHide = compositionLocalOf { false }

/** Resolved shape key for classic QS tiles; see [QSTileIconShapes]. */
val LocalQSTileIconShapeKey = compositionLocalOf { QSTileIconShapes.DEFAULT_KEY }

@Composable
fun rememberQSPanelStyle(): Int = rememberSecureIntSetting("qs_panel_style")

@Composable
fun rememberQSTileLabelHide(): Boolean = rememberSecureIntSetting("qs_tile_label_hide") == 1

@Composable
fun rememberQSTileIconShapeKey(): String {
    val raw =
        rememberSecureStringSetting(QSTileIconShapes.SETTINGS_KEY, defaultValue = null)
    return remember(raw) { QSTileIconShapes.normalizeKey(raw) }
}

@Composable
private fun rememberSecureIntSetting(key: String, defaultValue: Int = 0): Int {
    val context = LocalContext.current
    val value by produceState(
        initialValue =
            Settings.Secure.getIntForUser(
                context.contentResolver,
                key,
                defaultValue,
                UserHandle.USER_CURRENT,
            ),
    ) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    value =
                        Settings.Secure.getIntForUser(
                            context.contentResolver,
                            key,
                            defaultValue,
                            UserHandle.USER_CURRENT,
                        )
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(key),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        try {
            awaitCancellation()
        } finally {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return value
}

@Composable
private fun rememberSecureStringSetting(key: String, defaultValue: String?): String? {
    val context = LocalContext.current
    val value by produceState(
        initialValue =
            Settings.Secure.getStringForUser(
                context.contentResolver,
                key,
                UserHandle.USER_CURRENT,
            )
                ?: defaultValue,
    ) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    value =
                        Settings.Secure.getStringForUser(
                            context.contentResolver,
                            key,
                            UserHandle.USER_CURRENT,
                        )
                            ?: defaultValue
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(key),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        try {
            awaitCancellation()
        } finally {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return value
}

@Composable
fun TileLazyGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        state = state,
        columns = columns,
        verticalArrangement = spacedBy(CommonTileDefaults.TileArrangementPadding),
        horizontalArrangement = spacedBy(CommonTileDefaults.TileArrangementPadding),
        contentPadding = contentPadding,
        modifier = modifier,
        content = content,
    )
}

private val TileViewModel.traceName
    get() = spec.toString().takeLast(Trace.MAX_SECTION_NAME_LEN)

/**
 * This composable function is responsible for rendering a tile based on the provided
 * [TileViewModel]. It handles different states of the tile (e.g., available, unavailable),
 * interactions (click, long click), and visual styles (icon only or large tile).
 *
 * @param tile The [TileViewModel] containing the data and logic for the tile.
 * @param iconOnly A boolean indicating whether to display only the icon of the tile or the full
 *   tile content (false for large tiles).
 * @param squishiness The float value representing the current squishiness factor of the tile, used
 *   for animations.
 * @param coroutineScope The [CoroutineScope] to launch coroutines for animations.
 * @param tileHapticsViewModelFactoryProvider A provider for creating a [TileHapticsViewModel]
 *   instance, used for haptic feedback.
 * @param interactionSource An optional [MutableInteractionSource] to track user interactions with
 *   the tile, used by the parent composable to animate a bounce effect. Tiles may or may not use
 *   this interaction source to control whether they should bounce or not.
 * @param modifier An optional [Modifier] to be applied to the root composable of the tile.
 * @param isVisible Whether the tile is currently visible. Defaults to true.
 * @param requestToggleTextFeedback A lambda function that is invoked when a toggleable icon only
 *   tile is clicked, used to request the feedback text.
 * @param detailsViewModel An optional [DetailsViewModel] used to handle navigation to a detailed
 *   view when a tile is clicked, if applicable.
 * @param enableRevealEffect If `true`, the tiles will animate using the reveal animation.
 */
@Composable
fun ContentScope.Tile(
    tile: TileViewModel,
    iconOnly: Boolean,
    squishiness: () -> Float,
    coroutineScope: CoroutineScope,
    bounceableInfo: BounceableInfo?,
    tileHapticsViewModelFactoryProvider: TileHapticsViewModelFactoryProvider,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
    isVisible: () -> Boolean = { true },
    requestToggleTextFeedback: (TileSpec) -> Unit = {},
    detailsViewModel: DetailsViewModel?,
    enableRevealEffect: Boolean = false,
) {
    trace(tile.traceName) {
        val currentBounceableInfo by rememberUpdatedState(bounceableInfo)
        val resources = resources()

        /*
         * Use produce state because [QSTile.State] doesn't have well defined equals (due to
         * inheritance). This way, even if tile.state changes, uiState may not change and lead to
         * recomposition.
         */
        val uiState by
            produceState(tile.currentState.toUiState(resources), tile, resources) {
                tile.state.collect { value = it.toUiState(resources) }
            }
        val isClickable = uiState.state != STATE_UNAVAILABLE

        val icon by
            produceState(tile.currentState.toIconProvider(), tile) {
                tile.state.collect { value = it.toIconProvider() }
            }

        val colors = TileDefaults.getColorForState(uiState, iconOnly)
        val hapticsViewModel: TileHapticsViewModel? =
            rememberViewModel(traceName = "TileHapticsViewModel") {
                tileHapticsViewModelFactoryProvider.getHapticsViewModelFactory()?.create(tile)
            }

        val isClassicPanelStyle = LocalQSPanelStyle.current == 1

        if (tile.spec.spec == "sound" && !iconOnly) {
            QSTileRingerSlider()
            return@trace
        }

        val shapeMode = rememberTileShapeMode()
        val wantCircle = !isClassicPanelStyle && shapeMode == 3 && iconOnly
        val tileShape =
            if (wantCircle) CircleShape
            else TileDefaults.animateTileShapeAsState(uiState.state, shapeMode).value
        val animatedColor by animateColorAsState(colors.background, label = "QSTileBackgroundColor")
        val isDualTarget = uiState.handlesSecondaryClick

        val backgroundBrush = colors.backgroundBrush
        val outerShape = if (wantCircle) RoundedCornerShape(0.dp) else tileShape
        val outerColor: () -> Color =
            when {
                wantCircle -> { { Color.Transparent } }
                backgroundBrush != null -> { { Color.Transparent } }
                else -> { { animatedColor } }
            }
        val panelExpandableColor: () -> Color =
            if (isClassicPanelStyle) {
                { Color.Transparent }
            } else {
                outerColor
            }
        val panelExpandableShape: Shape =
            if (isClassicPanelStyle) {
                RoundedCornerShape(0.dp)
            } else {
                outerShape
            }
        val focusBorderColor = MaterialTheme.colorScheme.secondary

        val surfaceRevealModifier: Modifier
        val contentRevealModifier: Modifier
        if (enableRevealEffect) {
            val marginBottom =
                with(LocalDensity.current) { QuickSettingsShade.Dimensions.Padding.toPx() }
            surfaceRevealModifier =
                Modifier.verticalTactileSurfaceReveal(deltaY = marginBottom, label = tile.traceName)
            contentRevealModifier =
                Modifier.verticalFadeContentReveal(deltaY = marginBottom, label = tile.traceName)
        } else {
            surfaceRevealModifier = Modifier
            contentRevealModifier = Modifier
        }

        TileExpandable(
            color = panelExpandableColor,
            shape = panelExpandableShape,
            squishiness = squishiness,
            hapticsViewModel = hapticsViewModel,
            modifier =
                modifier
                    .then(surfaceRevealModifier)
                    .thenIf(backgroundBrush != null && !wantCircle && !isClassicPanelStyle) {
                        Modifier.background(requireNotNull(backgroundBrush), outerShape)
                    }
                    .thenIf(!wantCircle && !isClassicPanelStyle) {
                        modifier.borderOnFocus(color = focusBorderColor, outerShape.topEnd)
                    }
                    .fillMaxWidth()
                    .height(
                        when {
                            isClassicPanelStyle && LocalQSTileLabelHide.current ->
                                ClassicCircleSize + 8.dp
                            isClassicPanelStyle -> ClassicTileHeight
                            else -> CommonTileDefaults.TileHeight
                        },
                    )
                    .thenIf(currentBounceableInfo != null) {
                        Modifier.bounceable(
                            currentBounceableInfo!!.bounceable,
                            currentBounceableInfo!!.previousTile,
                            currentBounceableInfo!!.nextTile,
                            orientation = Orientation.Horizontal,
                            bounceEnd = currentBounceableInfo!!.bounceEnd,
                        )
                    },
        ) { expandable ->
            // Use main click on long press for small, available dual target tiles.
            // Open settings otherwise.
            val useLongClickToSettings = !(iconOnly && isDualTarget && isClickable)
            val longClick: (() -> Unit)? =
                {
                        hapticsViewModel?.setTileInteractionState(
                            TileHapticsViewModel.TileInteractionState.LONG_CLICKED
                        )

                        if (useLongClickToSettings) {
                            tile.settingsClick(expandable)
                        } else {
                            tile.mainClick(expandable)
                        }
                    }
                    .takeIf { !useLongClickToSettings || uiState.handlesLongClick }

            // Bounce the tile's container if it is toggleable and is not a large
            // dual target tile. These don't toggle on main click.
            val bounceContainer = uiState.isToggleable && (iconOnly || !isDualTarget)
            val contentBounceable =
                remember(currentBounceableInfo) {
                    currentBounceableInfo?.bounceable ?: BounceableTileViewModel()
                }
            val click: (() -> Unit)? =
                onClick@{
                        if (!isClickable) return@onClick

                        val hasDetails =
                            QsDetailedView.isEnabled &&
                                detailsViewModel?.onTileClicked(tile.spec) == true
                        if (hasDetails) return@onClick

                        // For those tile's who doesn't have a detailed view, process with
                        // their `onClick` behavior.
                        if (iconOnly && isDualTarget) {
                            tile.toggleClick()
                        } else {
                            tile.mainClick(expandable)
                        }

                        // Side effects of the click
                        hapticsViewModel?.setTileInteractionState(
                            TileHapticsViewModel.TileInteractionState.CLICKED
                        )

                        coroutineScope.launch {
                            // Bounce the tile's container if it is toggleable and is not a large
                            // dual target tile. These don't toggle on main click. Otherwise bounce
                            // the content of the tile.
                            if (bounceContainer) {
                                // Only bounce the container ourselves if a BounceableInfo was given
                                currentBounceableInfo?.bounceable?.animateContainerBounce()
                            } else {
                                contentBounceable.animateContentBounce(iconOnly)
                            }
                        }
                        if (uiState.isToggleable && iconOnly) {
                            // And show footer text feedback for icons
                            requestToggleTextFeedback(tile.spec)
                        }
                }

            if (wantCircle && !isClassicPanelStyle) {
                val interaction = remember { MutableInteractionSource() }

                Box(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .size(CommonTileDefaults.TileHeight)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .drawBehind {
                                backgroundBrush?.let { drawRect(brush = it) }
                                    ?: drawRect(animatedColor)
                            }
                            .indication(interaction, LocalIndication.current)
                            .tileCombinedClickable(
                                onClick = { click?.invoke() ?: Unit },
                                onLongClick = longClick,
                                accessibilityUiState = uiState.accessibilityUiState,
                                interactionSource = interactionSource.takeIf { bounceContainer }
                                    ?: interaction,
                                iconOnly = true,
                                isDualTarget = isDualTarget,
                            )
                            .tileTestTag(iconOnly),
                    ) {
                        val iconProvider: Context.() -> Icon = { getTileIcon(icon = icon) }
                        SmallTileContent(
                            iconProvider = iconProvider,
                            color = colors.icon,
                            modifier =
                                Modifier.align(Alignment.Center).bounceScale {
                                    contentBounceable.iconBounceScale
                                },
                        )
                    }
                }
            } else {
                TileContainer(
                    onClick = click,
                    onLongClick = longClick,
                    accessibilityUiState = uiState.accessibilityUiState,
                    iconOnly = iconOnly,
                    isDualTarget = isDualTarget,
                    interactionSource = interactionSource.takeIf { bounceContainer },
                    modifier = contentRevealModifier,
                ) {
                    val iconProvider: Context.() -> Icon = { getTileIcon(icon = icon) }
                    if (isClassicPanelStyle) {
                        ClassicCircleTileContent(
                            label = uiState.label,
                            iconProvider = iconProvider,
                            colors = colors,
                            hideLabel = LocalQSTileLabelHide.current,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else if (iconOnly) {
                        SmallTileContent(
                            iconProvider = iconProvider,
                            color = colors.icon,
                            modifier =
                                Modifier.align(Alignment.Center).bounceScale {
                                    contentBounceable.iconBounceScale
                                },
                        )
                    } else {
                        val iconShape by TileDefaults.animateIconShapeAsState(uiState.state, shapeMode)
                        val secondaryClick: (() -> Unit)? =
                            {
                                    hapticsViewModel?.setTileInteractionState(
                                        TileHapticsViewModel.TileInteractionState.CLICKED
                                    )
                                    tile.toggleClick()
                                }
                                .takeIf { isDualTarget }
                        LargeTileContent(
                            label = uiState.label,
                            secondaryLabel = uiState.secondaryLabel,
                            iconProvider = iconProvider,
                            sideDrawable = uiState.sideDrawable,
                            colors = colors,
                            iconShape = iconShape,
                            toggleClick = secondaryClick,
                            onLongClick = longClick,
                            accessibilityUiState = uiState.accessibilityUiState,
                            squishiness = squishiness,
                            isVisible = isVisible,
                            textScale = { contentBounceable.textBounceScale },
                            modifier =
                                Modifier.largeTilePadding(isDualTarget = uiState.handlesLongClick),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TileExpandable(
    color: () -> Color,
    shape: Shape,
    squishiness: () -> Float,
    hapticsViewModel: TileHapticsViewModel?,
    modifier: Modifier = Modifier,
    content: @Composable (Expandable) -> Unit,
) {
    Expandable(
        controller = rememberExpandableController(color = color, shape = shape),
        modifier = modifier.clip(shape).verticalSquish(squishiness),
        useModifierBasedImplementation = true,
    ) {
        content(hapticsViewModel?.createStateAwareExpandable(it) ?: it)
    }
}

@Composable
fun TileContainer(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    accessibilityUiState: AccessibilityUiState,
    iconOnly: Boolean,
    isDualTarget: Boolean,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isClassic = LocalQSPanelStyle.current == 1
    val tileHeight =
        if (isClassic && LocalQSTileLabelHide.current) {
            ClassicCircleSize + 8.dp
        } else if (isClassic) {
            ClassicTileHeight
        } else {
            TileHeight
        }
    Box(
        modifier =
            modifier
                .height(tileHeight)
                .fillMaxWidth()
                .tileCombinedClickable(
                    onClick = onClick ?: {},
                    onLongClick = onLongClick,
                    accessibilityUiState = accessibilityUiState,
                    iconOnly = iconOnly,
                    isDualTarget = isDualTarget,
                    interactionSource = interactionSource,
                )
                .tileTestTag(iconOnly),
        content = content,
    )
}

@Composable
fun LargeStaticTile(
    uiState: TileUiState,
    iconProvider: IconProvider,
    modifier: Modifier = Modifier,
) {
    val shapeMode = rememberTileShapeMode()

    val colors = TileDefaults.getColorForState(uiState = uiState, iconOnly = false)

    Box(
        modifier
            .clip(TileDefaults.animateTileShapeAsState(state = uiState.state, shapeMode = shapeMode).value)
            .drawBehind {
                val brush = colors.backgroundBrush
                if (brush != null) {
                    drawRect(brush = brush)
                } else {
                    drawRect(color = colors.background)
                }
            }
            .height(TileHeight)
            .largeTilePadding()
    ) {
        LargeTileContent(
            label = uiState.label,
            secondaryLabel = "",
            iconProvider = { getTileIcon(icon = iconProvider) },
            sideDrawable = null,
            colors = colors,
            squishiness = { 1f },
        )
    }
}

private fun Context.getTileIcon(icon: IconProvider): Icon {
    return icon.icon?.let {
        if (it is QSTileImpl.ResourceIcon) {
            Icon.Resource(it.resId, null)
        } else {
            Icon.Loaded(it.getDrawable(this), null)
        }
    } ?: Icon.Resource(R.drawable.ic_error_outline, null)
}

fun tileHorizontalArrangement(): Arrangement.Horizontal {
    return spacedBy(space = CommonTileDefaults.TileArrangementPadding, alignment = Alignment.Start)
}

@Composable
fun Modifier.tileCombinedClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    accessibilityUiState: AccessibilityUiState,
    interactionSource: MutableInteractionSource?,
    iconOnly: Boolean,
    isDualTarget: Boolean,
): Modifier {
    val longPressLabel =
        if (iconOnly && isDualTarget) longPressLabelMoreDetails() else longPressLabelSettings()
    return combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onClickLabel = accessibilityUiState.clickLabel,
            onLongClickLabel = longPressLabel,
            hapticFeedbackEnabled = !Flags.msdlFeedback(),
            interactionSource = interactionSource,
        )
        .semantics {
            val accessibilityRole =
                if (iconOnly && isDualTarget) {
                    Role.Switch
                } else {
                    accessibilityUiState.accessibilityRole
                }
            if (accessibilityRole == Role.Switch) {
                accessibilityUiState.toggleableState?.let { toggleableState = it }
            }
            role = accessibilityRole
            stateDescription = accessibilityUiState.stateDescription
        }
        .thenIf(iconOnly) {
            Modifier.semantics { contentDescription = accessibilityUiState.contentDescription }
        }
}

data class TileColors(
    val background: Color,
    val iconBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val icon: Color,
    val backgroundBrush: Brush? = null,
    val iconBackgroundBrush: Brush? = null,
    val useBolderLabel: Boolean = false,
)

@Composable
fun rememberTileShapeMode(): Int {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    fun readShapeMode(): Int {
        return try {
            Settings.System.getIntForUser(
                contentResolver, Settings.System.QS_TILE_SHAPE, 0,
                UserHandle.USER_CURRENT
            )
        } catch (_: Throwable) {
            0
        }
    }

    var shapeMode by remember { mutableIntStateOf(readShapeMode()) }

    DisposableEffect(contentResolver) {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                context.mainExecutor.execute {
                    shapeMode = readShapeMode()
                }
            }
        }

        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.QS_TILE_SHAPE),
            false, observer, UserHandle.USER_ALL
        )

        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    return shapeMode
}

@Composable
fun rememberQsGradientEnabled(): Boolean {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    fun readGradientEnabled(): Boolean {
        return try {
            Settings.System.getIntForUser(
                contentResolver, Settings.System.QS_TILE_GRADIENT_ENABLED, 1,
                UserHandle.USER_CURRENT
            ) == 1
        } catch (_: Throwable) {
            true
        }
    }

    var gradientEnabled by remember { mutableStateOf(readGradientEnabled()) }

    DisposableEffect(contentResolver) {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                context.mainExecutor.execute {
                    gradientEnabled = readGradientEnabled()
                }
            }
        }

        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.QS_TILE_GRADIENT_ENABLED),
            false, observer, UserHandle.USER_ALL
        )

        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    return gradientEnabled
}

/** Converts raw ARGB int from Settings to Compose Color. Forces full opacity when alpha is 0 (treats as 0x00RRGGBB). */
private fun gradientSettingArgbToColor(argb: Int): Color {
    val a = (argb shr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val alpha = if (a == 0) 1f else a / 255f
    return Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = alpha)
}

/** User-chosen gradient start/end when gradient is enabled (ColorPickerSystemPreference). Null = use default. */
@Composable
fun rememberQsGradientCustomColors(): Pair<Color?, Color?> {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    fun readStart(): Int {
        return try {
            Settings.System.getIntForUser(
                contentResolver, Settings.System.GRADIENT_START_COLOR, 0,
                UserHandle.USER_CURRENT
            )
        } catch (_: Throwable) {
            0
        }
    }
    fun readEnd(): Int {
        return try {
            Settings.System.getIntForUser(
                contentResolver, Settings.System.GRADIENT_END_COLOR, 0,
                UserHandle.USER_CURRENT
            )
        } catch (_: Throwable) {
            0
        }
    }

    var startArgb by remember { mutableIntStateOf(readStart()) }
    var endArgb by remember { mutableIntStateOf(readEnd()) }

    DisposableEffect(contentResolver) {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                context.mainExecutor.execute {
                    startArgb = readStart()
                    endArgb = readEnd()
                }
            }
        }
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.GRADIENT_START_COLOR),
            false, observer, UserHandle.USER_ALL
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.GRADIENT_END_COLOR),
            false, observer, UserHandle.USER_ALL
        )
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    return Pair(
        if (startArgb == 0) null else gradientSettingArgbToColor(startArgb),
        if (endArgb == 0) null else gradientSettingArgbToColor(endArgb)
    )
}

/**
 * When QS tile gradient is enabled, returns (brush, endColor) for use by the ringer slider thumb.
 * Returns null when gradient is disabled.
 */
@Composable
fun rememberQsTileGradientForRinger(): Pair<Brush?, Color?>? {
    if (!rememberQsGradientEnabled()) return null
    val (customStart, customEnd) = rememberQsGradientCustomColors()
    val context = LocalContext.current
    val resources = LocalResources.current
    val isDark = isSystemInDarkTheme()
    val defaultStart = remember(isDark, resources, context.theme) {
        val id =
            if (isDark) R.color.derpfestui_color_gradient_start_dark
            else R.color.derpfestui_color_gradient_start_light
        Color(resources.getColor(id, context.theme))
    }
    val defaultEnd = remember(isDark, resources, context.theme) {
        val id =
            if (isDark) R.color.derpfestui_color_gradient_end_dark
            else R.color.derpfestui_color_gradient_end_light
        Color(resources.getColor(id, context.theme))
    }
    val start = customStart ?: defaultStart
    val end = customEnd ?: defaultEnd
    return Pair(Brush.linearGradient(listOf(start, end)), end)
}

/** Whether to use accent-style tint for QS tiles (1 = enabled, 0 = default). Recomposes when setting changes. */
@Composable
private fun rememberQsUseNewTint(): Boolean {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    fun read(): Int =
        try {
            Settings.System.getIntForUser(
                contentResolver,
                Settings.System.QS_PANEL_BG_USE_NEW_TINT,
                0,
                UserHandle.USER_CURRENT
            )
        } catch (_: Throwable) {
            0
        }
    var value by remember { mutableIntStateOf(read()) }
    DisposableEffect(contentResolver) {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                context.mainExecutor.execute { value = read() }
            }
        }
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.QS_PANEL_BG_USE_NEW_TINT),
            false,
            observer,
            UserHandle.USER_ALL
        )
        onDispose { contentResolver.unregisterContentObserver(observer) }
    }
    return value == 1
}

private object TileDefaults {
    val ActiveIconCornerRadius = 16.dp

    /** Luminance-aware foreground so drawables/labels stay visible on gradient. When new tint is on and no gradient, use primary (accent). */
    @Composable
    private fun activeTileForegroundColor(): Color {
        val gradient = tileGradientBrushOrNull()
        val gradientEnd = rememberQsGradientEndColor()
        val useNewTint = rememberQsUseNewTint()
        val context = LocalContext.current
        return when {
            gradient != null && gradientEnd != null ->
                Color(BatteryColors.textColorOnBackground(context, gradientEnd.toArgb()))
            useNewTint -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onPrimary
        }
    }

    /** An active tile uses the active color as background. When new tint is on and no gradient, use primary at 20% for background. */
    @Composable
    fun activeTileColors(): TileColors {
        val gradient = tileGradientBrushOrNull()
        val useNewTint = rememberQsUseNewTint()
        val foreground = activeTileForegroundColor()
        val primary = MaterialTheme.colorScheme.primary
        val bg = when {
            gradient != null -> primary
            useNewTint -> primary.copy(alpha = 0.2f)
            else -> primary
        }
        val iconBg = when {
            gradient != null -> primary
            useNewTint -> primary.copy(alpha = 0.2f)
            else -> primary
        }
        return TileColors(
            background = bg,
            iconBackground = iconBg,
            label = foreground,
            secondaryLabel = foreground,
            icon = foreground,
            backgroundBrush = gradient,
            iconBackgroundBrush = gradient,
            useBolderLabel = useNewTint,
        )
    }

    /** An active tile with dual target only show the active color on the icon. When new tint and no gradient, icon uses primary at 20%. */
    @Composable
    fun activeDualTargetTileColors(): TileColors {
        val gradient = tileGradientBrushOrNull()
        val useNewTint = rememberQsUseNewTint()
        val iconForeground = activeTileForegroundColor()
        val primary = MaterialTheme.colorScheme.primary
        val iconBg = when {
            gradient != null -> primary
            useNewTint -> primary.copy(alpha = 0.2f)
            else -> primary
        }
        return TileColors(
            background = LocalAndroidColorScheme.current.surfaceEffect1,
            iconBackground = iconBg,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = iconForeground,
            iconBackgroundBrush = gradient,
            useBolderLabel = useNewTint,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun inactiveDualTargetTileColors(useNewTint: Boolean): TileColors =
        TileColors(
            background = LocalAndroidColorScheme.current.surfaceEffect1,
            iconBackground = LocalAndroidColorScheme.current.surfaceEffect2,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onSurface,
            useBolderLabel = useNewTint,
        )

    @Composable
    @ReadOnlyComposable
    fun inactiveTileColors(useNewTint: Boolean): TileColors =
        TileColors(
            background = LocalAndroidColorScheme.current.surfaceEffect1,
            iconBackground = Color.Transparent,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onSurface,
            useBolderLabel = useNewTint,
        )

    @Composable
    @ReadOnlyComposable
    fun unavailableTileColors(useNewTint: Boolean): TileColors {
        // When new tint is on, use same surface as inactive tiles so unavailable matches the panel.
        val surfaceColor = if (useNewTint) {
            LocalAndroidColorScheme.current.surfaceEffect1
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = .18f)
        }
        val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)
        return TileColors(
            background = surfaceColor,
            iconBackground = surfaceColor,
            label = onSurfaceVariantColor,
            secondaryLabel = onSurfaceVariantColor,
            icon = onSurfaceVariantColor,
        )
    }

    @Composable
    fun getColorForState(uiState: TileUiState, iconOnly: Boolean): TileColors {
        val useNewTint = rememberQsUseNewTint()
        return when (uiState.state) {
            STATE_ACTIVE -> {
                if (uiState.handlesSecondaryClick && !iconOnly) {
                    activeDualTargetTileColors()
                } else {
                    activeTileColors()
                }
            }

            STATE_INACTIVE -> {
                if (uiState.handlesSecondaryClick && !iconOnly) {
                    inactiveDualTargetTileColors(useNewTint)
                } else {
                    inactiveTileColors(useNewTint)
                }
            }

            else -> unavailableTileColors(useNewTint)
        }
    }

    @Composable
    fun animateIconShapeAsState(state: Int, shapeMode: Int): State<RoundedCornerShape> {
        return animateShapeAsState(
            state = state,
            activeCornerRadius = ActiveIconCornerRadius,
            label = "QSTileCornerRadius",
            shapeMode = shapeMode,
        )
    }

    @Composable
    fun animateTileShapeAsState(state: Int, shapeMode: Int): State<RoundedCornerShape> {
        return animateShapeAsState(
            state = state,
            activeCornerRadius = ActiveTileCornerRadius,
            label = "QSTileIconCornerRadius",
            shapeMode = shapeMode,
        )
    }

    @Composable
    fun animateShapeAsState(
        state: Int,
        activeCornerRadius: Dp,
        label: String,
        shapeMode: Int,
    ): State<RoundedCornerShape> {
        val animatedCornerRadius by
            animateDpAsState(
                targetValue = when (shapeMode) {
                        1 -> InactiveCornerRadius // Circle-ish
                        2 -> activeCornerRadius // Rounded Square
                        3 -> InactiveCornerRadius // Circle
                        else -> if (state == STATE_ACTIVE) activeCornerRadius else InactiveCornerRadius
                    },
                label = label,
            )

        return remember {
            val corner =
                object : CornerSize {
                    override fun toPx(shapeSize: Size, density: Density): Float {
                        return with(density) { animatedCornerRadius.toPx() }
                    }
                }
            mutableStateOf(RoundedCornerShape(corner))
        }
    }

    /** When gradient is enabled, returns the end color (for luminance-aware contrast). */
    @Composable
    private fun rememberQsGradientEndColor(): Color? {
        if (!rememberQsGradientEnabled()) return null
        val (customStart, customEnd) = rememberQsGradientCustomColors()
        val context = LocalContext.current
        val resources = LocalResources.current
        val isDark = isSystemInDarkTheme()
        val defaultEnd = remember(isDark, resources, context.theme) {
            val id = if (isDark) R.color.derpfestui_color_gradient_end_dark
                else R.color.derpfestui_color_gradient_end_light
            Color(resources.getColor(id, context.theme))
        }
        return customEnd ?: defaultEnd
    }

    @Composable
    private fun tileGradientBrushOrNull(): Brush? {
        val gradientEnabled = rememberQsGradientEnabled()
        if (!gradientEnabled) {
            return null
        }
        val (customStart, customEnd) = rememberQsGradientCustomColors()
        val context = LocalContext.current
        val resources = LocalResources.current
        val isDark = isSystemInDarkTheme()
        val defaultStart = remember(isDark, resources, context.theme) {
            val id = if (isDark) R.color.derpfestui_color_gradient_start_dark
                else R.color.derpfestui_color_gradient_start_light
            Color(resources.getColor(id, context.theme))
        }
        val defaultEnd = remember(isDark, resources, context.theme) {
            val id = if (isDark) R.color.derpfestui_color_gradient_end_dark
                else R.color.derpfestui_color_gradient_end_light
            Color(resources.getColor(id, context.theme))
        }
        val start = customStart ?: defaultStart
        val end = customEnd ?: defaultEnd
        return Brush.linearGradient(listOf(start, end))
    }
}

/**
 * A composable function that returns the [Resources]. It will be recomposed when [Configuration]
 * gets updated.
 */
@Composable
@ReadOnlyComposable
private fun resources(): Resources {
    LocalConfiguration.current
    return LocalResources.current
}

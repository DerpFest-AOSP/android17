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

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import com.android.compose.lifecycle.LaunchedEffectWithLifecycle
import com.android.compose.modifiers.thenIf
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.systemui.common.ui.icons.DragHandle
import com.android.systemui.qs.panels.ui.model.QsShadeComponent
import com.android.systemui.qs.panels.ui.model.QsSliderVisibility
import com.android.systemui.qs.panels.ui.viewmodel.EditModeLayoutTabViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Implementation of the [EditModeLayoutTabImpl] composer */
class EditModeLayoutTabImpl @Inject constructor() : EditModeLayoutTab {
    @Composable
    override fun Content(
        viewmodel: EditModeLayoutTabViewModel,
        brightness: @Composable () -> Unit,
        tilesGrid: @Composable () -> Unit,
        media: @Composable () -> Unit,
        volume: @Composable () -> Unit,
        brightnessVisibility: QsSliderVisibility,
        onBrightnessVisibilityChange: (QsSliderVisibility) -> Unit,
        isDualShade: Boolean,
        modifier: Modifier,
    ) {
        EditLayoutTabImpl(
            viewmodel = viewmodel,
            brightness = brightness,
            tilesGrid = tilesGrid,
            media = media,
            volume = volume,
            brightnessVisibility = brightnessVisibility,
            onBrightnessVisibilityChange = onBrightnessVisibilityChange,
            isDualShade = isDualShade,
            modifier = modifier,
        )
    }

    @Composable
    override fun DragShadow(
        viewmodel: EditModeLayoutTabViewModel,
        brightness: @Composable (() -> Unit),
        tilesGrid: @Composable (() -> Unit),
        media: @Composable (() -> Unit),
        volume: @Composable (() -> Unit),
        brightnessVisibility: QsSliderVisibility,
        isDualShade: Boolean,
        modifier: Modifier,
    ) {
        DragShadowImpl(
            viewmodel = viewmodel,
            brightness = brightness,
            tilesGrid = tilesGrid,
            media = media,
            volume = volume,
            brightnessVisibility = brightnessVisibility,
            isDualShade = isDualShade,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun EditLayoutTabImpl(
    viewmodel: EditModeLayoutTabViewModel,
    brightness: @Composable () -> Unit,
    tilesGrid: @Composable () -> Unit,
    media: @Composable () -> Unit,
    volume: @Composable () -> Unit,
    brightnessVisibility: QsSliderVisibility,
    onBrightnessVisibilityChange: (QsSliderVisibility) -> Unit,
    isDualShade: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var showVisibilitySheet by remember { mutableStateOf(false) }

    DragEventListener(listState, viewmodel)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            items(items = viewmodel.components, key = { it }) { component ->
                val isDragged by remember {
                    derivedStateOf { viewmodel.dragState?.component == component }
                }
                val contentAlpha by animateFloatAsState(if (isDragged) 0f else 1f)

                // Only show resizing handle when idle
                val showHandle by remember { derivedStateOf { viewmodel.dragState == null } }

                val itemInfo by remember {
                    derivedStateOf {
                        listState.layoutInfo.visibleItemsInfo.find { it.key == component }
                    }
                }

                DraggableContainer(
                    showBorder = !isDragged,
                    showHandle = showHandle,
                    isDraggingEnabled = true,
                    onDragStart = { itemInfo?.let { viewmodel.onDragStart(component, it.offset) } },
                    onDrag = { dragAmount ->
                        itemInfo?.let {
                            viewmodel.onDrag(
                                dragAmount = dragAmount.y.roundToInt(),
                                idleOffset = it.offset,
                            )
                        }
                    },
                    onDragEnd = { viewmodel.onDragEnd() },
                    modifier = Modifier.animateItem(),
                ) {
                    // While dragging, keep a same-type placeholder in the list slot so the real
                    // brightness/media content is only composed in DragShadow (avoids dual
                    // AndroidView / slider attachment crashes).
                    Box(Modifier.graphicsLayer { alpha = contentAlpha }) {
                        if (isDragged) {
                            PlaceholderComponent(component, brightnessVisibility, isDualShade)
                        } else {
                            Component(
                                component,
                                brightness,
                                tilesGrid,
                                media,
                                volume,
                                brightnessVisibility,
                                isDualShade,
                                onBrightnessRowClick = { showVisibilitySheet = true },
                            )
                        }
                    }
                }
            }
        }
        if (showVisibilitySheet) {
            BrightnessVisibilitySheet(
                visibility = brightnessVisibility,
                isDualShade = isDualShade,
                onVisibilityChange = onBrightnessVisibilityChange,
                onDismiss = { showVisibilitySheet = false },
            )
        }
    }
}

@Composable
private fun DragShadowImpl(
    viewmodel: EditModeLayoutTabViewModel,
    brightness: @Composable () -> Unit,
    tilesGrid: @Composable () -> Unit,
    media: @Composable () -> Unit,
    volume: @Composable () -> Unit,
    brightnessVisibility: QsSliderVisibility,
    isDualShade: Boolean,
    modifier: Modifier = Modifier,
) {
    val drag = updateTransition(viewmodel.dragState)
    val alpha by drag.animateFloat { if (it != null) 1f else 0f }
    val animatedOffset by drag.componentVerticalOffset()

    (drag.currentState ?: drag.targetState)?.let { dragState ->
        DraggableContainer(
            showBorder = true,
            showHandle = true,
            backgroundAlpha = DraggedContainerBackgroundAlpha,
            modifier =
                modifier.graphicsLayer {
                    val offsetInRoot = animatedOffset.toFloat()

                    translationY = offsetInRoot
                    this.alpha = alpha
                },
        ) {
            Component(
                dragState.component,
                brightness,
                tilesGrid,
                media,
                volume,
                brightnessVisibility,
                isDualShade,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalCoroutinesApi::class)
private fun DragEventListener(listState: LazyListState, viewmodel: EditModeLayoutTabViewModel) {
    LaunchedEffectWithLifecycle(listState, viewmodel) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .flatMapLatest { itemInfos ->
                snapshotFlow { viewmodel.dragState }
                    .filterNotNull()
                    .map { dragState ->
                        // Find the height of the dragged component and use the center of it as a
                        // threshold to trigger a move
                        val draggedComponentHeight =
                            itemInfos.find { it.key == dragState.component }?.size ?: 0
                        val offset = dragState.offset + draggedComponentHeight / 2

                        val target =
                            itemInfos.firstOrNull {
                                // Find first item that includes the offset
                                offset >= it.offset && offset < it.offset + it.size
                            }

                        // Returns the pair of source to target components
                        dragState.component to (target?.key as? QsShadeComponent)
                    }
            }
            .distinctUntilChanged()
            .collect { (source, target) -> viewmodel.onHover(source, target) }
    }
}

@Composable
private fun Component(
    component: QsShadeComponent,
    brightness: @Composable () -> Unit,
    tilesGrid: @Composable () -> Unit,
    media: @Composable () -> Unit,
    volume: @Composable () -> Unit,
    brightnessVisibility: QsSliderVisibility,
    isDualShade: Boolean,
    onBrightnessRowClick: (() -> Unit)? = null,
) {
    when (component) {
        QsShadeComponent.BRIGHTNESS ->
            BrightnessRow(
                visibility = brightnessVisibility,
                isDualShade = isDualShade,
                onClick = onBrightnessRowClick,
                content = brightness,
            )
        QsShadeComponent.VOLUME -> volume()
        QsShadeComponent.MEDIA -> media()
        QsShadeComponent.TILES_GRID -> tilesGrid()
    }
}

@Composable
private fun PlaceholderComponent(
    component: QsShadeComponent,
    brightnessVisibility: QsSliderVisibility,
    isDualShade: Boolean,
) {
    when (component) {
        QsShadeComponent.BRIGHTNESS ->
            BrightnessRow(
                visibility = brightnessVisibility,
                isDualShade = isDualShade,
                content = { EditModeLayoutTabDefaults.Brightness() },
            )
        QsShadeComponent.VOLUME -> EditModeLayoutTabDefaults.Volume()
        QsShadeComponent.MEDIA -> EditModeLayoutTabDefaults.Media()
        QsShadeComponent.TILES_GRID -> EditModeLayoutTabDefaults.TilesGrid()
    }
}

@Composable
private fun BrightnessRow(
    visibility: QsSliderVisibility,
    isDualShade: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val displayed = visibility.displayed(isDualShade)
    val hidden = displayed == QsSliderVisibility.HIDDEN
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(modifier) {
        Box(
            Modifier.graphicsLayer { alpha = if (hidden) HiddenBrightnessAlpha else 1f }
                .then(clickModifier)
        ) {
            content()
        }
        VisibilityChip(
            visibility = displayed,
            isDualShade = isDualShade,
            modifier =
                Modifier.align(Alignment.TopCenter).padding(top = 4.dp).zIndex(1f).then(clickModifier),
        )
    }
}

@Composable
private fun VisibilityChip(
    visibility: QsSliderVisibility,
    isDualShade: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = visibility.label(isDualShade, short = true),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun BrightnessVisibilitySheet(
    visibility: QsSliderVisibility,
    isDualShade: Boolean,
    onVisibilityChange: (QsSliderVisibility) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = visibility.displayed(isDualShade)
    val sheetTint = LocalAndroidColorScheme.current.surfaceEffect1.copy(alpha = 0.9f)
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        val view = LocalView.current
        val blurDrawable = rememberBackgroundBlurDrawable()
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.setDimAmount(0f)
        }

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .background(SheetScrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
            )
            Surface(
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape =
                    RoundedCornerShape(topStart = SheetCornerRadius, topEnd = SheetCornerRadius),
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .backgroundBlur(
                            blurDrawable,
                            sheetTint,
                            SheetBlurRadius,
                            SheetCornerRadius,
                        ),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 16.dp)
                            .selectableGroup(),
                ) {
                    Box(
                        Modifier.padding(top = 12.dp, bottom = 16.dp)
                            .width(32.dp)
                            .height(4.dp)
                            .drawBehind {
                                drawRoundRect(
                                    color = handleColor,
                                    cornerRadius = CornerRadius(size.minDimension / 2),
                                )
                            }
                    )
                    Text(
                        text = brightnessVisibilityTitle(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    )
                    QsSliderVisibility.available(isDualShade).forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .selectable(
                                        selected = option == selected,
                                        onClick = {
                                            onVisibilityChange(option)
                                            onDismiss()
                                        },
                                        role = Role.RadioButton,
                                    )
                                    .padding(vertical = 12.dp),
                        ) {
                            RadioButton(selected = option == selected, onClick = null)
                            Text(
                                text = option.label(isDualShade),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberBackgroundBlurDrawable(): BackgroundBlurDrawable? {
    val view = LocalView.current
    val drawable = remember(view) { view.viewRootImpl?.createBackgroundBlurDrawable() }
    DisposableEffect(drawable) { onDispose { drawable?.setBlurRadius(0) } }
    return drawable
}

private fun Modifier.backgroundBlur(
    drawable: BackgroundBlurDrawable?,
    tint: Color,
    blurRadius: Dp,
    cornerRadius: Dp,
): Modifier {
    if (drawable == null) {
        return drawBehind {
            drawRoundRect(color = tint, cornerRadius = CornerRadius(cornerRadius.toPx()))
        }
    }
    return drawBehind {
        drawable.setBlurRadius(blurRadius.roundToPx())
        drawable.setCornerRadius(cornerRadius.toPx())
        drawable.setColor(tint.toArgb())
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawIntoCanvas { canvas -> drawable.draw(canvas.nativeCanvas) }
    }
}

@Composable
private fun DraggableContainer(
    showBorder: Boolean,
    showHandle: Boolean,
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = ContainerBackgroundAlpha,
    isDraggingEnabled: Boolean = false,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDrag: (dragAmount: Offset) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    val container: @Composable () -> Unit =
        @Composable {
            Container(
                color = primaryColor,
                showBorder = showBorder,
                backgroundAlpha = backgroundAlpha,
                modifier =
                    Modifier.thenIf(isDraggingEnabled) {
                        Modifier.pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDrag = { _, dragAmount -> onDrag(dragAmount) },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                            )
                        }
                    },
                content = content,
            )
        }

    val handle: @Composable () -> Unit =
        @Composable {
            Handle(
                color = primaryColor,
                showHandle = showHandle,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier.thenIf(isDraggingEnabled) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { _, dragAmount -> onDrag(dragAmount) },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                            )
                        }
                    },
            )
        }

    // The draggable handle should be centered on the bottom border of the container.
    // This is done by calculating the total height of the layout as the height of the container +
    // half the height of the handle. The handle is then placed at the bottom, centered horizontally
    Layout(contents = listOf(container, handle), modifier = modifier) { measurables, constraints ->
        check(measurables.size == 2) { "The list passed to Layout.contents should have 2 contents" }
        check(measurables[0].size == 1) { "The container should emit exactly one node" }
        check(measurables[1].size == 1) { "The handle should emit exactly one node" }

        val contentPlaceable = measurables[0][0].measure(constraints)
        val handlePlaceable = measurables[1][0].measure(constraints)
        val width = contentPlaceable.width
        val height = contentPlaceable.height + handlePlaceable.height / 2
        layout(width, height) {
            contentPlaceable.placeRelative(0, 0)
            handlePlaceable.placeRelative(
                (width - handlePlaceable.width) / 2,
                height - handlePlaceable.height,
            )
        }
    }
}

@Composable
private fun Container(
    color: Color,
    showBorder: Boolean,
    backgroundAlpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val borderAlpha by animateFloatAsState(if (showBorder) 1f else 0f)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        color,
                        cornerRadius = CornerRadius(ContainerGridRadiusDp.toPx()),
                        alpha = backgroundAlpha,
                    )

                    // Offset the border by half the width of the stroke to keep it from
                    // drawing
                    // outside the composable's bounds
                    val offsetPx = BorderWidth.toPx()
                    drawRoundRect(
                        size = Size(size.width - offsetPx, size.height - offsetPx),
                        topLeft = Offset(offsetPx / 2, offsetPx / 2),
                        color = color,
                        alpha = borderAlpha,
                        cornerRadius = CornerRadius(ContainerGridRadiusDp.toPx()),
                        style = Stroke(width = BorderWidth.toPx()),
                    )
                }
                .padding(8.dp),
        content = content,
    )
}

@Composable
private fun Handle(
    color: Color,
    showHandle: Boolean,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val handleAlpha by animateFloatAsState(if (showHandle) 1f else 0f)
    Box(
        modifier
            .graphicsLayer { alpha = handleAlpha }
            .width(40.dp)
            .height(16.dp)
            .drawBehind {
                drawRoundRect(color = color, cornerRadius = CornerRadius(size.minDimension / 2))
            }
    ) {
        Icon(
            DragHandle,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * Returns the vertical offset in pixels for the dragged component.
 *
 * When a drag is active, it returns the manual offset from the gesture. When a drag is released, it
 * animates from the last manual offset to the idle position.
 *
 * Important: This offset is relative to the layout tab
 */
@Composable
private fun Transition<EditModeLayoutTabViewModel.DragState?>.componentVerticalOffset():
    State<Int> {
    val animatable = remember { Animatable(0, Int.VectorConverter) }
    val target = targetState
    LaunchedEffect(target) {
        val currentDragState = currentState

        // When a drag is still active, snap to the offset
        if (target != null) {
            animatable.snapTo(target.offset)
        } else if (currentDragState != null) {
            // When the drag is released, snap to the last offset and animate to the idle position
            animatable.snapTo(currentDragState.offset)
            animatable.animateTo(currentDragState.idleOffset)
        }
    }
    return animatable.asState()
}

private val ContainerGridRadiusDp = 28.dp
private const val ContainerBackgroundAlpha = .15f
private const val DraggedContainerBackgroundAlpha = .5f
private const val HiddenBrightnessAlpha = .4f
private val BorderWidth = 2.dp
private val SheetCornerRadius = ContainerGridRadiusDp
private val SheetBlurRadius = 30.dp
private val SheetScrimColor = Color.Black.copy(alpha = 0.24f)

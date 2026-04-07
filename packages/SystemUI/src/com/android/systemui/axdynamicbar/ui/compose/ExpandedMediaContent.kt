package com.android.systemui.axdynamicbar.ui.compose

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.widget.SeekBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.systemui.axdynamicbar.shared.IslandActions
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.*
import com.android.systemui.media.controls.ui.drawable.SquigglyProgress
import com.android.systemui.res.R
import kotlinx.coroutines.delay

private val AlbumArtSize = 80.dp
private val PlayPauseSize = 56.dp
private val ControlButtonSize = 44.dp
private val ControlIconSize = 22.dp
private val SeekBarHeight = 28.dp
private val MediaPopupCardShape = RoundedCornerShape(24.dp)
private val CinematicArtBoxSize = 58.dp
private val CinematicArtInnerRadius = RoundedCornerShape(10.dp)

@Composable
internal fun MediaCard(event: IslandEvent.Media, interactor: IslandActions) {
    val colors = rememberMediaColors(event)
    val accent = colors.accent
    val hasArt = event.albumArt != null
    val cardBgBase = MaterialTheme.colorScheme.surfaceVariant
    val onCard = Color.White
    val onCardSub = onCard.copy(alpha = 0.55f)

    val blurEffect =
        remember {
            if (Build.VERSION.SDK_INT >= 31) {
                RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.MIRROR).asComposeRenderEffect()
            } else {
                null
            }
        }

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .wrapContentHeight()
                .shadow(20.dp, MediaPopupCardShape)
                .clip(MediaPopupCardShape),
    ) {
        if (hasArt) {
            Image(
                bitmap = event.albumArt!!.toScaledBitmap(240.dp),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.matchParentSize().graphicsLayer {
                        if (blurEffect != null) {
                            renderEffect = blurEffect
                        }
                        scaleX = 1.15f
                        scaleY = 1.15f
                    },
            )
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.52f)))
        } else {
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                cardBgBase,
                                cardBgBase.copy(alpha = 0.80f),
                            ),
                    ),
                ),
            )
        }

        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to onCard.copy(alpha = 0.06f),
                    0.35f to Color.Transparent,
                    1f to Color.Transparent,
                ),
            ),
        )

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(CinematicArtBoxSize)
                        .clip(CinematicArtInnerRadius)
                        .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    event.albumArt != null ->
                        Image(
                            bitmap = event.albumArt!!.toScaledBitmap(CinematicArtBoxSize),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CinematicArtInnerRadius),
                        )
                    event.appIcon != null ->
                        Image(
                            bitmap = event.appIcon!!.toScaledBitmap(36.dp),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    else ->
                        Icon(
                            Icons.Filled.MusicNote,
                            null,
                            tint = onCard.copy(alpha = 0.50f),
                            modifier = Modifier.size(26.dp),
                        )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            interactor.openMediaApp()
                            interactor.collapseIsland()
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = event.track.ifEmpty { stringResource(R.string.ax_dynamic_bar_now_playing) },
                        style =
                            TextStyle(
                                color = onCard,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp,
                            ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (event.artist.isNotEmpty()) {
                        Text(
                            text = event.artist,
                            style =
                                TextStyle(
                                    color = onCardSub,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (event.duration > 0L) {
                    Spacer(Modifier.height(6.dp))
                    MediaSeekBar(
                        event = event,
                        interactor = interactor,
                        accent = accent,
                        cinematic = true,
                    )
                }

                Spacer(Modifier.height(10.dp))
                MediaControls(
                    event = event,
                    interactor = interactor,
                    accent = accent,
                    cinematic = true,
                )
            }
        }
    }
}

@Composable
internal fun MediaExpanded(
    event: IslandEvent.Media,
    interactor: IslandActions,
    modifier: Modifier = Modifier,
) {
    val colors = rememberMediaColors(event)
    val accent = colors.accent

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SpaceXxl)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                interactor.openMediaApp()
                interactor.collapseIsland()
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceXxl),
        ) {
            event.albumArt?.let { art ->
                Image(
                    bitmap = art.toScaledBitmap(SizeAlbumSm),
                    contentDescription = null,
                    modifier = Modifier.size(SizeAlbumSm).clip(ShapeLg),
                    contentScale = ContentScale.Crop,
                )
            } ?: Surface(
                modifier = Modifier.size(SizeAlbumSm),
                shape = ShapeLg,
                color = accent.copy(alpha = AlphaSubtle),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.MusicNote, null,
                        tint = accent,
                        modifier = Modifier.size(SpacePanel),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceXs),
            ) {
                Text(
                    event.track.ifEmpty { stringResource(R.string.ax_dynamic_bar_now_playing) },
                    color = OnCardText,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.artist.isNotEmpty()) {
                    Text(
                        event.artist,
                        color = accent,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            event.appIcon?.let { icon ->
                Image(
                    bitmap = icon.toScaledBitmap(SizeIconSm),
                    contentDescription = null,
                    modifier = Modifier.size(SizeIconSm).clip(ShapeXs),
                    colorFilter = ColorFilter.tint(OnCardText),
                )
            }
        }

        MediaControls(event, interactor, accent)
        if (event.duration > 0L) {
            MediaSeekBar(event, interactor, accent)
        }
    }
}

@Composable
private fun MediaControls(
    event: IslandEvent.Media,
    interactor: IslandActions,
    accent: Color,
    cinematic: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (cinematic) {
        val onCard = Color.White
        val playSurface = onCard.copy(alpha = 0.15f)
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (event.customActions.isNotEmpty()) {
                val ca = event.customActions.first()
                Box(
                    modifier =
                        Modifier.size(36.dp)
                            .clip(CircleShape)
                            .clickable { interactor.sendCustomAction(ca.action) },
                    contentAlignment = Alignment.Center,
                ) {
                    CustomActionIcon(
                        ca = ca,
                        tint = onCard,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Shuffle,
                        null,
                        tint = onCard.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box(
                modifier =
                    Modifier.size(36.dp)
                        .clip(CircleShape)
                        .clickable { interactor.skipPrev() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    null,
                    tint = onCard,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                modifier =
                    Modifier.width(64.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(44.dp))
                        .background(playSurface)
                        .clickable { interactor.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (event.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (event.isPlaying)
                        stringResource(R.string.ax_dynamic_bar_pause)
                    else
                        stringResource(R.string.ax_dynamic_bar_play),
                    tint = onCard,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box(
                modifier =
                    Modifier.size(36.dp)
                        .clip(CircleShape)
                        .clickable { interactor.skipNext() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    null,
                    tint = onCard,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (event.customActions.size > 1) {
                val ca = event.customActions[1]
                Box(
                    modifier =
                        Modifier.size(36.dp)
                            .clip(CircleShape)
                            .clickable { interactor.sendCustomAction(ca.action) },
                    contentAlignment = Alignment.Center,
                ) {
                    CustomActionIcon(
                        ca = ca,
                        tint = onCard,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier.size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                interactor.openMediaOutputSwitcher()
                                interactor.collapseIsland()
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        null,
                        tint = onCard,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        return
    }

    val onAccent = chipContentColorOn(accent)
    val tonalBg = accent.copy(alpha = AlphaSubtle)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaCustomActionButton(event, interactor, accent, tonalBg)

        Surface(
            onClick = { interactor.skipPrev() },
            shape = CircleShape,
            color = tonalBg,
            modifier = Modifier.size(ControlButtonSize),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.SkipPrevious, null,
                    tint = accent,
                    modifier = Modifier.size(ControlIconSize),
                )
            }
        }

        Surface(
            onClick = { interactor.togglePlayPause() },
            shape = CircleShape,
            color = accent,
            modifier = Modifier.size(PlayPauseSize),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    if (event.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (event.isPlaying)
                        stringResource(R.string.ax_dynamic_bar_pause)
                    else
                        stringResource(R.string.ax_dynamic_bar_play),
                    tint = onAccent,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Surface(
            onClick = { interactor.skipNext() },
            shape = CircleShape,
            color = tonalBg,
            modifier = Modifier.size(ControlButtonSize),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.SkipNext, null,
                    tint = accent,
                    modifier = Modifier.size(ControlIconSize),
                )
            }
        }

        MediaEndActionButton(event, interactor, accent, tonalBg)
    }
}

@Composable
private fun MediaSeekBar(
    event: IslandEvent.Media,
    interactor: IslandActions,
    accent: Color,
    cinematic: Boolean = false,
) {
    val mediaProgress = rememberMediaProgress(event)
    val isPlaying = event.isPlaying
    val durationMs = event.duration
    val positionMs = mediaProgress.positionMs
    val serverFraction = mediaProgress.progress

    var isScrubbing by remember { mutableStateOf(false) }
    var displayFraction by remember { mutableStateOf(serverFraction) }

    val interactorRef = rememberUpdatedState(interactor)

    // Read the dismiss swipe lock provided by MagneticSwipeToDismiss
    val swipeLock = LocalDismissSwipeLock.current

    // Smooth frame-interpolated progress when playing, snaps when paused or scrubbing
    LaunchedEffect(positionMs, durationMs, isPlaying) {
        if (isScrubbing) return@LaunchedEffect

        displayFraction = serverFraction

        if (!isPlaying || durationMs <= 0L) return@LaunchedEffect

        val startWallMs = System.currentTimeMillis()
        val startProgressMs = positionMs
        while (true) {
            delay(16L) // ~60 fps
            if (isScrubbing) break
            val elapsed = System.currentTimeMillis() - startWallMs
            val interpolated = ((startProgressMs + elapsed).toFloat() / durationMs).coerceIn(0f, 1f)
            displayFraction = interpolated
            if (interpolated >= 1f) break
        }
    }

    val displayMs = (displayFraction * durationMs).toLong()
    val whiteArgb = android.graphics.Color.WHITE
    val accentArgb = if (cinematic) whiteArgb else accent.toArgb()
    val trackAlphaArgb =
        if (cinematic) {
            com.android.internal.graphics.ColorUtils.setAlphaComponent(whiteArgb, 90)
        } else {
            accent.copy(alpha = AlphaSubtle).toArgb()
        }
    val secondaryProgressArgb =
        if (cinematic) {
            com.android.internal.graphics.ColorUtils.setAlphaComponent(whiteArgb, 60)
        } else {
            com.android.internal.graphics.ColorUtils.setAlphaComponent(accent.toArgb(), 60)
        }

    val labelColor = if (cinematic) Color.White.copy(alpha = 0.55f) else SubtleGray
    val timeStyle =
        if (cinematic) {
            TextStyle(
                color = labelColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            MaterialTheme.typography.labelSmall.copy(color = labelColor)
        }

    Column(verticalArrangement = Arrangement.spacedBy(SpaceXs)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (cinematic) 4.dp else 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatElapsedTime(displayMs),
                color = labelColor,
                style = timeStyle,
            )
            Text(
                text =
                    if (cinematic && durationMs > 0) {
                        "-${formatElapsedTime((durationMs - displayMs).coerceAtLeast(0))}"
                    } else {
                        formatElapsedTime(durationMs)
                    },
                color = labelColor,
                style = timeStyle,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SeekBarHeight)
                .pointerInput(swipeLock) {
                    awaitEachGesture {
                        awaitPointerEvent() // DOWN
                        swipeLock.value = true
                        try {
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                        } finally {
                            swipeLock.value = false
                        }
                    }
                }
                .pointerInput("tap") {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        displayFraction = fraction
                        interactorRef.value.seekTo((fraction * durationMs).toLong())
                    }
                }
                .pointerInput("drag") {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isScrubbing = true
                            displayFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            interactorRef.value.seekTo((displayFraction * durationMs).toLong())
                            isScrubbing = false
                        },
                        onDragCancel = { isScrubbing = false },
                        onHorizontalDrag = { change, _ ->
                            displayFraction =
                                (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            change.consume()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    SeekBar(context).apply {
                        max = 10_000
                        splitTrack = false
                        setPadding(0, 0, 0, 0)
                        // Disable direct touch — Compose handles all gestures above
                        isEnabled = false

                        // Pill-shaped thumb
                        thumb = createSeekBarThumb(context, accentArgb)
                        thumbOffset = thumb.intrinsicWidth / 2

                        // Set up SquigglyProgress on the progress layer
                        val layer = (progressDrawable?.mutate() as? LayerDrawable)
                        if (layer != null) {
                            layer.findDrawableByLayerId(android.R.id.background)
                                ?.mutate()?.setTint(trackAlphaArgb)

                            layer.findDrawableByLayerId(android.R.id.secondaryProgress)
                                ?.mutate()?.setTint(secondaryProgressArgb)

                            val squiggle = SquigglyProgress().apply {
                                waveLength = context.resources.getDimensionPixelSize(
                                    R.dimen.qs_media_seekbar_progress_wavelength
                                ).toFloat()
                                lineAmplitude = context.resources.getDimensionPixelSize(
                                    R.dimen.qs_media_seekbar_progress_amplitude
                                ).toFloat()
                                phaseSpeed = context.resources.getDimensionPixelSize(
                                    R.dimen.qs_media_seekbar_progress_phase
                                ).toFloat()
                                strokeWidth = context.resources.getDimensionPixelSize(
                                    R.dimen.qs_media_seekbar_progress_stroke_width
                                ).toFloat()
                                setTint(accentArgb)
                                drawRemainingLine = false
                                transitionEnabled = false
                                animate = false
                            }
                            layer.setDrawableByLayerId(android.R.id.progress, squiggle)
                            progressDrawable = layer
                        }
                    }
                },
                update = { bar ->
                    val target = (displayFraction * 10_000f).toInt().coerceIn(0, 10_000)
                    bar.progress = target

                    // Re-tint thumb for accent color changes (e.g. track switch)
                    (bar.thumb as? GradientDrawable)?.setColor(accentArgb)

                    val alpha = if (isPlaying) 255 else (255 * 0.55f).toInt()
                    bar.thumb?.alpha = alpha

                    val layer = bar.progressDrawable as? LayerDrawable

                    // Re-tint track colors
                    layer?.findDrawableByLayerId(android.R.id.background)
                        ?.setTint(trackAlphaArgb)
                    layer?.findDrawableByLayerId(android.R.id.secondaryProgress)
                        ?.setTint(secondaryProgressArgb)

                    val squiggle = layer
                        ?.findDrawableByLayerId(android.R.id.progress) as? SquigglyProgress

                    squiggle?.apply {
                        setTint(accentArgb)
                        setAlpha(alpha)
                        animate = isPlaying && !isScrubbing
                    }

                    layer?.alpha = alpha
                },
                modifier = Modifier.fillMaxWidth().height(SeekBarHeight),
            )
        }
    }
}

/**
 * Creates a pill-shaped thumb drawable for the seekbar.
 */
private fun createSeekBarThumb(context: android.content.Context, tintColor: Int): GradientDrawable {
    val wPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics
    ).toInt().coerceAtLeast(1)
    val hPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 16f, context.resources.displayMetrics
    ).toInt().coerceAtLeast(1)
    val radiusPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 16f, context.resources.displayMetrics
    )
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setSize(wPx, hPx)
        cornerRadius = radiusPx
        setColor(tintColor)
    }
}

@Composable
private fun MediaCustomActionButton(
    event: IslandEvent.Media,
    interactor: IslandActions,
    accent: Color,
    tonalBg: Color,
) {
    if (event.customActions.isNotEmpty()) {
        val ca = event.customActions.first()
        Surface(
            onClick = { interactor.sendCustomAction(ca.action) },
            shape = CircleShape,
            color = tonalBg,
            modifier = Modifier.size(ControlButtonSize),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CustomActionIcon(ca, tint = accent, modifier = Modifier.size(ControlIconSize))
            }
        }
    } else {
        Surface(
            onClick = { },
            shape = CircleShape,
            color = tonalBg.copy(alpha = AlphaSubtle),
            modifier = Modifier.size(ControlButtonSize),
            enabled = false,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.Shuffle, null,
                    tint = accent.copy(alpha = AlphaDisabled),
                    modifier = Modifier.size(ControlIconSize),
                )
            }
        }
    }
}

@Composable
private fun MediaEndActionButton(
    event: IslandEvent.Media,
    interactor: IslandActions,
    accent: Color,
    tonalBg: Color,
) {
    if (event.customActions.size > 1) {
        val ca = event.customActions[1]
        Surface(
            onClick = { interactor.sendCustomAction(ca.action) },
            shape = CircleShape,
            color = tonalBg,
            modifier = Modifier.size(ControlButtonSize),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CustomActionIcon(ca, tint = accent, modifier = Modifier.size(ControlIconSize))
            }
        }
    } else {
        Surface(
            onClick = {
                interactor.openMediaOutputSwitcher()
                interactor.collapseIsland()
            },
            shape = CircleShape,
            color = tonalBg,
            modifier = Modifier.size(ControlButtonSize),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.VolumeUp, null,
                    tint = accent,
                    modifier = Modifier.size(ControlIconSize),
                )
            }
        }
    }
}

@Composable
internal fun RowScope.CompactMediaRow(
    event: IslandEvent.Media,
    interactor: IslandActions,
) {
    event.albumArt?.let {
        Image(
            bitmap = it.toScaledBitmap(SizeCompactIcon),
            null,
            modifier = Modifier.size(SizeCompactIcon).clip(ShapeCompact),
            contentScale = ContentScale.Crop,
        )
    } ?: run {
        val accent = rememberMediaColors(event).accent
        Box(
            modifier = Modifier.size(SizeCompactIcon)
                .clip(ShapeCompact)
                .background(accent.copy(alpha = AlphaIconBg)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
    Spacer(Modifier.width(SpaceLg))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpaceXxs)) {
        Text(
            event.track.ifEmpty { stringResource(R.string.ax_dynamic_bar_music) },
            color = OnCardText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (event.artist.isNotEmpty())
            Text(
                event.artist,
                color = SubtleGray,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
    }
    Spacer(Modifier.width(SpaceMd))
    Surface(
        onClick = { interactor.togglePlayPause() },
        shape = CircleShape,
        color = ActionBg,
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (event.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                null,
                tint = OnActionText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.systemui.qs.panels.ui.model.QsShadeComponent

/** Lays out brightness, volume, tiles, and media according to [components]. */
@Composable
fun QsShadeComponentsColumn(
    components: List<QsShadeComponent>,
    brightness: @Composable () -> Unit,
    tiles: @Composable () -> Unit,
    media: @Composable () -> Unit,
    mediaInRow: Boolean,
    verticalArrangement: Arrangement.Vertical,
    horizontalArrangement: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    volume: @Composable () -> Unit = {},
) {
    Column(
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        modifier = modifier,
    ) {
        if (mediaInRow) {
            val firstOfPair =
                components.firstOrNull {
                    it == QsShadeComponent.TILES_GRID || it == QsShadeComponent.MEDIA
                }
            val tilesMediaRow = @Composable {
                Row(
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        key(QsShadeComponent.TILES_GRID) { tiles() }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        key(QsShadeComponent.MEDIA) { media() }
                    }
                }
            }
            components.forEach { component ->
                when (component) {
                    QsShadeComponent.TILES_GRID,
                    QsShadeComponent.MEDIA -> {
                        if (component == firstOfPair) {
                            tilesMediaRow()
                        }
                    }
                    QsShadeComponent.BRIGHTNESS -> {
                        key(QsShadeComponent.BRIGHTNESS) { brightness() }
                    }
                    QsShadeComponent.VOLUME -> {
                        key(QsShadeComponent.VOLUME) { volume() }
                    }
                }
            }
        } else {
            components.forEach { component ->
                key(component) {
                    when (component) {
                        QsShadeComponent.BRIGHTNESS -> brightness()
                        QsShadeComponent.VOLUME -> volume()
                        QsShadeComponent.TILES_GRID -> tiles()
                        QsShadeComponent.MEDIA -> media()
                    }
                }
            }
        }
    }
}

/** Lays out brightness, tiles, and media according to [components] (volume is omitted in QQS). */
@Composable
fun QqsShadeComponentsLayout(
    components: List<QsShadeComponent>,
    tiles: @Composable () -> Unit,
    media: @Composable () -> Unit,
    mediaInRow: Boolean,
    verticalArrangement: Arrangement.Vertical,
    horizontalArrangement: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
    brightness: @Composable () -> Unit = {},
) {
    val firstOfTilesMedia =
        components.firstOrNull {
            it == QsShadeComponent.TILES_GRID || it == QsShadeComponent.MEDIA
        }
    if (mediaInRow) {
        val tilesMediaRow = @Composable {
            Row(
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    key(QsShadeComponent.TILES_GRID) { tiles() }
                }
                Box(modifier = Modifier.weight(1f)) { key(QsShadeComponent.MEDIA) { media() } }
            }
        }
        Column(verticalArrangement = verticalArrangement, modifier = modifier) {
            components.forEach { component ->
                when (component) {
                    QsShadeComponent.BRIGHTNESS -> {
                        key(QsShadeComponent.BRIGHTNESS) { brightness() }
                    }
                    QsShadeComponent.VOLUME -> {}
                    QsShadeComponent.TILES_GRID,
                    QsShadeComponent.MEDIA -> {
                        if (component == firstOfTilesMedia) {
                            tilesMediaRow()
                        }
                    }
                }
            }
        }
    } else {
        Column(verticalArrangement = verticalArrangement, modifier = modifier) {
            components.forEach { component ->
                key(component) {
                    when (component) {
                        QsShadeComponent.BRIGHTNESS -> brightness()
                        QsShadeComponent.VOLUME -> {}
                        QsShadeComponent.TILES_GRID -> tiles()
                        QsShadeComponent.MEDIA -> media()
                    }
                }
            }
        }
    }
}

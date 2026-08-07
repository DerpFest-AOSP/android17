/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.systemui.qs.panels.ui.model.QsShadeComponent

/** Lays out brightness, tiles, and media according to [components]. */
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
) {
    Column(
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        modifier = modifier,
    ) {
        if (mediaInRow) {
            val brightnessIndex = components.indexOf(QsShadeComponent.BRIGHTNESS)
            val pairIndex =
                minOf(
                    components.indexOf(QsShadeComponent.TILES_GRID),
                    components.indexOf(QsShadeComponent.MEDIA),
                )
            val tilesMediaRow = @Composable {
                Row(
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) { tiles() }
                    Box(modifier = Modifier.weight(1f)) { media() }
                }
            }
            if (brightnessIndex <= pairIndex) {
                brightness()
                tilesMediaRow()
            } else {
                tilesMediaRow()
                brightness()
            }
        } else {
            components.forEach { component ->
                when (component) {
                    QsShadeComponent.BRIGHTNESS -> brightness()
                    QsShadeComponent.TILES_GRID -> tiles()
                    QsShadeComponent.MEDIA -> media()
                }
            }
        }
    }
}

/** Lays out QQS tiles and media according to [components] (brightness is omitted in QQS). */
@Composable
fun QqsShadeComponentsLayout(
    components: List<QsShadeComponent>,
    tiles: @Composable () -> Unit,
    media: @Composable () -> Unit,
    mediaInRow: Boolean,
    verticalArrangement: Arrangement.Vertical,
    horizontalArrangement: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
) {
    val mediaFirst =
        components.indexOf(QsShadeComponent.MEDIA) <
            components.indexOf(QsShadeComponent.TILES_GRID)
    if (mediaInRow) {
        Row(
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) {
            if (mediaFirst) {
                Box(modifier = Modifier.weight(1f)) { media() }
                Box(modifier = Modifier.weight(1f)) { tiles() }
            } else {
                Box(modifier = Modifier.weight(1f)) { tiles() }
                Box(modifier = Modifier.weight(1f)) { media() }
            }
        }
    } else {
        Column(verticalArrangement = verticalArrangement, modifier = modifier) {
            if (mediaFirst) {
                media()
                tiles()
            } else {
                tiles()
                media()
            }
        }
    }
}

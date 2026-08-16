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

package com.android.systemui.qs.panels.ui.model

/** Represents a movable part of the QS shade. */
enum class QsShadeComponent {
    BRIGHTNESS,
    VOLUME,
    TILES_GRID,
    MEDIA;

    companion object {
        /** Default top-to-bottom order for QS components. */
        @JvmField
        val DEFAULT_ORDER: List<QsShadeComponent> = listOf(BRIGHTNESS, VOLUME, TILES_GRID, MEDIA)

        private const val DELIMITER = ","

        /** Serializes [components] for SharedPreferences storage. */
        fun serialize(components: List<QsShadeComponent>): String =
            components.joinToString(DELIMITER) { it.name }

        /**
         * Parses a stored order string. Returns [DEFAULT_ORDER] when the value is missing, unknown,
         * incomplete, or contains duplicates.
         *
         * Stored orders from before [VOLUME] was added keep their relative order and insert volume
         * immediately after brightness.
         */
        fun parse(serialized: String?): List<QsShadeComponent> {
            if (serialized.isNullOrBlank()) return DEFAULT_ORDER
            val parsed =
                serialized.split(DELIMITER).mapNotNull { name ->
                    entries.firstOrNull { it.name == name }
                }
            if (parsed.toSet() == entries.toSet() && parsed.size == entries.size) {
                return parsed
            }
            return mergeMissingComponents(parsed.distinct())
        }

        private fun mergeMissingComponents(parsed: List<QsShadeComponent>): List<QsShadeComponent> {
            if (parsed.isEmpty()) return DEFAULT_ORDER
            val result = parsed.toMutableList()
            for (component in entries) {
                if (component in result) continue
                val insertAt =
                    if (component == VOLUME && BRIGHTNESS in result) {
                        result.indexOf(BRIGHTNESS) + 1
                    } else {
                        val defaultIndex = DEFAULT_ORDER.indexOf(component)
                        result
                            .indexOfFirst { DEFAULT_ORDER.indexOf(it) > defaultIndex }
                            .let { index -> if (index == -1) result.size else index }
                    }
                result.add(insertAt, component)
            }
            return if (result.toSet() == entries.toSet() && result.size == entries.size) {
                result
            } else {
                DEFAULT_ORDER
            }
        }
    }
}

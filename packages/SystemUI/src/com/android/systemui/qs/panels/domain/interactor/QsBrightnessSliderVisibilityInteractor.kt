/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.panels.domain.interactor

import android.content.Context
import android.database.ContentObserver
import android.os.UserHandle
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.panels.ui.model.QsSliderVisibility
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import lineageos.providers.LineageSettings

/** Reads and writes when the QS brightness slider should be shown. */
interface QsBrightnessSliderVisibilityInteractor {
    val visibility: StateFlow<QsSliderVisibility>

    fun setVisibility(visibility: QsSliderVisibility)
}

@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class QsBrightnessSliderVisibilityInteractorImpl
@Inject
constructor(
    @Application private val context: Context,
    private val userRepository: UserRepository,
    @Background backgroundDispatcher: CoroutineDispatcher,
    @Background backgroundScope: CoroutineScope,
) : QsBrightnessSliderVisibilityInteractor {

    override val visibility: StateFlow<QsSliderVisibility> =
        userRepository.selectedUserInfo
            .flatMapLatest { user ->
                conflatedCallbackFlow {
                    val observer =
                        object : ContentObserver(null) {
                            override fun onChange(selfChange: Boolean) {
                                trySend(read(user.id))
                            }
                        }
                    trySend(read(user.id))
                    context.contentResolver.registerContentObserver(
                        LineageSettings.Secure.getUriFor(SETTING),
                        false,
                        observer,
                        UserHandle.USER_ALL,
                    )
                    awaitClose { context.contentResolver.unregisterContentObserver(observer) }
                }
            }
            .flowOn(backgroundDispatcher)
            .stateIn(backgroundScope, SharingStarted.Eagerly, QsSliderVisibility.EXPANDED)

    override fun setVisibility(visibility: QsSliderVisibility) {
        LineageSettings.Secure.putIntForUser(
            context.contentResolver,
            SETTING,
            visibility.toInt(),
            userRepository.getSelectedUserInfo().id,
        )
    }

    private fun read(userId: Int): QsSliderVisibility {
        return try {
            QsSliderVisibility.fromInt(
                LineageSettings.Secure.getIntForUser(
                    context.contentResolver,
                    SETTING,
                    QsSliderVisibility.EXPANDED.toInt(),
                    userId,
                )
            )
        } catch (_: Throwable) {
            QsSliderVisibility.EXPANDED
        }
    }

    private companion object {
        const val SETTING = LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER
    }
}

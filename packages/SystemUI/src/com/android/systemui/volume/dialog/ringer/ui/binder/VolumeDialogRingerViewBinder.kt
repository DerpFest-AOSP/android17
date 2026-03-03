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

package com.android.systemui.volume.dialog.ringer.ui.binder

import android.animation.ArgbEvaluator
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import androidx.annotation.LayoutRes
import androidx.compose.ui.util.fastForEachIndexed
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.motion.widget.MotionScene
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.app.tracing.coroutines.launchInTraced
import com.android.app.tracing.coroutines.launchTraced
import com.android.internal.R as internalR
import com.android.systemui.res.R
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import com.android.systemui.volume.dialog.ringer.ui.util.VolumeDialogRingerDrawerTransitionListener
import com.android.systemui.volume.dialog.ringer.ui.util.updateCloseState
import com.android.systemui.volume.dialog.ringer.ui.util.updateOpenState
import com.android.systemui.volume.dialog.ringer.ui.viewmodel.RingerButtonUiModel
import com.android.systemui.volume.dialog.ringer.ui.viewmodel.RingerButtonViewModel
import com.android.systemui.volume.dialog.ringer.ui.viewmodel.RingerDrawerState
import com.android.systemui.volume.dialog.ringer.ui.viewmodel.RingerViewModel
import com.android.systemui.volume.dialog.ringer.ui.viewmodel.RingerViewModelState
import com.android.systemui.volume.dialog.ringer.ui.viewmodel.VolumeDialogRingerDrawerViewModel
import com.android.systemui.volume.dialog.ui.binder.ViewBinder
import com.android.systemui.volume.dialog.ui.utils.suspendAnimate
import com.android.systemui.volume.dialog.ui.viewmodel.VolumeDialogViewModel
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.mapLatest

private const val CLOSE_DRAWER_DELAY = 300L
// Ensure roundness and color of button is updated when progress is changed by a minimum fraction.
private const val BUTTON_MIN_VISIBLE_CHANGE = 0.05F

@OptIn(ExperimentalCoroutinesApi::class)
@VolumeDialogScope
class VolumeDialogRingerViewBinder
@Inject
constructor(
    private val viewModel: VolumeDialogRingerDrawerViewModel,
    private val dialogViewModel: VolumeDialogViewModel,
) : ViewBinder {
    private val roundnessSpringForce =
        SpringForce(1F).apply {
            stiffness = 800F
            dampingRatio = 0.6F
        }
    private val colorSpringForce =
        SpringForce(1F).apply {
            stiffness = 3800F
            dampingRatio = 1F
        }
    private val rgbEvaluator = ArgbEvaluator()

    override fun CoroutineScope.bind(view: View) {
        val volumeDialogBackgroundView = view.requireViewById<View>(R.id.volume_dialog_background)
        val ringerBackgroundView = view.requireViewById<View>(R.id.ringer_buttons_background)
        val drawerContainer = view.requireViewById<MotionLayout>(R.id.volume_ringer_drawer)

        val unselectedButtonUiModel = RingerButtonUiModel.getUnselectedButton(view.context)
        val selectedButtonUiModel = RingerButtonUiModel.getSelectedButton(view.context)
        val volumeDialogBgSmallRadius =
            view.context.resources.getDimensionPixelSize(
                R.dimen.volume_dialog_background_square_corner_radius
            )
        val volumeDialogBgFullRadius =
            view.context.resources.getDimensionPixelSize(
                R.dimen.volume_dialog_background_corner_radius
            )
        val bottomDefaultRadius = volumeDialogBgFullRadius.toFloat()
        val bottomCornerRadii =
            floatArrayOf(
                0F,
                0F,
                0F,
                0F,
                bottomDefaultRadius,
                bottomDefaultRadius,
                bottomDefaultRadius,
                bottomDefaultRadius,
            )
        var backgroundAnimationProgress: Float by
            Delegates.observable(0F) { _, _, progress ->
                ringerBackgroundView.applyCorners(
                    fullRadius = volumeDialogBgFullRadius,
                    diff = volumeDialogBgFullRadius - volumeDialogBgSmallRadius,
                    progress,
                )
            }
        val ringerDrawerTransitionListener = VolumeDialogRingerDrawerTransitionListener {
            backgroundAnimationProgress = it
        }
        drawerContainer.setTransitionListener(ringerDrawerTransitionListener)
        volumeDialogBackgroundView.background = volumeDialogBackgroundView.background.mutate()
        ringerBackgroundView.background = ringerBackgroundView.background.mutate()
        launchTraced("VDRVB#addTouchableBounds") {
            dialogViewModel.addTouchableBounds(ringerBackgroundView)
        }

        var gradientColorsForRinger: Pair<Int, Int>? = getGradientColorsForRinger(view.context)
        var selectedRingerButtonRef: ImageButton? = null
        launchTraced("VDRVB#gradientObserver") {
            val contentResolver = view.context.contentResolver
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        gradientColorsForRinger = getGradientColorsForRinger(view.context)
                        view.post {
                            selectedRingerButtonRef?.let {
                                applySelectedButtonAppearance(
                                    it, gradientColorsForRinger, view.context
                                )
                            }
                        }
                    }
                }
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.QS_VOLUME_GRADIENT_ENABLED),
                false,
                observer,
                UserHandle.USER_ALL,
            )
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.GRADIENT_START_COLOR),
                false,
                observer,
                UserHandle.USER_ALL,
            )
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.GRADIENT_END_COLOR),
                false,
                observer,
                UserHandle.USER_ALL,
            )
            try {
                awaitCancellation()
            } finally {
                contentResolver.unregisterContentObserver(observer)
            }
        }

        viewModel.ringerViewModel
            .mapLatest { ringerState ->
                when (ringerState) {
                    is RingerViewModelState.Available -> {
                        val uiModel = ringerState.uiModel
                        val orientation =
                            if (
                                view.context.resources.getBoolean(
                                    R.bool.volume_dialog_ringer_drawer_should_open_to_the_side
                                )
                            ) {
                                ringerState.orientation
                            } else {
                                Configuration.ORIENTATION_PORTRAIT
                            }

                        // Set up view background and visibility
                        drawerContainer.visibility = View.VISIBLE
                        (volumeDialogBackgroundView.background as GradientDrawable).cornerRadii =
                            bottomCornerRadii
                        when (uiModel.drawerState) {
                            is RingerDrawerState.Initial -> {
                                drawerContainer.animateAndBindDrawerButtons(
                                    viewModel,
                                    uiModel,
                                    selectedButtonUiModel,
                                    unselectedButtonUiModel,
                                    gradientColorsForRinger,
                                    { selectedRingerButtonRef = it },
                                )
                                ringerDrawerTransitionListener.setProgressChangeEnabled(true)
                                drawerContainer.closeDrawer(
                                    ringerBackgroundView,
                                    uiModel.currentButtonIndex,
                                    orientation,
                                )
                            }
                            is RingerDrawerState.Closed -> {
                                if (
                                    uiModel.selectedButton.ringerMode ==
                                        uiModel.drawerState.currentMode
                                ) {
                                    drawerContainer.animateAndBindDrawerButtons(
                                        viewModel,
                                        uiModel,
                                        selectedButtonUiModel,
                                        unselectedButtonUiModel,
                                        gradientColorsForRinger,
                                        { selectedRingerButtonRef = it },
                                        onProgressChanged = { progress, isReverse ->
                                            // Let's make button progress when switching matches
                                            // motionLayout transition progress. When full
                                            // radius,
                                            // progress is 0.0. When small radius, progress is
                                            // 1.0.
                                            backgroundAnimationProgress =
                                                if (isReverse) {
                                                    1F - progress
                                                } else {
                                                    progress
                                                }
                                        },
                                    ) {
                                        if (
                                            uiModel.currentButtonIndex ==
                                                uiModel.availableButtons.size - 1
                                        ) {
                                            ringerDrawerTransitionListener.setProgressChangeEnabled(
                                                false
                                            )
                                        } else {
                                            ringerDrawerTransitionListener.setProgressChangeEnabled(
                                                true
                                            )
                                        }
                                        drawerContainer.closeDrawer(
                                            ringerBackgroundView,
                                            uiModel.currentButtonIndex,
                                            orientation,
                                        )
                                    }
                                }
                            }
                            is RingerDrawerState.Open -> {
                                drawerContainer.animateAndBindDrawerButtons(
                                    viewModel,
                                    uiModel,
                                    selectedButtonUiModel,
                                    unselectedButtonUiModel,
                                    gradientColorsForRinger,
                                    { selectedRingerButtonRef = it },
                                )
                                // Open drawer
                                if (
                                    uiModel.currentButtonIndex == uiModel.availableButtons.size - 1
                                ) {
                                    ringerDrawerTransitionListener.setProgressChangeEnabled(false)
                                } else {
                                    ringerDrawerTransitionListener.setProgressChangeEnabled(true)
                                }
                                updateOpenState(drawerContainer, orientation, ringerBackgroundView)
                                drawerContainer
                                    .getTransition(R.id.close_to_open_transition)
                                    .setInterpolatorInfo(
                                        MotionScene.Transition.INTERPOLATE_REFERENCE_ID,
                                        null,
                                        R.anim.volume_dialog_ringer_open,
                                    )
                                drawerContainer.transitionToState(
                                    R.id.volume_dialog_ringer_drawer_open
                                )
                                ringerBackgroundView.background =
                                    ringerBackgroundView.background.mutate()
                            }
                        }
                    }
                    is RingerViewModelState.Unavailable -> {
                        drawerContainer.visibility = View.GONE
                        volumeDialogBackgroundView.setBackgroundResource(
                            R.drawable.volume_dialog_background
                        )
                    }
                }
            }
            .launchInTraced("VDRVB#ringerViewModel", this)
    }

    private suspend fun MotionLayout.animateAndBindDrawerButtons(
        viewModel: VolumeDialogRingerDrawerViewModel,
        uiModel: RingerViewModel,
        selectedButtonUiModel: RingerButtonUiModel,
        unselectedButtonUiModel: RingerButtonUiModel,
        gradientColorsForRinger: Pair<Int, Int>? = null,
        onSelectedButtonBound: (ImageButton?) -> Unit = {},
        onProgressChanged: (Float, Boolean) -> Unit = { _, _ -> },
        onAnimationEnd: Runnable? = null,
    ) {
        ensureChildCount(R.layout.volume_ringer_button, uiModel.availableButtons.size)
        if (
            uiModel.drawerState is RingerDrawerState.Closed &&
                uiModel.drawerState.currentMode != uiModel.drawerState.previousMode
        ) {
            val count = uiModel.availableButtons.size
            val selectedButton = getChildAt(count - uiModel.currentButtonIndex) as ImageButton
            val previousIndex =
                uiModel.availableButtons.indexOfFirst {
                    it.ringerMode == uiModel.drawerState.previousMode
                }
            val unselectedButton = getChildAt(count - previousIndex) as ImageButton
            // We only need to execute on roundness animation end and volume dialog background
            // progress update once because these changes should be applied once on volume dialog
            // background and ringer drawer views.
            coroutineScope {
                val selectedCornerRadius = selectedButton.backgroundShape().cornerRadius
                if (selectedCornerRadius.toInt() != selectedButtonUiModel.cornerRadius) {
                    launchTraced("VDRVB#selectedButtonAnimation") {
                        selectedButton.animateTo(
                            selectedButtonUiModel,
                            if (uiModel.currentButtonIndex == count - 1) {
                                onProgressChanged
                            } else {
                                { _, _ -> }
                            },
                        )
                    }
                }
                val unselectedCornerRadius = unselectedButton.backgroundShape().cornerRadius
                if (unselectedCornerRadius.toInt() != unselectedButtonUiModel.cornerRadius) {
                    launchTraced("VDRVB#unselectedButtonAnimation") {
                        unselectedButton.animateTo(
                            unselectedButtonUiModel,
                            if (previousIndex == count - 1) {
                                onProgressChanged
                            } else {
                                { _, _ -> }
                            },
                        )
                    }
                }
                launchTraced("VDRVB#bindButtons") {
                    delay(CLOSE_DRAWER_DELAY)
                    bindButtons(
                        viewModel,
                        uiModel,
                        onAnimationEnd,
                        isAnimated = true,
                        gradientColorsForRinger,
                        onSelectedButtonBound,
                    )
                }
            }
        } else {
            bindButtons(
                viewModel,
                uiModel,
                onAnimationEnd,
                gradientColorsForRinger = gradientColorsForRinger,
                onSelectedButtonBound = onSelectedButtonBound,
            )
        }
    }

    private fun MotionLayout.bindButtons(
        viewModel: VolumeDialogRingerDrawerViewModel,
        uiModel: RingerViewModel,
        onAnimationEnd: Runnable? = null,
        isAnimated: Boolean = false,
        gradientColorsForRinger: Pair<Int, Int>? = null,
        onSelectedButtonBound: (ImageButton?) -> Unit = {},
    ) {
        onSelectedButtonBound(null)
        val count = uiModel.availableButtons.size
        uiModel.availableButtons.fastForEachIndexed { index, ringerButton ->
            val view = getChildAt(count - index) as ImageButton
            val isOpen = uiModel.drawerState is RingerDrawerState.Open
            if (index == uiModel.currentButtonIndex) {
                view.bindDrawerButton(
                    if (isOpen) ringerButton else uiModel.selectedButton,
                    viewModel,
                    isOpen,
                    isSelected = true,
                    isAnimated = isAnimated,
                    gradientColorsForRinger = gradientColorsForRinger,
                    onSelectedButtonBound = { onSelectedButtonBound(view) },
                )
            } else {
                view.bindDrawerButton(ringerButton, viewModel, isOpen, isAnimated = isAnimated)
            }
        }
        onAnimationEnd?.run()
    }

    private fun ImageButton.bindDrawerButton(
        buttonViewModel: RingerButtonViewModel,
        viewModel: VolumeDialogRingerDrawerViewModel,
        isOpen: Boolean,
        isSelected: Boolean = false,
        isAnimated: Boolean = false,
        gradientColorsForRinger: Pair<Int, Int>? = null,
        onSelectedButtonBound: (() -> Unit)? = null,
    ) {
        // id = buttonViewModel.viewId
        setSelected(isSelected)
        val ringerContentDesc = context.getString(buttonViewModel.contentDescriptionResId)
        setImageResource(buttonViewModel.imageResId)
        contentDescription =
            if (isSelected && !isOpen) {
                context.getString(
                    R.string.volume_ringer_drawer_closed_content_description,
                    ringerContentDesc,
                )
            } else {
                ringerContentDesc
            }
        if (isSelected && !isAnimated) {
            onSelectedButtonBound?.invoke()
            if (gradientColorsForRinger != null) {
                applyGradientSelectionBackground(this, gradientColorsForRinger, context)
                val iconTint =
                    BatteryColors.textColorOnBackground(context, gradientColorsForRinger.second)
                setColorFilter(iconTint)
            } else {
                setBackgroundResource(R.drawable.volume_drawer_selection_bg)
                setColorFilter(context.getColor(internalR.color.materialColorOnPrimary))
                background = background.mutate()
            }
        } else if (!isAnimated) {
            setBackgroundResource(R.drawable.volume_ringer_item_bg)
            setColorFilter(context.getColor(internalR.color.materialColorOnSurface))
            background = background.mutate()
        }
        setOnClickListener {
            viewModel.onRingerButtonClicked(buttonViewModel.ringerMode, isSelected)
        }
    }

    private fun MotionLayout.ensureChildCount(@LayoutRes viewLayoutId: Int, count: Int) {
        val childCountDelta = childCount - count - 1
        when {
            childCountDelta > 0 -> {
                removeViews(0, childCountDelta)
            }
            childCountDelta < 0 -> {
                val inflater = LayoutInflater.from(context)
                repeat(-childCountDelta) {
                    inflater.inflate(viewLayoutId, this, true)
                    getChildAt(childCount - 1).id = View.generateViewId()
                }
            }
        }
    }

    private fun MotionLayout.closeDrawer(
        ringerBackground: View,
        selectedIndex: Int,
        orientation: Int,
    ) {
        setTransition(R.id.close_to_open_transition)
        getTransition(R.id.close_to_open_transition)
            .setInterpolatorInfo(
                MotionScene.Transition.INTERPOLATE_REFERENCE_ID,
                null,
                R.anim.volume_dialog_ringer_close,
            )
        updateCloseState(this, selectedIndex, orientation, ringerBackground)
        transitionToState(R.id.volume_dialog_ringer_drawer_close)
    }

    private suspend fun ImageButton.animateTo(
        ringerButtonUiModel: RingerButtonUiModel,
        onProgressChanged: (Float, Boolean) -> Unit = { _, _ -> },
    ) {
        val roundnessAnimation =
            SpringAnimation(FloatValueHolder(0F), 1F).setSpring(roundnessSpringForce)
        val colorAnimation = SpringAnimation(FloatValueHolder(0F), 1F).setSpring(colorSpringForce)
        val radius = backgroundShape().cornerRadius
        val cornerRadiusDiff = ringerButtonUiModel.cornerRadius - backgroundShape().cornerRadius

        roundnessAnimation.minimumVisibleChange = BUTTON_MIN_VISIBLE_CHANGE
        colorAnimation.minimumVisibleChange = BUTTON_MIN_VISIBLE_CHANGE
        coroutineScope {
            launchTraced("VDRVB#colorAnimation") {
                val startIconColor = imageTintList?.colors?.firstOrNull()
                    ?: ringerButtonUiModel.tintColor
                val startBgColor = backgroundShape().color?.colors?.getOrNull(0)
                    ?: ringerButtonUiModel.backgroundColor
                colorAnimation.suspendAnimate { value ->
                    val currentIconColor =
                        rgbEvaluator.evaluate(
                            value.coerceIn(0F, 1F),
                            startIconColor,
                            ringerButtonUiModel.tintColor,
                        ) as Int
                    val currentBgColor =
                        rgbEvaluator.evaluate(
                            value.coerceIn(0F, 1F),
                            startBgColor,
                            ringerButtonUiModel.backgroundColor,
                        ) as Int

                    backgroundShape().setColor(currentBgColor)
                    background.invalidateSelf()
                    setColorFilter(currentIconColor)
                }
            }
            roundnessAnimation.suspendAnimate { value ->
                onProgressChanged(value, cornerRadiusDiff > 0F)
                backgroundShape().cornerRadius = radius + value * cornerRadiusDiff
                background.invalidateSelf()
            }
        }
    }

    private fun View.applyCorners(fullRadius: Int, diff: Int, progress: Float) {
        val radius = fullRadius - progress * diff
        (background as GradientDrawable).cornerRadius = radius
        background.invalidateSelf()
    }

    /** When volume gradient is enabled, returns (startArgb, endArgb); otherwise null. */
    private fun getGradientColorsForRinger(context: android.content.Context): Pair<Int, Int>? {
        return try {
            if (
                Settings.System.getIntForUser(
                    context.contentResolver,
                    Settings.System.QS_VOLUME_GRADIENT_ENABLED,
                    1,
                    UserHandle.USER_CURRENT,
                ) != 1
            ) {
                return null
            }
            val isDark =
                (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            val startArgb =
                Settings.System.getIntForUser(
                    context.contentResolver,
                    Settings.System.GRADIENT_START_COLOR,
                    0,
                    UserHandle.USER_CURRENT,
                )
            val endArgb =
                Settings.System.getIntForUser(
                    context.contentResolver,
                    Settings.System.GRADIENT_END_COLOR,
                    0,
                    UserHandle.USER_CURRENT,
                )
            val start =
                if (startArgb != 0) startArgb
                else context.getColor(
                    if (isDark) R.color.derpfestui_color_gradient_start_dark
                    else R.color.derpfestui_color_gradient_start_light
                )
            val end =
                if (endArgb != 0) endArgb
                else context.getColor(
                    if (isDark) R.color.derpfestui_color_gradient_end_dark
                    else R.color.derpfestui_color_gradient_end_light
                )
            Pair(start, end)
        } catch (_: Throwable) {
            null
        }
    }

    /** Sets the selected ringer button background to a gradient (start -> end), matching auto brightness button. */
    private fun applyGradientSelectionBackground(
        button: ImageButton,
        gradientColors: Pair<Int, Int>,
        context: android.content.Context,
    ) {
        val (start, end) = gradientColors
        val radiusPx =
            context.resources.getDimensionPixelSize(
                R.dimen.volume_dialog_ringer_selected_button_background_radius
            ).toFloat()
        val gradientDrawable =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(start, end)
            ).apply {
                setCornerRadius(radiusPx)
            }
        val insetPx = (4 * context.resources.displayMetrics.density).toInt()
        button.background = InsetDrawable(gradientDrawable, insetPx, insetPx, insetPx, insetPx)
    }

    private fun applySelectedButtonAppearance(
        button: ImageButton,
        gradientColors: Pair<Int, Int>?,
        context: android.content.Context,
    ) {
        if (gradientColors != null) {
            applyGradientSelectionBackground(button, gradientColors, context)
            button.setColorFilter(BatteryColors.textColorOnBackground(context, gradientColors.second))
        } else {
            button.setBackgroundResource(R.drawable.volume_drawer_selection_bg)
            button.background = button.background.mutate()
            button.setColorFilter(context.getColor(internalR.color.materialColorOnPrimary))
        }
    }
}

private fun ImageButton.backgroundShape(): GradientDrawable =
    (background as InsetDrawable).drawable as GradientDrawable

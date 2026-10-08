/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.Utilities
import com.android.launcher3.util.SystemUiController
import com.android.launcher3.views.BaseDragLayer
import kotlin.math.abs

/**
 * The feed panel left of the first home screen page. It follows the finger while you swipe
 * from the home screen (through [FeedOverlay]), and can be swiped away again to the left.
 */
class FeedPanelView(private val launcher: Launcher) :
    AbstractFloatingView(launcher, null), Insettable {

    val state = FeedPanelState(launcher)
    /** Told how far open the panel is, so the workspace can react (from [FeedOverlay]). */
    var onProgress: ((Float) -> Unit)? = null

    private var progress = 0f
    private var animator: ValueAnimator? = null
    private val touchSlop = ViewConfiguration.get(launcher).scaledTouchSlop
    private val minFling = ViewConfiguration.get(launcher).scaledMinimumFlingVelocity * 3f
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var velocity: VelocityTracker? = null

    init {
        orientation = VERTICAL
        visibility = View.GONE
        // Keep clicks from reaching the workspace underneath.
        isClickable = true
        addView(ComposeView(launcher).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                FeedPanel(state, onOpenSettings = { openSettings(false) },
                    onImport = { openSettings(true) })
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Adds the panel to the launcher's drag layer (once). */
    fun attach() {
        if (parent != null) return
        val lp = BaseDragLayer.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT)
        lp.ignoreInsets = true
        launcher.dragLayer.addView(this, lp)
    }

    fun detach() {
        animator?.cancel()
        setProgress(0f)
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun setInsets(insets: Rect) {
        val density = resources.displayMetrics.density
        state.topInset = (insets.top / density).dp
        state.bottomInset = (insets.bottom / density).dp
    }

    // --- Opening and closing ---

    /** Called while the home screen is being dragged towards the panel. */
    fun onDragFromHome(fraction: Float) {
        animator?.cancel()
        setProgress(fraction.coerceIn(0f, 1f))
    }

    /** Finishes a drag from the home screen: opens or closes depending on distance and speed. */
    fun onDragFromHomeEnd(velocityX: Float) {
        val open = when {
            velocityX > minFling -> true
            velocityX < -minFling -> false
            else -> progress > 0.35f
        }
        settle(open)
    }

    fun open(animate: Boolean = true) = settle(true, animate)

    override fun handleClose(animate: Boolean) {
        settle(false, animate)
    }

    private fun settle(open: Boolean, animate: Boolean = true) {
        animator?.cancel()
        val target = if (open) 1f else 0f
        if (!animate || width == 0) {
            setProgress(target)
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = (300 * abs(target - progress)).toLong().coerceAtLeast(120)
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { setProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) animator = null
                }
            })
            start()
        }
    }

    private fun setProgress(p: Float) {
        progress = p
        val w = if (width > 0) width else resources.displayMetrics.widthPixels
        val rtl = Utilities.isRtl(resources)
        translationX = (if (rtl) 1 else -1) * (1f - p) * w
        val visible = p > 0f
        mIsOpen = visible
        if (visible != (visibility == View.VISIBLE)) {
            visibility = if (visible) View.VISIBLE else View.GONE
            if (!visible) {
                state.openItem = null
                state.showAddDialog = false
            }
        }
        updateStatusBar(p > 0.5f)
        onProgress?.invoke(p)
    }

    private var statusBarOverridden = false

    private fun updateStatusBar(overPanel: Boolean) {
        if (overPanel == statusBarOverridden) return
        statusBarOverridden = overPanel
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val flags = when {
            !overPanel -> 0
            dark -> SystemUiController.FLAG_DARK_STATUS or SystemUiController.FLAG_DARK_NAV
            else -> SystemUiController.FLAG_LIGHT_STATUS or SystemUiController.FLAG_LIGHT_NAV
        }
        launcher.systemUiController.updateUiState(
            SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET, flags)
    }

    // --- Swiping the panel away ---

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
                velocity?.recycle()
                velocity = VelocityTracker.obtain()
                velocity?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(ev)
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                val towardsHome = if (Utilities.isRtl(resources)) dx > 0 else dx < 0
                if (towardsHome && abs(dx) > touchSlop * 2 && abs(dx) > abs(dy) * 1.5f) {
                    dragging = true
                    animator?.cancel()
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(ev)
        velocity?.addMovement(ev)
        val w = width.coerceAtLeast(1)
        val dx = (ev.rawX - downX) * if (Utilities.isRtl(resources)) -1 else 1
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> setProgress((1f + dx / w).coerceIn(0f, 1f))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.computeCurrentVelocity(1000)
                val vx = (velocity?.xVelocity ?: 0f) * if (Utilities.isRtl(resources)) -1 else 1
                dragging = false
                settle(when {
                    vx < -minFling -> false
                    vx > minFling -> true
                    else -> progress > 0.65f
                })
            }
        }
        return true
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent) = false

    override fun onBackInvoked() {
        if (!state.onBack()) close(true)
    }

    override fun isOfType(type: Int) = (type and AbstractFloatingView.TYPE_NUDGE) != 0

    private fun openSettings(startImport: Boolean) {
        launcher.startActivity(Intent(launcher, FeedSettingsActivity::class.java)
            .putExtra(FeedSettingsActivity.EXTRA_IMPORT, startImport))
    }
}

/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.folio.feed.FeedTheme
import com.android.launcher3.views.BaseDragLayer
import kotlinx.coroutines.launch

/**
 * Shown when something on a locked home screen is long-pressed: a small card next to it with
 * a button that unlocks the home screen for a while once it has been held down.
 */
class HomeLockPopup private constructor(
    private val launcher: Launcher,
    private val anchor: Rect,
) : AbstractFloatingView(launcher, null), Insettable {

    private val density = resources.displayMetrics.density
    private val insets = Rect()
    private val card = ComposeView(launcher).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent { FeedTheme { LockCard(onUnlock = ::unlock) } }
    }

    init {
        isClickable = true
        alpha = 0f
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    override fun setInsets(insets: Rect) {
        this.insets.set(insets)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val margin = (16 * density).toInt()
        val cardWidth = minOf(w - 2 * margin, (340 * density).toInt()).coerceAtLeast(0)
        card.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST))
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = card.measuredWidth
        val h = card.measuredHeight
        val margin = (16 * density).toInt()
        val gap = (12 * density).toInt()
        val width = r - l
        val height = b - t
        val top = insets.top + margin
        val bottom = height - insets.bottom - margin
        val (cx, y) = if (anchor.isEmpty && anchor.left == 0 && anchor.top == 0) {
            width / 2 to (height - h) / 2
        } else {
            // Above what was pressed if it fits, otherwise below it.
            val above = anchor.top - gap - h
            anchor.centerX() to (if (above >= top) above else anchor.bottom + gap)
        }
        val x = (cx - w / 2).coerceIn(margin, (width - w - margin).coerceAtLeast(margin))
        val cy = y.coerceIn(top, (bottom - h).coerceAtLeast(top))
        card.layout(x, cy, x + w, cy + h)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // A touch outside the card closes the popup.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && !inCard(ev)) close(true)
        return true
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && !inCard(ev)) {
            close(true)
            return true
        }
        return false
    }

    /** This view fills the drag layer, so its coordinates are the drag layer's. */
    private fun inCard(ev: MotionEvent): Boolean =
        ev.x >= card.left && ev.x < card.right && ev.y >= card.top && ev.y < card.bottom

    private fun unlock() {
        if (!mIsOpen) return
        HomeLock.unlockForAWhile(launcher)
        close(true)
    }

    private fun open() {
        mIsOpen = true
        val lp = BaseDragLayer.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT)
        lp.ignoreInsets = true
        launcher.dragLayer.addView(this, lp)
        card.scaleX = 0.92f
        card.scaleY = 0.92f
        animate().alpha(1f).setDuration(150).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    override fun handleClose(animate: Boolean) {
        if (!mIsOpen) return
        mIsOpen = false
        if (!animate) {
            remove()
            return
        }
        animate().alpha(0f).setDuration(120).withEndAction { remove() }.start()
    }

    private fun remove() {
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun isOfType(type: Int) = (type and TYPE_OPTIONS_POPUP_DIALOG) != 0

    companion object {
        /** Hold the button this long to unlock. */
        private const val HOLD_MS = 900

        @JvmStatic
        fun show(launcher: Launcher, anchor: Rect) {
            closeOpenViews(launcher, false,
                TYPE_ACTION_POPUP or TYPE_OPTIONS_POPUP or TYPE_OPTIONS_POPUP_DIALOG)
            HomeLockPopup(launcher, Rect(anchor)).open()
        }

        @Composable
        private fun LockCard(onUnlock: () -> Unit) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Lock, null, Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.folio_lock_popup_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.folio_lock_popup_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    HoldButton(onUnlock)
                }
            }
        }

        /** Fills up while held; unlocks once full. Letting go early empties it again. */
        @Composable
        private fun HoldButton(onUnlock: () -> Unit) {
            val progress = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            val view = LocalView.current
            val label = stringResource(R.string.folio_lock_popup_action)
            val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .semantics {
                        role = Role.Button
                        onLongClick(label) { onUnlock(); true }
                        onClick(label) { onUnlock(); true }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            val job = scope.launch {
                                val left = ((1f - progress.value) * HOLD_MS).toInt()
                                progress.animateTo(1f, tween(left, easing = LinearEasing))
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onUnlock()
                            }
                            waitForUpOrCancellation()
                            if (progress.value < 1f) {
                                job.cancel()
                                scope.launch { progress.animateTo(0f, tween(200)) }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.align(Alignment.CenterStart).fillMaxHeight()
                        .fillMaxWidth(progress.value).background(fill)
                )
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.folio_lock_popup_hold),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

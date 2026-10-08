/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherFiles
import com.android.launcher3.R
import java.lang.ref.WeakReference

/**
 * "Lock home screen": while it's on, shortcuts, folders and widgets on the home screen and
 * dock can't be moved, removed, resized or added. Apps still open normally.
 *
 * Long-pressing anything while locked shows [HomeLockPopup]; holding its button unlocks the
 * home screen for [UNLOCK_MS], after which it locks again by itself (once any drag in
 * progress has finished).
 */
object HomeLock {
    /** Matches the switch in launcher_preferences.xml. */
    const val PREF_KEY = "pref_folio_lock_home"

    const val UNLOCK_MS = 45_000L

    /** Unlocked for a while from the popup; the setting itself stays on. Main thread only. */
    @Volatile
    private var tempUnlocked = false
    private var launcherRef: WeakReference<Launcher>? = null
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)

    /** Turning the setting on or off ends any temporary unlock. Kept as a strong reference. */
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREF_KEY) endUnlock()
    }
    private var listening = false

    @JvmStatic
    fun isLocked(context: Context): Boolean =
        !tempUnlocked && prefs(context).getBoolean(PREF_KEY, false)

    @JvmStatic
    fun showLockedMessage(context: Context) {
        Toast.makeText(context, R.string.folio_lock_toast, Toast.LENGTH_SHORT).show()
    }

    /** What a long press on [v] does on a locked home screen: shows the unlock popup. */
    @JvmStatic
    fun onLockedLongPress(launcher: Launcher, v: View): Boolean {
        val rect = Rect()
        try {
            launcher.dragLayer.getDescendantRectRelativeToSelf(v, rect)
        } catch (e: Exception) {
            rect.setEmpty()
        }
        HomeLockPopup.show(launcher, rect)
        return true
    }

    /** A long press on an empty part of the home screen at ([x], [y]); negative: no point. */
    @JvmStatic
    fun onLockedLongPressAt(launcher: Launcher, x: Float, y: Float) {
        val rect = Rect()
        if (x >= 0 && y >= 0) rect.set(x.toInt(), y.toInt(), x.toInt(), y.toInt())
        HomeLockPopup.show(launcher, rect)
    }

    /** Unlocks the home screen for [UNLOCK_MS]. */
    fun unlockForAWhile(launcher: Launcher) {
        if (!listening) {
            prefs(launcher).registerOnSharedPreferenceChangeListener(prefListener)
            listening = true
        }
        tempUnlocked = true
        launcherRef = WeakReference(launcher)
        handler.removeCallbacks(relock)
        handler.postDelayed(relock, UNLOCK_MS)
        Toast.makeText(launcher, R.string.folio_lock_unlocked, Toast.LENGTH_SHORT).show()
    }

    private fun endUnlock() {
        handler.removeCallbacks(relock)
        tempUnlocked = false
    }

    private val relock = object : Runnable {
        override fun run() {
            val launcher = launcherRef?.get()
            // Let a drag that's under way finish first.
            if (launcher != null && !launcher.isDestroyed && launcher.dragController.isDragging) {
                handler.postDelayed(this, 500)
                return
            }
            tempUnlocked = false
            launcherRef = null
            if (launcher == null || launcher.isDestroyed || !isLocked(launcher)) return
            AbstractFloatingView.closeOpenViews(launcher, true,
                AbstractFloatingView.TYPE_WIDGET_RESIZE_FRAME or
                    AbstractFloatingView.TYPE_WIDGETS_BOTTOM_SHEET or
                    AbstractFloatingView.TYPE_ACTION_POPUP)
            Toast.makeText(launcher, R.string.folio_lock_relocked, Toast.LENGTH_SHORT).show()
        }
    }
}

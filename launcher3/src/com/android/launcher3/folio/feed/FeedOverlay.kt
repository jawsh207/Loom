/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.content.SharedPreferences
import android.view.MotionEvent
import com.android.launcher3.Launcher
import com.android.systemui.plugins.shared.LauncherOverlayManager
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayCallbacks
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayTouchProxy

/**
 * Puts Folio's feed panel in the "-1" spot left of the first home screen page, where Google's
 * launchers show Discover. Launcher3 hands over the overscroll of the first page through
 * [LauncherOverlayTouchProxy]; here that drives [FeedPanelView] in the launcher's own window.
 */
class FeedOverlay private constructor(private val launcher: Launcher) :
    LauncherOverlayManager, LauncherOverlayTouchProxy {

    private val panel = FeedPanelView(launcher)
    private var callbacks: LauncherOverlayCallbacks? = null
    private var flingVelocity = 0f

    init {
        panel.onProgress = { callbacks?.onOverlayScrollChanged(it) }
    }

    private fun attach() {
        panel.attach()
        launcher.setLauncherOverlay(this)
        FeedRefreshJob.schedule(launcher)
    }

    // --- LauncherOverlayTouchProxy: the swipe from the home screen ---

    override fun setOverlayCallbacks(callbacks: LauncherOverlayCallbacks) {
        this.callbacks = callbacks
    }

    override fun onFlingVelocity(velocity: Float) {
        flingVelocity = velocity
    }

    override fun onOverlayMotionEvent(ev: MotionEvent, scrollProgress: Float) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                flingVelocity = 0f
                panel.onDragFromHome(0f)
            }
            MotionEvent.ACTION_MOVE -> panel.onDragFromHome(scrollProgress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                panel.onDragFromHomeEnd(flingVelocity)
        }
    }

    // --- LauncherOverlayManager ---

    override fun openOverlay() = panel.open()

    override fun hideOverlay(duration: Int) = panel.close(duration > 0)

    override fun onActivityDestroyed() {
        panel.detach()
        callbacks = null
    }

    companion object {
        /**
         * The overlay for [launcher]: the feed panel when it's switched on in settings,
         * otherwise none. Call [watchSetting] once so switching it takes effect right away.
         */
        @JvmStatic
        fun create(launcher: Launcher): LauncherOverlayManager {
            if (!FeedPrefs.isEnabled(launcher)) {
                FeedRefreshJob.schedule(launcher) // cancels it
                return object : LauncherOverlayManager {}
            }
            return FeedOverlay(launcher).also { launcher.dragLayer.post { it.attach() } }
        }

        private val listeners = HashMap<Launcher, SharedPreferences.OnSharedPreferenceChangeListener>()

        /** Rebuilds the launcher's overlay when the feed panel is switched on or off. */
        @JvmStatic
        fun watchSetting(launcher: Launcher) {
            if (listeners.containsKey(launcher)) return
            val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    FeedPrefs.ENABLED -> launcher.recreateOverlay()
                    FeedPrefs.REFRESH_HOURS, FeedPrefs.WIFI_ONLY ->
                        FeedRefreshJob.schedule(launcher)
                }
            }
            listeners[launcher] = l
            FeedPrefs.prefs(launcher).registerOnSharedPreferenceChangeListener(l)
        }

        @JvmStatic
        fun unwatchSetting(launcher: Launcher) {
            listeners.remove(launcher)?.let {
                FeedPrefs.prefs(launcher).unregisterOnSharedPreferenceChangeListener(it)
            }
        }
    }
}

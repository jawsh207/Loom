/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherFiles
import com.android.launcher3.states.RotationHelper
import java.util.WeakHashMap

/**
 * Makes Home settings › Allow home screen rotation cover Loom's other screens too (Home
 * settings, Grid, Drawer tabs, Manage feeds, Backup & restore, the widget picker): with it off
 * they stay upright like the home screen; with it on they turn with the phone when auto-rotate
 * is on.
 *
 * The home screen itself is left to [RotationHelper], which also handles tablets and the
 * system's own reasons to rotate it. Tablets and other large screens always rotate, as the home
 * screen does there.
 */
object ScreenRotation : Application.ActivityLifecycleCallbacks {

    private lateinit var prefs: SharedPreferences
    /** Loom's screens that are open now, so a change of the setting reaches them at once. */
    private val open = WeakHashMap<Activity, Unit>()

    // Kept in a field: SharedPreferences only holds its listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == RotationHelper.PREF_ALLOW_ROTATION) open.keys.toList().forEach(::apply)
    }

    /** Called once from the Application. */
    @JvmStatic
    fun install(app: Application) {
        prefs = app.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        app.registerActivityLifecycleCallbacks(this)
    }

    private fun apply(activity: Activity) {
        val largeScreen = activity.resources.configuration.smallestScreenWidthDp >= 600
        val allowed = largeScreen || prefs.getBoolean(RotationHelper.PREF_ALLOW_ROTATION, false)
        val wanted = if (allowed) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
        if (activity.requestedOrientation != wanted) activity.requestedOrientation = wanted
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is Launcher) return
        open[activity] = Unit
        apply(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        open.remove(activity)
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}

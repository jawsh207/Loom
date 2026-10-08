/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.content.Context
import android.view.View
import android.widget.Toast
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherFiles
import com.android.launcher3.R

/**
 * "Lock home screen": while it's on, shortcuts, folders and widgets on the home screen and
 * dock can't be moved, removed, resized or added. Long-pressing an icon still shows its menu
 * (app shortcuts, app info, Edit icon), and apps still open normally.
 */
object HomeLock {
    /** Matches the switch in launcher_preferences.xml. */
    const val PREF_KEY = "pref_folio_lock_home"

    @JvmStatic
    fun isLocked(context: Context): Boolean =
        context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY, false)

    @JvmStatic
    fun showLockedMessage(context: Context) {
        Toast.makeText(context, R.string.folio_lock_toast, Toast.LENGTH_SHORT).show()
    }

    /**
     * What a long press does on a locked layout: an icon's menu without picking the icon up;
     * for anything else (widgets, folders) a short note that the layout is locked.
     */
    @JvmStatic
    fun onLockedLongPress(launcher: Launcher, v: View): Boolean {
        if (v is BubbleTextView && v.canShowLongPressPopup() &&
                launcher.popupControllerForAppIcons.show(v) != null) {
            return true
        }
        showLockedMessage(launcher)
        return true
    }
}

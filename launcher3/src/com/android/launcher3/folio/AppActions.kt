/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.allapps.search.AppsSearchContainerLayout
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.LauncherComponentProvider

/** Renaming apps and hiding them from the app drawer. */
object AppActions {

    // --- Hidden apps ---

    @JvmStatic
    fun isHidden(context: Context, info: ItemInfo): Boolean {
        if (info.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) return false
        val cn = info.targetComponent ?: return false
        val user = info.user ?: return false
        return AppPrefs.hidden(context).get(cn, user) != null
    }

    @JvmStatic
    fun hasHiddenApps(context: Context): Boolean = AppPrefs.hidden(context).entries().isNotEmpty()

    @JvmStatic
    fun setHidden(launcher: Launcher, info: ItemInfo, hidden: Boolean) {
        val cn = info.targetComponent ?: return
        AppPrefs.hidden(launcher).set(cn, info.user, if (hidden) "1" else null)
        // Re-filter the drawer's list, and the search results if a search is showing.
        launcher.appsView?.let { apps ->
            apps.setDrawerTabFilter(DrawerTabsStore.get(launcher).selectedFilter)
            (apps.searchUiManager as? AppsSearchContainerLayout)?.onAppsUpdated()
        }
        val name = info.title ?: ""
        Toast.makeText(launcher,
            launcher.getString(if (hidden) R.string.folio_hidden_toast
                else R.string.folio_unhidden_toast, name),
            Toast.LENGTH_LONG).show()
    }

    /** Whether [query] asks for the hidden apps ("hidden", in any case). */
    @JvmStatic
    fun isHiddenQuery(context: Context, query: String): Boolean =
        query.trim().equals(context.getString(R.string.folio_hidden_query), ignoreCase = true)

    // --- Custom names ---

    /** The name the app itself gives its launcher activity. */
    private fun originalLabel(context: Context, cn: ComponentName, user: UserHandle): String? =
        try {
            context.getSystemService(LauncherApps::class.java)
                ?.getActivityList(cn.packageName, user)
                ?.firstOrNull { it.componentName == cn }?.label?.toString()
        } catch (e: Exception) {
            null
        }

    /** Asks for a new name for an app; blank (or "Original name") goes back to its own. */
    @JvmStatic
    fun showRenameDialog(launcher: Launcher, info: ItemInfo) {
        val cn = info.targetComponent ?: return
        val user = info.user ?: return
        val labels = AppPrefs.labels(launcher)
        val custom = labels.get(cn, user)
        val original = originalLabel(launcher, cn, user) ?: info.title?.toString() ?: ""
        val current = custom ?: info.title?.toString() ?: original

        val input = EditText(launcher).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            isSingleLine = true
            hint = original
            setText(current)
            setSelection(current.length)
        }
        val frame = FrameLayout(launcher).apply {
            val pad = resources.getDimensionPixelSize(R.dimen.drawer_tab_dialog_padding)
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val builder = DrawerTabDialogs.builder(launcher)
            .setTitle(R.string.folio_rename_title)
            .setView(frame)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                setLabel(launcher, cn, user, name.takeIf { it.isNotEmpty() && it != original })
            }
        if (custom != null) {
            builder.setNeutralButton(R.string.folio_rename_reset) { _, _ ->
                setLabel(launcher, cn, user, null)
            }
        }
        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    private fun setLabel(context: Context, cn: ComponentName, user: UserHandle, label: String?) {
        AppPrefs.labels(context).set(cn, user, label)
        // Reload this app's name and icon wherever it's shown (same path as Edit icon).
        LauncherComponentProvider.get(context).iconChangeTracker
            .notifyIconChanged(cn.packageName, user)
    }

}

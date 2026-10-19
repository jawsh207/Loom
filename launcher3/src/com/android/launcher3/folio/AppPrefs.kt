/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.os.UserHandle

/**
 * Per-app settings (a custom name, hidden from the drawer), stored by launcher activity and
 * user, e.g. `com.example/.Main#0`.
 *
 * Some apps (banking apps especially) rename their launcher activity in updates. A lookup that
 * misses falls back to the one entry saved for the same package and user, so a setting isn't
 * lost when that happens.
 */
class AppPrefs private constructor(context: Context, name: String) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** In-memory copy; lookups happen for every icon the launcher loads. */
    @Volatile
    private var snapshot: Map<String, String> = load()

    private fun load(): Map<String, String> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    /** The key actually holding [cn]'s value, if any (its own, or its package's only one). */
    private fun keyFor(cn: ComponentName, user: UserHandle): String? {
        val own = key(cn, user)
        val map = snapshot
        if (map.containsKey(own)) return own
        val prefix = cn.packageName + "/"
        val suffix = "#" + user.hashCode()
        val other = map.keys.singleOrNull { it.startsWith(prefix) && it.endsWith(suffix) }
            ?: return null
        // Only if that activity is gone: a package with two launcher activities (rare) keeps
        // separate settings for each.
        val otherClass = ComponentName.unflattenFromString(other.substringBeforeLast('#'))
            ?.className ?: return null
        val stillThere = try {
            appContext.getSystemService(LauncherApps::class.java)
                ?.getActivityList(cn.packageName, user)
                ?.any { it.componentName.className == otherClass } ?: false
        } catch (e: Exception) {
            false
        }
        return if (stillThere) null else other
    }

    fun get(cn: ComponentName, user: UserHandle): String? = keyFor(cn, user)?.let { snapshot[it] }

    /** Sets [cn]'s value, or clears it when [value] is null. */
    fun set(cn: ComponentName, user: UserHandle, value: String?) {
        val edit = prefs.edit()
        keyFor(cn, user)?.let { edit.remove(it) }
        if (value != null) edit.putString(key(cn, user), value)
        edit.commit()
        snapshot = load()
    }

    /** Every entry, as (package, user hash) → value; for listing hidden apps. */
    fun entries(): Map<String, String> = snapshot

    companion object {
        fun key(cn: ComponentName, user: UserHandle) = cn.flattenToString() + "#" + user.hashCode()

        /** The package and user hash a stored key belongs to. */
        fun packageAndUser(key: String): Pair<String, Int>? {
            val slash = key.indexOf('/')
            val hash = key.lastIndexOf('#')
            if (slash <= 0 || hash <= slash) return null
            return key.substring(0, slash) to (key.substring(hash + 1).toIntOrNull() ?: return null)
        }

        @Volatile private var labels: AppPrefs? = null
        @Volatile private var hidden: AppPrefs? = null

        /** Custom app names. */
        @JvmStatic
        fun labels(context: Context): AppPrefs =
            labels ?: synchronized(this) {
                labels ?: AppPrefs(context, "folio_app_labels").also { labels = it }
            }

        /** Apps hidden from the drawer (value "1"). */
        @JvmStatic
        fun hidden(context: Context): AppPrefs =
            hidden ?: synchronized(this) {
                hidden ?: AppPrefs(context, "folio_hidden_apps").also { hidden = it }
            }
    }
}

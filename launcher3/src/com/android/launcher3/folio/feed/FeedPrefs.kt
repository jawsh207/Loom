/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.LauncherFiles

/** Feed settings, kept in the launcher's preferences file (the settings screen writes them). */
object FeedPrefs {
    /** Matches the switch in launcher_preferences.xml. Off by default: Folio stays offline. */
    const val ENABLED = "pref_feed_enabled"
    const val SETTINGS = "pref_feed_settings"
    const val REFRESH_HOURS = "pref_feed_refresh_hours"
    const val WIFI_ONLY = "pref_feed_wifi_only"
    const val KEEP_DAYS = "pref_feed_keep_days"
    const val TEXT_SCALE = "pref_feed_text_scale"
    const val UNREAD_ONLY = "pref_feed_unread_only"
    const val LARGE_IMAGES = "pref_feed_large_images"

    val REFRESH_CHOICES = intArrayOf(0, 1, 3, 6, 12, 24)
    val KEEP_CHOICES = intArrayOf(7, 30, 90, 365)

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(ENABLED, false)

    /** Hours between background refreshes; 0 means only when you refresh by hand. */
    fun refreshHours(context: Context) = prefs(context).getInt(REFRESH_HOURS, 3)

    fun wifiOnly(context: Context) = prefs(context).getBoolean(WIFI_ONLY, false)

    fun keepDays(context: Context) = prefs(context).getInt(KEEP_DAYS, 30)

    fun textScale(context: Context) = prefs(context).getFloat(TEXT_SCALE, 1f)

    /** Article text sizes offered in Manage feeds › Style: label to scale. */
    @JvmField
    val TEXT_SIZES = listOf(
        com.android.launcher3.R.string.feed_text_small to 0.9f,
        com.android.launcher3.R.string.feed_text_normal to 1f,
        com.android.launcher3.R.string.feed_text_large to 1.15f,
        com.android.launcher3.R.string.feed_text_larger to 1.3f,
        com.android.launcher3.R.string.feed_text_largest to 1.5f,
    )

    /** The label of the current text size. */
    fun textSizeLabel(context: Context): Int {
        val scale = textScale(context)
        return TEXT_SIZES.minByOrNull { kotlin.math.abs(it.second - scale) }!!.first
    }

    fun largeImages(context: Context) = prefs(context).getBoolean(LARGE_IMAGES, false)
}

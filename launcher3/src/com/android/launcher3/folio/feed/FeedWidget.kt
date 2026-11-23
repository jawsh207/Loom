/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import com.android.launcher3.InvariantDeviceProfile
import kotlin.math.ceil

/**
 * The one widget that can sit at the top of the feed panel (Manage feeds › At the top ›
 * Widget).
 *
 * It has its own widget host, separate from the home screen's, so the launcher's own widget
 * bookkeeping (which removes widget ids it doesn't know about) never touches it. The chosen
 * widget's id is kept in [FeedPrefs.TOP_WIDGET]; [FeedWidgetActivity] picks, binds and sets it up.
 * Its height starts at what it would have on the home screen and can be changed with the
 * handle under it in the panel ([FeedPrefs.TOP_WIDGET_HEIGHT]).
 */
object FeedWidget {
    private const val TAG = "FeedWidget"

    /** Any number not used by another host in Loom (the home screen's is 1024). */
    private const val HOST_ID = 0x4c6f6f6d

    private var host: AppWidgetHost? = null

    @JvmStatic
    fun host(context: Context): AppWidgetHost =
        host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }

    /** The chosen widget's id, if it's still bound to this host; otherwise -1. */
    fun currentId(context: Context): Int {
        val id = FeedPrefs.prefs(context).getInt(FeedPrefs.TOP_WIDGET, -1)
        if (id == -1) return -1
        // A backup restored on another phone, or after reinstalling, brings an id that isn't ours.
        val held = runCatching { host(context).appWidgetIds.contains(id) }.getOrDefault(false)
        return if (held && info(context, id) != null) id else -1
    }

    fun info(context: Context, id: Int): AppWidgetProviderInfo? =
        AppWidgetManager.getInstance(context).getAppWidgetInfo(id)

    /** Makes [id] the feed's widget, releasing the one before it. */
    fun set(context: Context, id: Int) {
        val prefs = FeedPrefs.prefs(context)
        val old = prefs.getInt(FeedPrefs.TOP_WIDGET, -1)
        if (old != -1 && old != id) runCatching { host(context).deleteAppWidgetId(old) }
        // A new widget starts at its own height, not the last one's.
        prefs.edit().putInt(FeedPrefs.TOP_WIDGET, id).remove(FeedPrefs.TOP_WIDGET_HEIGHT).apply()
    }

    fun remove(context: Context) {
        val prefs = FeedPrefs.prefs(context)
        val old = prefs.getInt(FeedPrefs.TOP_WIDGET, -1)
        if (old != -1) runCatching { host(context).deleteAppWidgetId(old) }
        prefs.edit().remove(FeedPrefs.TOP_WIDGET).remove(FeedPrefs.TOP_WIDGET_HEIGHT).apply()
    }

    /** The widget updates itself only while its host listens: while the panel is attached. */
    @JvmStatic
    fun startListening(context: Context) {
        try {
            host(context).startListening()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start the feed widget host", e)
        }
    }

    @JvmStatic
    fun stopListening(context: Context) {
        try {
            host(context).stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't stop the feed widget host", e)
        }
    }

    fun createView(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
        host(context).createView(context, id, info)

    /** The shortest the widget can be made. */
    const val MIN_HEIGHT_DP = 48f

    /** The tallest: most of the screen, leaving room to scroll past it. */
    fun maxHeightDp(context: Context): Float {
        val m = context.resources.displayMetrics
        return m.heightPixels / m.density * 0.7f
    }

    /** The height chosen with the handle, or else the one the widget asks for. */
    fun heightDp(context: Context, info: AppWidgetProviderInfo): Float {
        val saved = FeedPrefs.prefs(context).getFloat(FeedPrefs.TOP_WIDGET_HEIGHT, -1f)
        val dp = if (saved > 0) saved else naturalHeightDp(context, info)
        return dp.coerceIn(MIN_HEIGHT_DP, maxHeightDp(context))
    }

    /**
     * The height the widget would have on the home screen: the rows it asks for (or that its
     * minimum height needs) times the home screen's row height.
     */
    private fun naturalHeightDp(context: Context, info: AppWidgetProviderInfo): Float {
        val density = context.resources.displayMetrics.density
        val cellPx = runCatching {
            InvariantDeviceProfile.INSTANCE.get(context).getDeviceProfile(context)
                .workspaceProfile.cellHeightPx.toFloat()
        }.getOrDefault(0f).takeIf { it > 0 } ?: (96 * density)
        val rows = if (info.targetCellHeight > 0) info.targetCellHeight
            else ceil(maxOf(info.minHeight, 1) / cellPx).toInt().coerceAtLeast(1)
        return rows * cellPx / density
    }

    fun saveHeight(context: Context, dp: Float) {
        FeedPrefs.prefs(context).edit().putFloat(FeedPrefs.TOP_WIDGET_HEIGHT,
            dp.coerceIn(MIN_HEIGHT_DP, maxHeightDp(context))).apply()
    }

    /** Back to the height the widget asks for. */
    fun resetHeight(context: Context) {
        FeedPrefs.prefs(context).edit().remove(FeedPrefs.TOP_WIDGET_HEIGHT).apply()
    }

    /** Tells the widget how much room it has, so it lays itself out for it. */
    fun reportSize(context: Context, id: Int, widthDp: Float, heightDp: Float) {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp.toInt())
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(SizeF(widthDp, heightDp)))
        }
        runCatching { AppWidgetManager.getInstance(context).updateAppWidgetOptions(id, options) }
    }
}

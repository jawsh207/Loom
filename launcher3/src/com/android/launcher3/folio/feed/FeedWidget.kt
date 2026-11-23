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

/**
 * The one widget that can sit at the top of the feed panel (Manage feeds › At the top ›
 * Widget).
 *
 * It has its own widget host, separate from the home screen's, so the launcher's own widget
 * bookkeeping (which removes widget ids it doesn't know about) never touches it. The chosen
 * widget's id is kept in [FeedPrefs.TOP_WIDGET]; [FeedWidgetActivity] picks, binds and sets it up.
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
        prefs.edit().putInt(FeedPrefs.TOP_WIDGET, id).apply()
    }

    fun remove(context: Context) {
        val prefs = FeedPrefs.prefs(context)
        val old = prefs.getInt(FeedPrefs.TOP_WIDGET, -1)
        if (old != -1) runCatching { host(context).deleteAppWidgetId(old) }
        prefs.edit().remove(FeedPrefs.TOP_WIDGET).apply()
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

    /** A height that fits the widget's own idea of its size, within reason for a panel. */
    fun heightDp(context: Context, info: AppWidgetProviderInfo): Float {
        val density = context.resources.displayMetrics.density
        val cells = info.targetCellHeight
        val dp = if (cells > 0) cells * 96f else maxOf(info.minHeight, info.minResizeHeight) / density
        return dp.coerceIn(80f, 360f)
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

/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherFiles
import com.android.launcher3.R

/**
 * Folio's home screen and app drawer grid sizes. 0 means "use the device's default grid".
 *
 * Applied on top of the grid Launcher3 picks for the display (like a device maker's partner
 * overrides), so icon sizes and spacing still come from that grid. A different home screen
 * size uses its own layout database, reset to the stock layout for that size.
 */
object FolioGrid {
    const val COLUMNS = "pref_folio_grid_columns"
    const val ROWS = "pref_folio_grid_rows"
    const val HOTSEAT = "pref_folio_grid_hotseat"
    const val DRAWER_COLUMNS = "pref_folio_drawer_columns"
    /** Set with a home screen size change: the next layout load starts from the stock layout. */
    private const val FRESH_START = "pref_folio_grid_fresh_start"

    /** Matches the entry in launcher_preferences.xml. */
    const val SETTINGS_KEY = "pref_folio_grid"

    val COLUMN_RANGE = 3..8
    val ROW_RANGE = 3..10

    val KEYS = setOf(COLUMNS, ROWS, HOTSEAT, DRAWER_COLUMNS)

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)

    data class Sizes(val columns: Int, val rows: Int, val hotseat: Int, val drawerColumns: Int)

    /** The device's own grid (before Folio's changes), for the settings screen. */
    @Volatile
    var stock = Sizes(4, 5, 4, 4)
        private set

    fun read(context: Context): Sizes {
        val p = prefs(context)
        return Sizes(
            p.getInt(COLUMNS, 0), p.getInt(ROWS, 0), p.getInt(HOTSEAT, 0),
            p.getInt(DRAWER_COLUMNS, 0),
        )
    }

    /**
     * Saves new sizes. With [freshStart], the home screen and dock are reset to the stock
     * layout when it reloads for the new size (see ModelDbController.folioFreshStart).
     */
    fun write(context: Context, sizes: Sizes, freshStart: Boolean) {
        prefs(context).edit()
            .putBoolean(FRESH_START, freshStart)
            .putInt(COLUMNS, sizes.columns)
            .putInt(ROWS, sizes.rows)
            .putInt(HOTSEAT, sizes.hotseat)
            .putInt(DRAWER_COLUMNS, sizes.drawerColumns)
            .commit()
    }

    @JvmStatic
    fun consumeFreshStart(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(FRESH_START, false)) return false
        p.edit().putBoolean(FRESH_START, false).commit()
        return true
    }

    /** The home screen size [sizes] works out to (columns, rows, dock) on this device. */
    fun effectiveHome(sizes: Sizes): Triple<Int, Int, Int> {
        val custom = sizes.columns in COLUMN_RANGE && sizes.rows in ROW_RANGE
        val columns = if (custom) sizes.columns else stock.columns
        val rows = if (custom) sizes.rows else stock.rows
        val dock = when {
            sizes.hotseat in COLUMN_RANGE -> sizes.hotseat
            custom -> sizes.columns
            else -> stock.hotseat
        }
        return Triple(columns, rows, dock)
    }

    /** Called by [InvariantDeviceProfile] after it has picked the device's grid. */
    @JvmStatic
    fun applyOverrides(idp: InvariantDeviceProfile, context: Context) {
        val s = read(context)
        val stockColumns = idp.numColumns
        val stockRows = idp.numRows
        val stockHotseat = idp.numDatabaseHotseatIcons
        stock = Sizes(stockColumns, stockRows, stockHotseat, idp.numAllAppsColumns)

        val customHome = s.columns in COLUMN_RANGE && s.rows in ROW_RANGE
        val columns = if (customHome) s.columns else stockColumns
        val rows = if (customHome) s.rows else stockRows
        val hotseat = when {
            s.hotseat in COLUMN_RANGE -> s.hotseat
            customHome -> s.columns
            else -> stockHotseat
        }
        if (columns != stockColumns || rows != stockRows || hotseat != stockHotseat) {
            idp.numColumns = columns
            idp.numRows = rows
            idp.numShownHotseatIcons = hotseat
            idp.numDatabaseHotseatIcons = hotseat
            // Launcher3 decides whether to migrate the layout by the database name alone, so
            // each home screen size (including the dock) gets its own database. Changing size
            // then goes through grid migration, which moves the icons over.
            idp.dbFile = "launcher_${columns}_by_${rows}" +
                (if (hotseat != columns) "_dock_$hotseat" else "") + ".db"
            idp.defaultLayoutId = stockLayoutFor(columns, rows, hotseat)
        }
        if (s.drawerColumns in COLUMN_RANGE) {
            idp.numAllAppsColumns = s.drawerColumns
            idp.numDatabaseAllAppsColumns = s.drawerColumns
        }
        fitIcons(idp, context,
            home = columns > stockColumns || hotseat > stockHotseat,
            drawer = s.drawerColumns in COLUMN_RANGE && s.drawerColumns > stock.drawerColumns)
    }

    /**
     * The stock home screen layout for a grid size, loaded when the home screen is reset.
     * Their home screen icons sit in the last row and their dock icons are placed by slot,
     * so they fit any size; dock icons beyond the dock's size are left out.
     */
    private fun stockLayoutFor(columns: Int, rows: Int, dock: Int): Int = when {
        dock >= 5 -> R.xml.default_workspace_5x5
        rows >= 5 -> R.xml.default_workspace_4x5
        else -> R.xml.default_workspace_4x4
    }

    /**
     * Launcher3 shrinks icons when rows run out of height, but not when columns run out of
     * width; with more columns (or dock icons) than the device's grid, scale them to fit.
     */
    private fun fitIcons(idp: InvariantDeviceProfile, context: Context, home: Boolean,
            drawer: Boolean) {
        val widthDp = context.resources.configuration.smallestScreenWidthDp.toFloat()
        if (widthDp <= 0f) return
        val margin = idp.horizontalMargin?.getOrNull(0) ?: 16f
        val homeColumns = maxOf(idp.numColumns, idp.numShownHotseatIcons)
        if (home) idp.iconSize?.let { sizes ->
            val factor = fitFactor(widthDp - 2 * margin, homeColumns, sizes[0])
            if (factor < 1f) {
                idp.iconSize = FloatArray(sizes.size) { sizes[it] * factor }
                idp.iconTextSize = idp.iconTextSize?.let { t ->
                    FloatArray(t.size) { t[it] * maxOf(0.8f, factor) }
                }
            }
        }
        if (drawer) idp.allAppsIconSize?.let { sizes ->
            val factor = fitFactor(widthDp - 2 * 16f, idp.numAllAppsColumns, sizes[0])
            if (factor < 1f) {
                idp.allAppsIconSize = FloatArray(sizes.size) { sizes[it] * factor }
                idp.allAppsIconTextSize = idp.allAppsIconTextSize?.let { t ->
                    FloatArray(t.size) { t[it] * maxOf(0.8f, factor) }
                }
            }
        }
    }

    /** How much to shrink [iconDp] icons so [columns] of them fit in [widthDp] with space. */
    private fun fitFactor(widthDp: Float, columns: Int, iconDp: Float): Float {
        if (columns <= 0 || iconDp <= 0f) return 1f
        val cellDp = widthDp / columns
        return minOf(1f, cellDp * 0.78f / iconDp)
    }
}

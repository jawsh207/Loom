/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.widget.WidgetManagerHelper

/**
 * Moves the home screen and dock onto a new grid size (Home settings › Grid) without losing
 * anything. Launcher3's own migration drops dock icons that don't fit a smaller dock and widgets
 * bigger than the new grid, and reuses an old layout if that size was used before; this one
 * keeps every item:
 *
 * - Each item stays on its page at the same spot if it still fits there.
 * - Otherwise it goes to the first free spot on the same page, then on any other page, and if
 *   none has room, on new pages added at the end.
 * - Dock icons beyond the new dock's size move to the home screen the same way.
 * - Widgets wider or taller than the new grid are shrunk to fit it.
 * - Folders keep their contents (their items are copied with them, untouched).
 *
 * Rows are copied as they are (ids, folder contents, widget ids, everything); only their
 * container and position change.
 */
object GridMigration {
    private const val TAG = "LoomGridMigration"

    private class Item(val values: ContentValues) {
        val id = values.getAsInteger(Favorites._ID)
        val container: Int = values.getAsInteger(Favorites.CONTAINER)
        val screen: Int = values.getAsInteger(Favorites.SCREEN) ?: 0
        val cellX: Int = values.getAsInteger(Favorites.CELLX) ?: 0
        val cellY: Int = values.getAsInteger(Favorites.CELLY) ?: 0
        var spanX: Int = (values.getAsInteger(Favorites.SPANX) ?: 1).coerceAtLeast(1)
        var spanY: Int = (values.getAsInteger(Favorites.SPANY) ?: 1).coerceAtLeast(1)
        var minSpanX = spanX
        var minSpanY = spanY
    }

    /** One page's occupied cells. */
    private class Page(val columns: Int, val rows: Int) {
        val cells = Array(columns) { BooleanArray(rows) }

        fun vacant(x: Int, y: Int, w: Int, h: Int): Boolean {
            if (x < 0 || y < 0 || x + w > columns || y + h > rows) return false
            for (i in x until x + w) for (j in y until y + h) if (cells[i][j]) return false
            return true
        }

        fun mark(x: Int, y: Int, w: Int, h: Int) {
            for (i in x until x + w) for (j in y until y + h) cells[i][j] = true
        }

        /** First free spot for w×h in reading order, or null. */
        fun find(w: Int, h: Int): Pair<Int, Int>? {
            for (y in 0..rows - h) for (x in 0..columns - w) if (vacant(x, y, w, h)) return x to y
            return null
        }
    }

    /**
     * Rebuilds [destTable] from [srcTable] for a grid of [columns]×[rows] and a dock of
     * [dockSize]. Both tables are in [db]; [destTable] is emptied first.
     */
    @JvmStatic
    fun migrate(
        context: Context,
        db: SQLiteDatabase,
        srcTable: String,
        destTable: String,
        columns: Int,
        rows: Int,
        dockSize: Int,
    ) {
        val all = ArrayList<Item>()
        db.query(srcTable, null, null, null, null, null, null).use { c: Cursor ->
            while (c.moveToNext()) {
                val v = ContentValues()
                DatabaseUtils.cursorRowToContentValues(c, v)
                all.add(Item(v))
            }
        }
        db.delete(destTable, null, null)

        val dock = all.filter { it.container == Favorites.CONTAINER_HOTSEAT }.sortedBy { it.screen }
        val desktop = all.filter { it.container == Favorites.CONTAINER_DESKTOP }
            .sortedWith(compareBy({ it.screen }, { it.cellY }, { it.cellX }))
        // Everything else (folder contents) is copied unchanged.
        val inside = all.filter {
            it.container != Favorites.CONTAINER_HOTSEAT && it.container != Favorites.CONTAINER_DESKTOP
        }

        // Widgets: how small they can be made.
        val widgets = WidgetManagerHelper(context)
        for (item in desktop) {
            if (item.values.getAsInteger(Favorites.ITEM_TYPE) != Favorites.ITEM_TYPE_APPWIDGET) {
                continue
            }
            val provider = item.values.getAsString(Favorites.APPWIDGET_PROVIDER)
                ?.let(ComponentName::unflattenFromString)
            val spans = runCatching {
                widgets.getLauncherAppWidgetInfo(
                    item.values.getAsInteger(Favorites.APPWIDGET_ID) ?: -1, provider)?.minSpans
            }.getOrNull()
            if (spans != null) {
                if (spans.x > 0) item.minSpanX = minOf(spans.x, item.spanX)
                if (spans.y > 0) item.minSpanY = minOf(spans.y, item.spanY)
            }
        }
        // Nothing can be bigger than the grid itself.
        for (item in desktop) {
            item.spanX = item.spanX.coerceAtMost(columns)
            item.spanY = item.spanY.coerceAtMost(rows)
            item.minSpanX = item.minSpanX.coerceIn(1, item.spanX)
            item.minSpanY = item.minSpanY.coerceIn(1, item.spanY)
        }

        val pages = sortedMapOf<Int, Page>()
        fun page(screen: Int) = pages.getOrPut(screen) { Page(columns, rows) }
        desktop.map { it.screen }.distinct().forEach { page(it) }
        if (pages.isEmpty()) page(0)

        fun write(item: Item, container: Int, screen: Int, x: Int, y: Int, w: Int, h: Int) {
            val v = ContentValues(item.values)
            v.put(Favorites.CONTAINER, container)
            v.put(Favorites.SCREEN, screen)
            v.put(Favorites.CELLX, x)
            v.put(Favorites.CELLY, y)
            v.put(Favorites.SPANX, w)
            v.put(Favorites.SPANY, h)
            db.insertOrThrow(destTable, null, v)
        }

        /** Places [item] on [screen] at the first free spot (full size, then smallest). */
        fun placeOn(item: Item, screen: Int): Boolean {
            val p = page(screen)
            for ((w, h) in listOf(item.spanX to item.spanY, item.minSpanX to item.minSpanY)) {
                p.find(w, h)?.let { (x, y) ->
                    p.mark(x, y, w, h)
                    write(item, Favorites.CONTAINER_DESKTOP, screen, x, y, w, h)
                    return true
                }
            }
            return false
        }

        val homeless = ArrayList<Item>()
        // 1. Where it was, if it still fits.
        for (item in desktop) {
            val p = page(item.screen)
            if (p.vacant(item.cellX, item.cellY, item.spanX, item.spanY)) {
                p.mark(item.cellX, item.cellY, item.spanX, item.spanY)
                write(item, Favorites.CONTAINER_DESKTOP, item.screen, item.cellX, item.cellY,
                    item.spanX, item.spanY)
            } else {
                homeless.add(item)
            }
        }
        // 2. Elsewhere on its own page.
        val stillHomeless = homeless.filterNot { placeOn(it, it.screen) }.toMutableList()

        // 3. The dock: in order, as many as fit; the rest join the items still to place.
        dock.forEachIndexed { i, item ->
            if (i < dockSize) {
                write(item, Favorites.CONTAINER_HOTSEAT, i, i, 0, 1, 1)
            } else {
                item.spanX = 1; item.spanY = 1; item.minSpanX = 1; item.minSpanY = 1
                stillHomeless.add(item)
            }
        }

        // 4. Any page with room, then new pages at the end.
        for (item in stillHomeless) {
            if (pages.keys.toList().any { placeOn(item, it) }) continue
            var screen = pages.lastKey() + 1
            while (!placeOn(item, screen)) screen++
        }

        // Folder contents, unchanged.
        for (item in inside) db.insertOrThrow(destTable, null, item.values)

        Log.i(TAG, "Moved ${desktop.size} home screen items, ${dock.size} dock items and " +
            "${inside.size} folder items to ${columns}x$rows (dock $dockSize); " +
            "${stillHomeless.size} placed on another page")
    }
}

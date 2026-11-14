/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.folio.feed.FeedDatabase
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.widget.LauncherWidgetHolder
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup & restore (Home settings): one zip file with the home screen and dock layout (the
 * launcher's database: pages, folders, widgets, shortcuts), Loom's settings, drawer tabs,
 * renamed and hidden apps, and the feeds (subscriptions, folders, starred and saved articles).
 *
 * Restoring writes the settings and feeds straight away, and leaves the layout database waiting
 * in [stagedDir]; Loom then restarts, and the launcher swaps it in before it first opens its
 * database (ModelDbController.folioApplyRestore). Widgets whose id isn't Loom's on this phone
 * (a new phone, or Loom reinstalled) are marked to be set up again with a tap.
 */
object FolioBackup {
    private const val TAG = "FolioBackup"
    private const val FORMAT = 1
    private const val MANIFEST = "loom-backup.json"
    private const val PREFS = "prefs.json"
    private const val LAUNCHER_DB = "launcher.db"
    private const val FEEDS_DB = "feeds.db"
    private const val FEEDS_DB_NAME = "folio_feeds.db"

    /** Preference files saved whole. */
    private val WHOLE_PREF_FILES = listOf("drawer_tabs", "folio_app_labels", "folio_hidden_apps")

    /** Settings saved from the main preferences (everything else there is launcher state). */
    private fun isSetting(key: String) = key != "pref_folio_grid_fresh_start" && (
        key.startsWith("pref_folio_") || key.startsWith("pref_feed_") || key in setOf(
            "pref_add_icon_to_home", "pref_double_tap_to_lock", "pref_swipe_down_notifications"))

    class BackupException(message: String) : Exception(message)

    fun suggestedName(): String =
        "Loom backup " + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".zip"

    private fun versionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    // --- Backing up ---

    /** Writes a backup to [uri]. Call off the main thread. */
    @Throws(Exception::class)
    fun backup(context: Context, uri: Uri) {
        val work = File(context.cacheDir, "folio_backup").apply { deleteRecursively(); mkdirs() }
        try {
            val launcherDb = File(work, LAUNCHER_DB)
            // A consistent copy of the layout, taken on the launcher's own database thread.
            MODEL_EXECUTOR.submit {
                vacuumInto(LauncherAppState.getInstance(context).model.modelDbController.db,
                    launcherDb)
            }.get()
            val feedsDb = File(work, FEEDS_DB)
            vacuumInto(FeedDatabase.get(context).readableDatabase, feedsDb)

            val manifest = JSONObject()
                .put("app", "Loom")
                .put("format", FORMAT)
                .put("versionCode", versionCode(context))
                .put("created", System.currentTimeMillis())
            context.contentResolver.openOutputStream(uri, "wt")?.use { raw ->
                ZipOutputStream(raw.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry(MANIFEST))
                    zip.write(manifest.toString(2).toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry(PREFS))
                    zip.write(exportPrefs(context).toString(2).toByteArray())
                    zip.closeEntry()
                    for (f in listOf(launcherDb, feedsDb)) {
                        zip.putNextEntry(ZipEntry(f.name))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: throw IOException("Couldn't open $uri")
        } finally {
            work.deleteRecursively()
        }
    }

    private fun vacuumInto(db: SQLiteDatabase, out: File) {
        out.delete()
        db.execSQL("VACUUM INTO '" + out.absolutePath.replace("'", "''") + "'")
    }

    private fun exportPrefs(context: Context): JSONObject {
        val all = JSONObject()
        val main = context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE)
        all.put(LauncherFiles.SHARED_PREFERENCES_KEY,
            prefsToJson(main.all.filterKeys(::isSetting)))
        for (name in WHOLE_PREF_FILES) {
            all.put(name, prefsToJson(context.getSharedPreferences(name, Context.MODE_PRIVATE).all))
        }
        return all
    }

    private fun prefsToJson(values: Map<String, *>): JSONObject {
        val o = JSONObject()
        for ((k, v) in values) {
            val (type, value) = when (v) {
                is Boolean -> "boolean" to v
                is Int -> "int" to v
                is Long -> "long" to v
                is Float -> "float" to v.toDouble()
                is String -> "string" to v
                is Set<*> -> "set" to JSONArray(v.map { it.toString() })
                else -> continue
            }
            o.put(k, JSONObject().put("type", type).put("value", value))
        }
        return o
    }

    // --- Restoring ---

    /** Where a restored layout waits for the launcher to restart. */
    private fun stagedDir(context: Context) = File(context.filesDir, "folio_restore")

    /** The restored layout database waiting to be swapped in, if there is one. */
    @JvmStatic
    fun stagedLauncherDb(context: Context): File? =
        File(stagedDir(context), LAUNCHER_DB).takeIf { it.isFile }

    /**
     * Reads the backup at [uri] and restores settings and feeds; the layout is left staged for
     * the restart. Call off the main thread, then [restart].
     */
    @Throws(Exception::class)
    fun restore(context: Context, uri: Uri) {
        val work = File(context.cacheDir, "folio_restore_in").apply { deleteRecursively(); mkdirs() }
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        val name = File(e.name).name // no paths from the file
                        if (e.isDirectory || name !in setOf(MANIFEST, PREFS, LAUNCHER_DB, FEEDS_DB)) {
                            continue
                        }
                        File(work, name).outputStream().use { zip.copyTo(it) }
                    }
                }
            } ?: throw IOException("Couldn't open $uri")

            val manifestFile = File(work, MANIFEST)
            if (!manifestFile.isFile) throw BackupException("not_a_backup")
            val manifest = JSONObject(manifestFile.readText())
            if (manifest.optString("app") != "Loom") throw BackupException("not_a_backup")
            if (manifest.optInt("format") > FORMAT ||
                manifest.optLong("versionCode") > versionCode(context)) {
                throw BackupException("newer")
            }
            val launcherDb = File(work, LAUNCHER_DB)
            if (!launcherDb.isFile || !looksLikeLayout(launcherDb)) {
                throw BackupException("not_a_backup")
            }

            // Settings first: the home screen size decides where the layout goes.
            File(work, PREFS).takeIf { it.isFile }?.let { importPrefs(context, JSONObject(it.readText())) }

            File(work, FEEDS_DB).takeIf { it.isFile }?.let { feeds ->
                FeedDatabase.closeForRestore()
                context.deleteDatabase(FEEDS_DB_NAME)
                feeds.copyTo(context.getDatabasePath(FEEDS_DB_NAME), overwrite = true)
            }

            val staged = stagedDir(context).apply { deleteRecursively(); mkdirs() }
            launcherDb.copyTo(File(staged, LAUNCHER_DB), overwrite = true)
        } finally {
            work.deleteRecursively()
        }
    }

    private fun looksLikeLayout(file: File): Boolean = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT count(*) FROM ${Favorites.TABLE_NAME}", null).use { it.moveToFirst() }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Not a layout database", e)
        false
    }

    private fun importPrefs(context: Context, all: JSONObject) {
        val main = context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE)
        main.edit().apply {
            main.all.keys.filter(::isSetting).forEach { remove(it) }
            all.optJSONObject(LauncherFiles.SHARED_PREFERENCES_KEY)?.let { putAll(this, it, ::isSetting) }
        }.commit()
        for (name in WHOLE_PREF_FILES) {
            val p = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            p.edit().clear().apply {
                all.optJSONObject(name)?.let { putAll(this, it) { true } }
            }.commit()
        }
    }

    private fun putAll(e: SharedPreferences.Editor, o: JSONObject, keep: (String) -> Boolean) {
        for (k in o.keys()) {
            if (!keep(k)) continue
            val item = o.optJSONObject(k) ?: continue
            when (item.optString("type")) {
                "boolean" -> e.putBoolean(k, item.optBoolean("value"))
                "int" -> e.putInt(k, item.optInt("value"))
                "long" -> e.putLong(k, item.optLong("value"))
                "float" -> e.putFloat(k, item.optDouble("value").toFloat())
                "string" -> e.putString(k, item.optString("value"))
                "set" -> item.optJSONArray("value")?.let { a ->
                    e.putStringSet(k, (0 until a.length()).map { a.optString(it) }.toSet())
                }
            }
        }
    }

    /** Back to a fresh launcher, which picks up the restored layout. */
    fun restart(context: Context) {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
            .setPackage(context.packageName)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 600)
    }

    /**
     * Called by the launcher with the restored layout in place, before it opens it: widgets
     * whose id doesn't belong to Loom on this phone are marked to be set up again, and widget
     * ids Loom holds that the restored layout doesn't use are released.
     */
    @JvmStatic
    fun fixRestoredWidgets(context: Context, db: File) {
        try {
            val host = AppWidgetHost(context, LauncherWidgetHolder.APPWIDGET_HOST_ID)
            val held = host.appWidgetIds.toMutableSet()
            val manager = AppWidgetManager.getInstance(context)
            val used = HashSet<Int>()
            SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READWRITE).use { sql ->
                sql.rawQuery("SELECT ${Favorites._ID}, ${Favorites.APPWIDGET_ID}, " +
                    "${Favorites.APPWIDGET_PROVIDER} FROM ${Favorites.TABLE_NAME} " +
                    "WHERE ${Favorites.ITEM_TYPE} = ${Favorites.ITEM_TYPE_APPWIDGET}", null
                ).use { c ->
                    while (c.moveToNext()) {
                        val rowId = c.getLong(0)
                        val widgetId = c.getInt(1)
                        val provider = c.getString(2)
                        val info = if (widgetId in held) manager.getAppWidgetInfo(widgetId) else null
                        if (info != null && info.provider.flattenToString() == provider) {
                            used.add(widgetId)
                        } else {
                            sql.update(Favorites.TABLE_NAME, ContentValues().apply {
                                put(Favorites.RESTORED, LauncherAppWidgetInfo.FLAG_ID_NOT_VALID
                                    or LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY
                                    or LauncherAppWidgetInfo.FLAG_UI_NOT_READY)
                            }, "${Favorites._ID} = ?", arrayOf(rowId.toString()))
                        }
                    }
                }
            }
            (held - used).forEach { host.deleteAppWidgetId(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't check the restored widgets", e)
        }
    }

    /** Removes the staged layout once it's in place. */
    @JvmStatic
    fun clearStaged(context: Context) {
        stagedDir(context).deleteRecursively()
    }
}

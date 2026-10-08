/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Feeds, their items and cached articles. All calls block; use them off the main thread. */
class FeedDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "folio_feeds.db", null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE feeds (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                site_url TEXT,
                folder TEXT,
                etag TEXT,
                last_modified TEXT,
                last_checked INTEGER NOT NULL DEFAULT 0,
                last_error TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                feed_id INTEGER NOT NULL REFERENCES feeds(id) ON DELETE CASCADE,
                guid TEXT NOT NULL,
                title TEXT NOT NULL,
                link TEXT,
                author TEXT,
                published INTEGER NOT NULL,
                summary TEXT,
                content TEXT,
                image TEXT,
                read INTEGER NOT NULL DEFAULT 0,
                starred INTEGER NOT NULL DEFAULT 0,
                added INTEGER NOT NULL,
                article_html TEXT,
                article_title TEXT,
                article_byline TEXT,
                UNIQUE(feed_id, guid))"""
        )
        db.execSQL("CREATE INDEX items_published ON items(published DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // --- Feeds ---

    fun feeds(): List<Feed> = readableDatabase.rawQuery(
        """SELECT f.id, f.url, f.title, f.site_url, f.folder, f.last_checked, f.last_error,
              (SELECT COUNT(*) FROM items i WHERE i.feed_id = f.id AND i.read = 0)
           FROM feeds f ORDER BY COALESCE(f.folder, ''), f.title COLLATE NOCASE""",
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(Feed(c.getLong(0), c.getString(1), c.getString(2), c.str(3), c.str(4),
                    c.getLong(5), c.str(6), c.getInt(7)))
            }
        }
    }

    fun feed(id: Long): Feed? = feeds().firstOrNull { it.id == id }

    fun feedByUrl(url: String): Feed? = feeds().firstOrNull { it.url == url }

    fun insertFeed(url: String, title: String, siteUrl: String?, folder: String?): Long =
        writableDatabase.insertOrThrow("feeds", null, ContentValues().apply {
            put("url", url)
            put("title", title)
            put("site_url", siteUrl)
            put("folder", folder?.takeIf { it.isNotBlank() })
        })

    fun renameFeed(id: Long, title: String) = updateFeed(id, ContentValues().apply {
        put("title", title)
    })

    fun setFolder(id: Long, folder: String?) = updateFeed(id, ContentValues().apply {
        put("folder", folder?.trim()?.takeIf { it.isNotEmpty() })
    })

    fun deleteFeed(id: Long) {
        writableDatabase.delete("feeds", "id = ?", arrayOf(id.toString()))
    }

    fun cacheHeaders(id: Long): Pair<String?, String?> = readableDatabase.rawQuery(
        "SELECT etag, last_modified FROM feeds WHERE id = ?", arrayOf(id.toString()),
    ).use { c -> if (c.moveToFirst()) c.str(0) to c.str(1) else null to null }

    fun recordFetch(id: Long, etag: String?, lastModified: String?, error: String?,
            title: String?, siteUrl: String?) {
        updateFeed(id, ContentValues().apply {
            put("last_checked", System.currentTimeMillis())
            put("last_error", error)
            if (error == null) {
                put("etag", etag)
                put("last_modified", lastModified)
            }
            if (siteUrl != null) put("site_url", siteUrl)
        })
        // A feed keeps the title it was given unless it never had a real one.
        if (title != null) {
            writableDatabase.execSQL(
                "UPDATE feeds SET title = ? WHERE id = ? AND title = url",
                arrayOf<Any>(title, id),
            )
        }
    }

    private fun updateFeed(id: Long, values: ContentValues) {
        writableDatabase.update("feeds", values, "id = ?", arrayOf(id.toString()))
    }

    // --- Items ---

    /** Stores new items; returns how many were new. Existing items keep their read state. */
    fun insertItems(feedId: Long, items: List<ParsedItem>): Int {
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (item in items) {
                val values = ContentValues().apply {
                    put("feed_id", feedId)
                    put("guid", item.guid)
                    put("title", item.title)
                    put("link", item.link)
                    put("author", item.author)
                    put("published", if (item.published > 0) item.published else now)
                    put("summary", item.summary)
                    put("content", item.content)
                    put("image", item.image)
                    put("added", now)
                }
                val row = db.insertWithOnConflict("items", null, values,
                    SQLiteDatabase.CONFLICT_IGNORE)
                if (row != -1L) {
                    added++
                } else {
                    // Already known: refresh what the feed says, keep read/starred/article.
                    values.remove("added")
                    values.remove("published")
                    db.update("items", values, "feed_id = ? AND guid = ?",
                        arrayOf(feedId.toString(), item.guid))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    fun items(filter: FeedFilter, limit: Int = 500): List<FeedItem> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        filter.feedId?.let { where += "i.feed_id = ?"; args += it.toString() }
        filter.folder?.let { where += "f.folder = ?"; args += it }
        if (filter.unreadOnly) where += "i.read = 0"
        if (filter.starredOnly) where += "i.starred = 1"
        val clause = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")
        return readableDatabase.rawQuery(
            """SELECT i.id, i.feed_id, f.title, i.title, i.link, i.author, i.published,
                  i.summary, i.content, i.image, i.read, i.starred
               FROM items i JOIN feeds f ON f.id = i.feed_id
               $clause ORDER BY i.published DESC LIMIT $limit""",
            args.toTypedArray(),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toItem()) } }
    }

    fun item(id: Long): FeedItem? = readableDatabase.rawQuery(
        """SELECT i.id, i.feed_id, f.title, i.title, i.link, i.author, i.published,
              i.summary, i.content, i.image, i.read, i.starred
           FROM items i JOIN feeds f ON f.id = i.feed_id WHERE i.id = ?""",
        arrayOf(id.toString()),
    ).use { c -> if (c.moveToFirst()) c.toItem() else null }

    fun setRead(id: Long, read: Boolean) {
        writableDatabase.execSQL("UPDATE items SET read = ? WHERE id = ?",
            arrayOf<Any>(if (read) 1 else 0, id))
    }

    fun setStarred(id: Long, starred: Boolean) {
        writableDatabase.execSQL("UPDATE items SET starred = ? WHERE id = ?",
            arrayOf<Any>(if (starred) 1 else 0, id))
    }

    fun markAllRead(filter: FeedFilter) {
        val ids = items(filter, limit = Int.MAX_VALUE).filter { !it.read }.map { it.id }
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { setRead(it, true) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun cachedArticle(id: Long): Article? = readableDatabase.rawQuery(
        "SELECT article_html, article_title, article_byline, link FROM items WHERE id = ?",
        arrayOf(id.toString()),
    ).use { c ->
        if (c.moveToFirst() && c.str(0) != null) {
            Article(c.str(1) ?: "", c.str(2), c.getString(0), c.str(3), extracted = true)
        } else {
            null
        }
    }

    fun cacheArticle(id: Long, article: Article) {
        writableDatabase.update("items", ContentValues().apply {
            put("article_html", article.html)
            put("article_title", article.title)
            put("article_byline", article.byline)
        }, "id = ?", arrayOf(id.toString()))
    }

    /** Removes read, unstarred items older than [days] days. */
    fun prune(days: Int) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        writableDatabase.execSQL(
            "DELETE FROM items WHERE starred = 0 AND read = 1 AND published < ?",
            arrayOf<Any>(cutoff),
        )
        // Unread items are kept twice as long before they go too.
        writableDatabase.execSQL(
            "DELETE FROM items WHERE starred = 0 AND published < ?",
            arrayOf<Any>(cutoff - days * 24L * 60 * 60 * 1000),
        )
    }

    private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)

    private fun Cursor.toItem() = FeedItem(
        id = getLong(0), feedId = getLong(1), feedTitle = getString(2), title = getString(3),
        link = str(4), author = str(5), published = getLong(6), summary = str(7),
        content = str(8), image = str(9), read = getInt(10) != 0, starred = getInt(11) != 0,
    )

    companion object {
        private const val VERSION = 1

        @Volatile private var instance: FeedDatabase? = null

        fun get(context: Context): FeedDatabase =
            instance ?: synchronized(this) {
                instance ?: FeedDatabase(context).also { instance = it }
            }
    }
}

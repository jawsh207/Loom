/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/** Everything the feed UI and the background refresh do with feeds. */
class FeedRepository private constructor(context: Context) {

    private val app = context.applicationContext
    private val db = FeedDatabase.get(app)

    private val _version = MutableStateFlow(0L)
    /** Changes whenever feeds or items change, so screens can reload. */
    val version: StateFlow<Long> = _version.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val articleCache = ConcurrentHashMap<Long, Article>()

    private fun changed() {
        _version.value = _version.value + 1
    }

    suspend fun feeds(): List<Feed> = io { db.feeds() }

    suspend fun items(filter: FeedFilter): List<FeedItem> = io { db.items(filter) }

    suspend fun item(id: Long): FeedItem? = io { db.item(id) }

    /**
     * Subscribes to [input], which may be a feed URL or a website (its feed is looked up).
     * Returns the new feed or throws an exception with a message for the user.
     */
    suspend fun addFeed(input: String, folder: String?): Feed = io {
        val url = normalizeUrl(input)
        db.feedByUrl(url)?.let { return@io it }
        val (feedUrl, parsed) = discover(url)
        db.feedByUrl(feedUrl)?.let { return@io it }
        val id = db.insertFeed(feedUrl, parsed.title ?: URL(feedUrl).host, parsed.siteUrl, folder)
        db.insertItems(id, parsed.items)
        db.recordFetch(id, null, null, null, null, parsed.siteUrl)
        changed()
        db.feed(id)!!
    }

    /** Adds every feed from an OPML file; returns how many were new. */
    suspend fun importOpml(input: InputStream): Int {
        val entries = io { Opml.read(input) }
        var added = 0
        io {
            for (e in entries) {
                if (db.feedByUrl(e.url) == null) {
                    db.insertFeed(e.url, e.title?.takeIf { it.isNotBlank() } ?: e.url, null,
                        e.folder)
                    added++
                }
            }
        }
        changed()
        refresh(force = true)
        return added
    }

    suspend fun exportOpml(output: OutputStream) = io { Opml.write(output, db.feeds()) }

    suspend fun rename(feedId: Long, title: String) = io { db.renameFeed(feedId, title) }
        .also { changed() }

    suspend fun setFolder(feedId: Long, folder: String?) = io { db.setFolder(feedId, folder) }
        .also { changed() }

    suspend fun delete(feedId: Long) = io { db.deleteFeed(feedId) }.also { changed() }

    suspend fun setRead(itemId: Long, read: Boolean) = io { db.setRead(itemId, read) }
        .also { changed() }

    suspend fun setStarred(itemId: Long, starred: Boolean) =
        io { db.setStarred(itemId, starred) }.also { changed() }

    suspend fun markAllRead(filter: FeedFilter) = io { db.markAllRead(filter) }
        .also { changed() }

    /**
     * Fetches new items. Without [force], feeds checked in the last few minutes are skipped.
     * Returns the number of new items.
     */
    suspend fun refresh(force: Boolean = false, feedId: Long? = null): Int {
        if (_refreshing.value && feedId == null) return 0
        _refreshing.value = true
        try {
            val feeds = io { db.feeds() }.filter { feedId == null || it.id == feedId }
            val now = System.currentTimeMillis()
            val due = feeds.filter { force || now - it.lastChecked > 5 * 60 * 1000 }
            val gate = Semaphore(4)
            val added = coroutineScope {
                due.map { feed -> async(Dispatchers.IO) { gate.withPermit { refreshOne(feed) } } }
                    .awaitAll().sum()
            }
            io { db.prune(FeedPrefs.keepDays(app)) }
            changed()
            return added
        } finally {
            _refreshing.value = false
        }
    }

    private fun refreshOne(feed: Feed): Int {
        val (etag, lastModified) = db.cacheHeaders(feed.id)
        return try {
            val r = FeedHttp.get(feed.url, FEED_ACCEPT, MAX_FEED_BYTES, etag, lastModified)
            if (r.notModified) {
                db.recordFetch(feed.id, etag, lastModified, null, null, null)
                return 0
            }
            val parsed = FeedParser.parse(r.body, r.url)
            val added = db.insertItems(feed.id, parsed.items)
            db.recordFetch(feed.id, r.etag, r.lastModified, null, parsed.title, parsed.siteUrl)
            added
        } catch (e: Exception) {
            Log.w(TAG, "Refreshing ${feed.url} failed", e)
            db.recordFetch(feed.id, null, null, describe(e), null, null)
            0
        }
    }

    /**
     * The readable version of an item. Uses the cached extraction when there is one, otherwise
     * fetches the page; if that fails, falls back to the feed's own text.
     */
    suspend fun article(item: FeedItem): Article {
        articleCache[item.id]?.let { return it }
        io { db.cachedArticle(item.id) }?.let { articleCache[item.id] = it; return it }
        val link = item.link
        val extracted = if (link != null && link.startsWith("http")) {
            io {
                try {
                    ArticleExtractor.extract(link)
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't load $link", e)
                    null
                }
            }
        } else {
            null
        }
        // The feed's full text can be better than a poor extraction.
        val feedHtml = item.content ?: item.summary
        val feedTextLength = feedHtml?.let { io { Jsoup.parse(it).text().length } } ?: 0
        val article = if (extracted != null && (feedTextLength < 1500 ||
                io { Jsoup.parse(extracted.html).text().length } >= feedTextLength / 2)) {
            extracted.copy(title = item.title.ifBlank { extracted.title })
                .also { io { db.cacheArticle(item.id, it) } }
        } else {
            Article(item.title, item.author, feedHtml ?: "", link, extracted = extracted != null)
        }
        articleCache[item.id] = article
        return article
    }

    // --- Discovery ---

    /** Finds the feed for [url]: the URL itself, a feed the page links to, or a usual path. */
    private fun discover(url: String): Pair<String, ParsedFeed> {
        val r = FeedHttp.get(url, FEED_ACCEPT + ",text/html;q=0.8", MAX_FEED_BYTES)
        try {
            return r.url to FeedParser.parse(r.body, r.url)
        } catch (e: FeedParser.NotAFeedException) {
            // A web page: look for <link rel="alternate" type="application/rss+xml"> etc.
        }
        val doc = Jsoup.parse(r.body.inputStream(), null, r.url)
        val candidates = doc.select("link[rel~=(?i)alternate][href]")
            .filter {
                val type = it.attr("type").lowercase()
                type.contains("rss") || type.contains("atom") || type.contains("feed+json") ||
                    type.contains("application/json")
            }
            .map { it.absUrl("href") }
            .toMutableList()
        val root = URL(URL(r.url), "/").toString().trimEnd('/')
        candidates += listOf("/feed", "/rss", "/feed.xml", "/rss.xml", "/atom.xml",
            "/index.xml", "/feed/", "/rss/").map { root + it }
        for (candidate in candidates.distinct()) {
            try {
                val fr = FeedHttp.get(candidate, FEED_ACCEPT, MAX_FEED_BYTES)
                return fr.url to FeedParser.parse(fr.body, fr.url)
            } catch (e: Exception) {
                // try the next one
            }
        }
        throw IOException("No feed found at this address")
    }

    private fun normalizeUrl(input: String): String {
        var s = input.trim()
        if (s.startsWith("feed://")) s = "https://" + s.removePrefix("feed://")
        if (!s.contains("://")) s = "https://$s"
        URL(s) // throws on nonsense
        return s
    }

    private fun describe(e: Exception): String = when (e) {
        is FeedParser.NotAFeedException -> "Not a feed"
        is FeedHttp.HttpException -> "Server answered ${e.code}"
        is java.net.UnknownHostException -> "Couldn't reach the server"
        is java.net.SocketTimeoutException -> "Timed out"
        is javax.net.ssl.SSLException -> "Secure connection failed"
        else -> e.message ?: e.javaClass.simpleName
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        private const val TAG = "FolioFeeds"
        private const val MAX_FEED_BYTES = 10 * 1024 * 1024
        private const val FEED_ACCEPT = "application/rss+xml,application/atom+xml," +
            "application/feed+json,application/xml;q=0.9,text/xml;q=0.9,*/*;q=0.5"

        @Volatile private var instance: FeedRepository? = null

        fun get(context: Context): FeedRepository =
            instance ?: synchronized(this) {
                instance ?: FeedRepository(context).also { instance = it }
            }

        /** A user-facing reason for a failed add. */
        fun messageFor(e: Throwable): String = when (e) {
            is java.net.MalformedURLException -> "That doesn't look like a web address"
            is java.net.UnknownHostException -> "Couldn't reach that address"
            is FeedHttp.HttpException -> "The server answered ${e.code}"
            is java.net.SocketTimeoutException -> "The server took too long to answer"
            else -> e.message ?: "Something went wrong"
        }
    }
}

/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

/** A subscribed feed. */
data class Feed(
    val id: Long,
    val url: String,
    val title: String,
    val siteUrl: String?,
    val folder: String?,
    val lastChecked: Long,
    val lastError: String?,
    val unread: Int = 0,
)

/** One entry of a feed. Content fields hold HTML as the feed provided it. */
data class FeedItem(
    val id: Long,
    val feedId: Long,
    val feedTitle: String,
    val title: String,
    val link: String?,
    val author: String?,
    val published: Long,
    val summary: String?,
    val content: String?,
    val image: String?,
    val read: Boolean,
    val starred: Boolean,
)

/** The readable article for an item: extracted from its web page, or the feed's own text. */
data class Article(
    val title: String,
    val byline: String?,
    val html: String,
    val baseUrl: String?,
    /** False when the page couldn't be fetched or read and [html] is the feed's own text. */
    val extracted: Boolean,
)

/** What the feed list shows. */
data class FeedFilter(
    val feedId: Long? = null,
    val folder: String? = null,
    val unreadOnly: Boolean = false,
    val starredOnly: Boolean = false,
)

/** A feed as read from the network, before it is stored. */
data class ParsedFeed(
    val title: String?,
    val siteUrl: String?,
    val items: List<ParsedItem>,
)

data class ParsedItem(
    val guid: String,
    val title: String,
    val link: String?,
    val author: String?,
    val published: Long,
    val summary: String?,
    val content: String?,
    val image: String?,
)

/** An entry of an OPML file. */
data class OpmlEntry(val url: String, val title: String?, val folder: String?)

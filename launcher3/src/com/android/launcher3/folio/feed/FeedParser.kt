/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.util.Xml
import java.io.ByteArrayInputStream
import java.net.URL
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.xmlpull.v1.XmlPullParser

/** Reads RSS 0.9x/1.0/2.0, Atom and JSON Feed. */
object FeedParser {

    private const val ATOM = "http://www.w3.org/2005/Atom"
    private const val CONTENT = "http://purl.org/rss/1.0/modules/content/"
    private const val DC = "http://purl.org/dc/elements/1.1/"
    private const val MEDIA = "http://search.yahoo.com/mrss/"
    private const val XHTML = "http://www.w3.org/1999/xhtml"
    private const val RSS1 = "http://purl.org/rss/1.0/"

    /** RSS 2.0 elements have no namespace; RSS 1.0 (RDF) ones have their own. */
    private fun isPlain(ns: String) = ns.isEmpty() || ns == RSS1

    class NotAFeedException : Exception("Not a feed")

    /** Parses [body] fetched from [url]; throws [NotAFeedException] if it isn't a feed. */
    fun parse(body: ByteArray, url: String): ParsedFeed {
        var start = 0
        if (body.size >= 3 && body[0] == 0xEF.toByte() && body[1] == 0xBB.toByte() &&
                body[2] == 0xBF.toByte()) {
            start = 3 // UTF-8 byte order mark
        }
        while (start < body.size && body[start].toInt().toChar().isWhitespace()) start++
        if (start >= body.size) throw NotAFeedException()
        return when (body[start].toInt().toChar()) {
            '{' -> parseJson(String(body, Charsets.UTF_8), url)
            '<' -> parseXml(body, url)
            else -> throw NotAFeedException()
        }
    }

    // --- XML (RSS and Atom) ---

    private fun parseXml(body: ByteArray, url: String): ParsedFeed {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        try {
            p.setInput(ByteArrayInputStream(body), null)
            while (p.next() != XmlPullParser.START_TAG) {
                if (p.eventType == XmlPullParser.END_DOCUMENT) throw NotAFeedException()
            }
            return when (p.name.lowercase(Locale.ROOT)) {
                "rss", "rdf" -> parseRss(p, url)
                "feed" -> parseAtom(p, url)
                else -> throw NotAFeedException()
            }
        } catch (e: NotAFeedException) {
            throw e
        } catch (e: Exception) {
            // HTML and broken XML both end up here.
            throw NotAFeedException()
        }
    }

    private fun parseRss(p: XmlPullParser, url: String): ParsedFeed {
        var title: String? = null
        var link: String? = null
        val items = mutableListOf<ParsedItem>()
        val rootDepth = p.depth
        while (!(p.next() == XmlPullParser.END_TAG && p.depth == rootDepth)) {
            if (p.eventType == XmlPullParser.END_DOCUMENT) break
            if (p.eventType != XmlPullParser.START_TAG) continue
            when {
                p.name == "item" -> items += parseRssItem(p, url)
                p.name == "title" && isPlain(p.namespace) && title == null && p.depth <= 3 ->
                    title = text(p)
                p.name == "link" && isPlain(p.namespace) && link == null && p.depth <= 3 ->
                    link = text(p)
                p.name == "channel" || p.name == "rss" || p.name == "RDF" -> Unit // descend
                p.depth > rootDepth + 1 && p.name != "channel" -> skip(p)
            }
        }
        return ParsedFeed(cleanTitle(title), resolve(url, link), items)
    }

    private fun parseRssItem(p: XmlPullParser, url: String): ParsedItem {
        val depth = p.depth
        var title: String? = null
        var link: String? = null
        var guid: String? = null
        var author: String? = null
        var date: Long = 0
        var summary: String? = null
        var content: String? = null
        var image: String? = null
        while (!(p.next() == XmlPullParser.END_TAG && p.depth == depth)) {
            if (p.eventType == XmlPullParser.END_DOCUMENT) break
            if (p.eventType != XmlPullParser.START_TAG) continue
            val ns = p.namespace
            when {
                p.name == "title" && isPlain(ns) -> title = text(p)
                p.name == "link" && isPlain(ns) -> link = text(p)
                p.name == "link" && ns == ATOM -> {
                    if (link == null && p.getAttributeValue(null, "rel").orEmpty()
                            .let { it.isEmpty() || it == "alternate" }) {
                        link = p.getAttributeValue(null, "href")
                    }
                    skip(p)
                }
                p.name == "guid" -> guid = text(p)
                p.name == "pubDate" || (p.name == "date" && ns == DC) ->
                    date = parseDate(text(p)).takeIf { it > 0 } ?: date
                p.name == "description" && isPlain(ns) -> summary = text(p)
                p.name == "encoded" && ns == CONTENT -> content = text(p)
                p.name == "creator" && ns == DC -> author = text(p)
                p.name == "author" && isPlain(ns) -> author = text(p)
                p.name == "enclosure" -> {
                    val type = p.getAttributeValue(null, "type").orEmpty()
                    if (image == null && type.startsWith("image/")) {
                        image = p.getAttributeValue(null, "url")
                    }
                    skip(p)
                }
                (p.name == "thumbnail" || p.name == "content") && ns == MEDIA -> {
                    val medium = p.getAttributeValue(null, "medium").orEmpty()
                    val type = p.getAttributeValue(null, "type").orEmpty()
                    val u = p.getAttributeValue(null, "url")
                    if (image == null && u != null && (p.name == "thumbnail" ||
                            medium == "image" || type.startsWith("image/"))) {
                        image = u
                    }
                    if (p.name == "content") {
                        // media:content can wrap a media:thumbnail.
                        readMediaChildren(p)?.let { if (image == null) image = it }
                    } else {
                        skip(p)
                    }
                }
                else -> skip(p)
            }
        }
        val resolvedLink = resolve(url, link?.trim())
            ?: guid?.takeIf { it.startsWith("http") }
        return ParsedItem(
            guid = guid?.trim()?.takeIf { it.isNotEmpty() } ?: resolvedLink
                ?: (title + date),
            title = cleanTitle(title) ?: "(untitled)",
            link = resolvedLink,
            author = author?.trim()?.takeIf { it.isNotEmpty() },
            published = date,
            summary = summary,
            content = content,
            image = resolve(url, image) ?: firstImage(content ?: summary, resolvedLink ?: url),
        )
    }

    private fun readMediaChildren(p: XmlPullParser): String? {
        var found: String? = null
        val depth = p.depth
        while (!(p.next() == XmlPullParser.END_TAG && p.depth == depth)) {
            if (p.eventType == XmlPullParser.END_DOCUMENT) break
            if (p.eventType == XmlPullParser.START_TAG && p.name == "thumbnail" &&
                    found == null) {
                found = p.getAttributeValue(null, "url")
            }
        }
        return found
    }

    private fun parseAtom(p: XmlPullParser, url: String): ParsedFeed {
        var title: String? = null
        var link: String? = null
        val items = mutableListOf<ParsedItem>()
        val rootDepth = p.depth
        while (!(p.next() == XmlPullParser.END_TAG && p.depth == rootDepth)) {
            if (p.eventType == XmlPullParser.END_DOCUMENT) break
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "entry" -> items += parseAtomEntry(p, url)
                "title" -> title = atomText(p)
                "link" -> {
                    val rel = p.getAttributeValue(null, "rel").orEmpty()
                    if (rel.isEmpty() || rel == "alternate") {
                        link = p.getAttributeValue(null, "href")
                    }
                    skip(p)
                }
                else -> skip(p)
            }
        }
        return ParsedFeed(cleanTitle(title), resolve(url, link), items)
    }

    private fun parseAtomEntry(p: XmlPullParser, url: String): ParsedItem {
        val depth = p.depth
        var title: String? = null
        var link: String? = null
        var id: String? = null
        var author: String? = null
        var published: Long = 0
        var updated: Long = 0
        var summary: String? = null
        var content: String? = null
        var image: String? = null
        while (!(p.next() == XmlPullParser.END_TAG && p.depth == depth)) {
            if (p.eventType == XmlPullParser.END_DOCUMENT) break
            if (p.eventType != XmlPullParser.START_TAG) continue
            when {
                p.name == "title" -> title = atomText(p)
                p.name == "link" -> {
                    val rel = p.getAttributeValue(null, "rel").orEmpty()
                    val type = p.getAttributeValue(null, "type").orEmpty()
                    val href = p.getAttributeValue(null, "href")
                    if ((rel.isEmpty() || rel == "alternate") && link == null) link = href
                    if (rel == "enclosure" && type.startsWith("image/") && image == null) {
                        image = href
                    }
                    skip(p)
                }
                p.name == "id" -> id = text(p)
                p.name == "published" -> published = parseDate(text(p))
                p.name == "updated" -> updated = parseDate(text(p))
                p.name == "summary" -> summary = atomText(p)
                p.name == "content" -> content = atomText(p)
                p.name == "author" -> author = readAtomAuthor(p)
                p.name == "thumbnail" && p.namespace == MEDIA -> {
                    if (image == null) image = p.getAttributeValue(null, "url")
                    skip(p)
                }
                p.name == "group" && p.namespace == MEDIA -> {
                    readMediaChildren(p)?.let { if (image == null) image = it }
                }
                else -> skip(p)
            }
        }
        val resolvedLink = resolve(url, link)
        return ParsedItem(
            guid = id?.trim()?.takeIf { it.isNotEmpty() } ?: resolvedLink ?: (title + updated),
            title = cleanTitle(title) ?: "(untitled)",
            link = resolvedLink,
            author = author,
            published = if (published > 0) published else updated,
            summary = summary,
            content = content,
            image = resolve(url, image) ?: firstImage(content ?: summary, resolvedLink ?: url),
        )
    }

    private fun readAtomAuthor(p: XmlPullParser): String? {
        var name: String? = null
        val depth = p.depth
        while (!(p.next() == XmlPullParser.END_TAG && p.depth == depth)) {
            if (p.eventType == XmlPullParser.END_DOCUMENT) break
            if (p.eventType == XmlPullParser.START_TAG) {
                if (p.name == "name") name = text(p) else skip(p)
            }
        }
        return name?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Atom text constructs: text, html, or inline xhtml (serialised back to markup). */
    private fun atomText(p: XmlPullParser): String {
        val type = p.getAttributeValue(null, "type").orEmpty()
        if (type != "xhtml") {
            val t = text(p)
            return if (type == "html" || type.contains("html")) t else escapeHtml(t)
        }
        val sb = StringBuilder()
        val depth = p.depth
        while (!(p.next() == XmlPullParser.END_TAG && p.depth == depth)) {
            when (p.eventType) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> {
                    if (p.depth == depth + 1 && p.name == "div" && p.namespace == XHTML) {
                        continue // the required wrapper div
                    }
                    sb.append('<').append(p.name)
                    for (i in 0 until p.attributeCount) {
                        sb.append(' ').append(p.getAttributeName(i)).append("=\"")
                            .append(escapeHtml(p.getAttributeValue(i))).append('"')
                    }
                    sb.append('>')
                }
                XmlPullParser.END_TAG -> {
                    if (!(p.depth == depth + 1 && p.name == "div")) {
                        sb.append("</").append(p.name).append('>')
                    }
                }
                XmlPullParser.TEXT -> sb.append(escapeHtml(p.text))
            }
        }
        return sb.toString()
    }

    private fun text(p: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = p.depth
        while (!(p.next() == XmlPullParser.END_TAG && p.depth == depth)) {
            when (p.eventType) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.TEXT -> sb.append(p.text)
            }
        }
        return sb.toString()
    }

    private fun skip(p: XmlPullParser) {
        if (p.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    // --- JSON Feed ---

    private fun parseJson(text: String, url: String): ParsedFeed {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw NotAFeedException()
        }
        if (!root.optString("version").contains("jsonfeed.org")) throw NotAFeedException()
        val items = mutableListOf<ParsedItem>()
        val arr = root.optJSONArray("items")
        for (i in 0 until (arr?.length() ?: 0)) {
            val o = arr!!.optJSONObject(i) ?: continue
            val link = resolve(url, o.optStringOrNull("url") ?: o.optStringOrNull("external_url"))
            val html = o.optStringOrNull("content_html")
                ?: o.optStringOrNull("content_text")?.let { escapeHtml(it) }
            val authorObj = o.optJSONArray("authors")?.optJSONObject(0)
                ?: o.optJSONObject("author")
            items += ParsedItem(
                guid = o.optStringOrNull("id") ?: link ?: "$i",
                title = cleanTitle(o.optStringOrNull("title"))
                    ?: o.optStringOrNull("summary")?.take(80) ?: "(untitled)",
                link = link,
                author = authorObj?.optStringOrNull("name"),
                published = parseDate(o.optStringOrNull("date_published")
                    ?: o.optStringOrNull("date_modified")),
                summary = o.optStringOrNull("summary")?.let { escapeHtml(it) },
                content = html,
                image = resolve(url, o.optStringOrNull("image")
                    ?: o.optStringOrNull("banner_image")) ?: firstImage(html, link ?: url),
            )
        }
        return ParsedFeed(
            cleanTitle(root.optStringOrNull("title")),
            resolve(url, root.optStringOrNull("home_page_url")),
            items,
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    // --- Helpers ---

    /** Titles may carry entities or stray markup; reduce them to plain text. */
    fun cleanTitle(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        val plain = if (t.contains('<') || t.contains('&')) Jsoup.parse(t).text() else t
        return plain.replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() }
    }

    fun resolve(base: String, link: String?): String? {
        if (link.isNullOrBlank()) return null
        return try {
            URL(URL(base), link.trim()).toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun firstImage(html: String?, base: String): String? {
        if (html.isNullOrBlank() || !html.contains("<img", ignoreCase = true)) return null
        return try {
            Jsoup.parse(html, base).select("img[src]").firstOrNull { img ->
                val w = img.attr("width").toIntOrNull() ?: 100
                val h = img.attr("height").toIntOrNull() ?: 100
                w > 2 && h > 2 // skip tracking pixels
            }?.absUrl("src")?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun escapeHtml(s: String) = Parser.unescapeEntities(s, false)
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val RFC822 = listOf(
        "EEE, d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm Z",
        "EEE, d MMM yyyy HH:mm zzz", "d MMM yyyy HH:mm:ss Z", "d MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yy HH:mm:ss Z", "EEE, d MMM yy HH:mm:ss zzz", "EEE, d MMM yyyy",
        "yyyy-MM-dd HH:mm:ss",
    )

    /** Parses the date formats feeds use in practice; 0 if unknown. */
    fun parseDate(raw: String?): Long {
        val s = raw?.trim()?.replace(Regex("\\s+"), " ") ?: return 0
        if (s.isEmpty()) return 0
        if (s.length >= 10 && s[4] == '-') {
            runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            runCatching {
                return OffsetDateTime.parse(s.replace(' ', 'T') + "Z",
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
            }
            runCatching {
                return LocalDate.parse(s.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }
        }
        // Some feeds use "UT" or "Z" as the zone, which SimpleDateFormat doesn't know.
        val fixed = s.replace(Regex(" (UT|Z)$"), " +0000")
        for (pattern in RFC822) {
            val f = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = true
            }
            val pos = ParsePosition(0)
            val d = f.parse(fixed, pos)
            if (d != null && pos.index > 0) return d.time
        }
        return 0
    }
}

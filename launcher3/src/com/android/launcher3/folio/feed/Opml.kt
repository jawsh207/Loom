/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.util.Xml
import java.io.InputStream
import java.io.OutputStream
import org.xmlpull.v1.XmlPullParser

/** OPML import and export: the format feed readers use to move subscriptions around. */
object Opml {

    /** Reads every feed in an OPML file. Nested outlines without a feed URL become folders. */
    fun read(input: InputStream): List<OpmlEntry> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)
        val out = mutableListOf<OpmlEntry>()
        val folders = ArrayDeque<String?>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> if (p.name.equals("outline", ignoreCase = true)) {
                    val url = attr(p, "xmlUrl") ?: attr(p, "xmlurl") ?: attr(p, "url")
                    val title = attr(p, "title") ?: attr(p, "text")
                    if (url != null && (url.startsWith("http://") ||
                            url.startsWith("https://"))) {
                        out += OpmlEntry(url.trim(), title, folders.lastOrNull { it != null })
                        folders.addLast(null)
                    } else {
                        folders.addLast(title)
                    }
                }
                XmlPullParser.END_TAG -> if (p.name.equals("outline", ignoreCase = true)) {
                    folders.removeLastOrNull()
                }
            }
        }
        return out.distinctBy { it.url }
    }

    private fun attr(p: XmlPullParser, name: String): String? =
        p.getAttributeValue(null, name)?.takeIf { it.isNotBlank() }

    fun write(output: OutputStream, feeds: List<Feed>) {
        val s = Xml.newSerializer()
        s.setOutput(output, "UTF-8")
        s.startDocument("UTF-8", true)
        s.startTag(null, "opml").attribute(null, "version", "2.0")
        s.startTag(null, "head")
        s.startTag(null, "title").text("Folio feeds").endTag(null, "title")
        s.endTag(null, "head")
        s.startTag(null, "body")
        val byFolder = feeds.groupBy { it.folder }
        for ((folder, list) in byFolder.entries.sortedBy { it.key ?: "" }) {
            if (folder != null) {
                s.startTag(null, "outline").attribute(null, "text", folder)
                    .attribute(null, "title", folder)
            }
            for (f in list) {
                s.startTag(null, "outline")
                    .attribute(null, "type", "rss")
                    .attribute(null, "text", f.title)
                    .attribute(null, "title", f.title)
                    .attribute(null, "xmlUrl", f.url)
                f.siteUrl?.let { s.attribute(null, "htmlUrl", it) }
                s.endTag(null, "outline")
            }
            if (folder != null) s.endTag(null, "outline")
        }
        s.endTag(null, "body")
        s.endTag(null, "opml")
        s.endDocument()
        s.flush()
    }
}

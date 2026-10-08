/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.util.Log
import java.nio.charset.Charset
import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Turns an article's web page into just the article: title, byline and the main text with its
 * images. Uses Readability4J, a port of the algorithm behind Firefox's Reader View, with a
 * simpler fallback of our own.
 */
object ArticleExtractor {

    private const val TAG = "FolioArticle"
    private const val MAX_PAGE_BYTES = 8 * 1024 * 1024
    /** Less readable text than this means extraction didn't find the article. */
    private const val MIN_TEXT = 280

    /** Fetches and extracts [url]; returns null if the page has no recognisable article. */
    fun extract(url: String): Article? {
        val response = FeedHttp.get(url,
            "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5", MAX_PAGE_BYTES)
        val charset = response.contentType
            ?.let { Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(it) }
            ?.groupValues?.get(1)
            ?.let { runCatching { Charset.forName(it) }.getOrNull() }
        val doc = Jsoup.parse(response.body.inputStream(), charset?.name(), response.url)
        return extract(doc, response.url)
    }

    fun extract(doc: Document, url: String): Article? {
        lazyLoadedImages(doc)
        absoluteUrls(doc)
        try {
            val a = Readability4J(url, doc.outerHtml()).parse()
            val html = a.content
            val text = a.textContent.orEmpty().trim()
            if (html != null && text.length >= MIN_TEXT) {
                return Article(
                    title = a.title?.trim().orEmpty().ifEmpty { doc.title() },
                    byline = a.byline?.trim()?.takeIf { it.isNotEmpty() && it.length < 120 },
                    html = html,
                    baseUrl = url,
                    extracted = true,
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Readability failed for $url", t)
        }
        return fallback(doc, url)
    }

    /** Many sites only fill in real image URLs from JavaScript; take them from data-* attrs. */
    private fun lazyLoadedImages(doc: Document) {
        for (img in doc.select("img")) {
            val src = img.attr("src")
            val real = listOf("data-src", "data-lazy-src", "data-original", "data-url")
                .map { img.attr(it) }.firstOrNull { it.isNotBlank() }
                ?: img.attr("data-srcset").ifBlank { img.attr("srcset") }
                    .split(',').map { it.trim().substringBefore(' ') }
                    .lastOrNull { it.isNotBlank() }
            if (real != null && (src.isBlank() || src.startsWith("data:"))) {
                img.attr("src", real)
            }
        }
        // <noscript> often holds the non-JavaScript version of an image.
        for (ns in doc.select("noscript")) {
            val inner = Jsoup.parseBodyFragment(ns.html()).select("img").firstOrNull()
            if (inner != null && ns.parent() != null) ns.replaceWith(inner)
        }
    }

    /**
     * Makes links and image sources absolute before Readability4J sees them: its own
     * conversion drops the port ("http://host:8080/a.png" becomes "http://host/a.png").
     */
    private fun absoluteUrls(doc: Document) {
        for (attr in listOf("src", "href", "poster")) {
            for (el in doc.select("[$attr]")) {
                val abs = el.absUrl(attr)
                if (abs.isNotEmpty()) el.attr(attr, abs)
            }
        }
        for (el in doc.select("[srcset]")) {
            el.attr("srcset", el.attr("srcset").split(',').filter { it.isNotBlank() }.joinToString(", ") { part ->
                val bits = part.trim().split(Regex("\\s+"), limit = 2)
                val abs = runCatching { java.net.URL(java.net.URL(doc.location()), bits[0]) }
                    .getOrNull()?.toString() ?: bits[0]
                if (bits.size > 1) "$abs ${bits[1]}" else abs
            })
        }
    }

    /** Picks the element with the most paragraph text. */
    private fun fallback(doc: Document, url: String): Article? {
        doc.select("script, style, nav, header, footer, aside, form, iframe, noscript, " +
            "[role=navigation], [aria-hidden=true], .comments, #comments").remove()
        var best: Element? = null
        var bestScore = 0
        for (candidate in doc.select("article, main, [role=main], div, section")) {
            val score = candidate.select("> p, > div > p").sumOf { it.text().length }
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        val el = best ?: return null
        if (el.text().length < MIN_TEXT) return null
        return Article(
            title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?.takeIf { it.isNotBlank() } ?: doc.title(),
            byline = doc.selectFirst("meta[name=author]")?.attr("content")
                ?.takeIf { it.isNotBlank() },
            html = el.outerHtml(),
            baseUrl = url,
            extracted = true,
        )
    }
}

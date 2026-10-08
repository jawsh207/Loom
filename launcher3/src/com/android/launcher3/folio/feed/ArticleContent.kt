/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** A piece of an article, rendered natively instead of in a web view. */
sealed interface ArticleBlock {
    data class Paragraph(val text: AnnotatedString) : ArticleBlock
    data class Heading(val level: Int, val text: AnnotatedString) : ArticleBlock
    data class Image(val url: String, val alt: String?, val caption: AnnotatedString?) :
        ArticleBlock
    data class Quote(val text: AnnotatedString) : ArticleBlock
    data class ListItem(val marker: String, val depth: Int, val text: AnnotatedString) :
        ArticleBlock
    data class Code(val text: String) : ArticleBlock
    data class Table(val rows: List<List<AnnotatedString>>) : ArticleBlock
    data class Embed(val url: String) : ArticleBlock
    data object Divider : ArticleBlock
}

/** Converts article HTML into [ArticleBlock]s. */
class ArticleHtml(private val linkColor: Color) {

    private val out = mutableListOf<ArticleBlock>()
    private var line = AnnotatedString.Builder()
    private var lastWasSpace = true
    private val seenImages = HashSet<String>()

    fun parse(html: String, baseUrl: String?, skipImage: String?): List<ArticleBlock> {
        skipImage?.let { seenImages += it }
        val body = Jsoup.parse(html, baseUrl ?: "").body()
        blocks(body)
        flushParagraph()
        return out
    }

    private fun blocks(el: Element) {
        for (node in el.childNodes()) block(node)
    }

    private fun block(node: Node) {
        if (node is TextNode) {
            appendText(node.text())
            return
        }
        if (node !is Element) return
        when (val tag = node.normalName()) {
            "script", "style", "noscript", "svg", "form", "button", "input", "select",
            "textarea", "nav", "template" -> Unit
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                flushParagraph()
                val text = inlineOf(node)
                if (text.isNotBlank()) out += ArticleBlock.Heading(tag[1] - '0', text)
            }
            "p", "div", "section", "article", "main", "header", "footer", "aside", "center",
            "details", "summary", "dl", "dd", "dt" -> {
                flushParagraph()
                blocks(node)
                flushParagraph()
            }
            "br" -> newline()
            "hr" -> { flushParagraph(); out += ArticleBlock.Divider }
            "img" -> image(node, null)
            "picture" -> node.selectFirst("img")?.let { image(it, null) }
            "figure" -> {
                flushParagraph()
                val caption = node.selectFirst("figcaption")?.let { inlineOf(it) }
                val img = node.selectFirst("img")
                if (img != null) {
                    image(img, caption)
                } else {
                    node.selectFirst("figcaption")?.remove()
                    blocks(node)
                    flushParagraph()
                    caption?.let { if (it.isNotBlank()) out += ArticleBlock.Paragraph(it) }
                }
            }
            "blockquote" -> {
                flushParagraph()
                val text = inlineOf(node, blockBreaks = true)
                if (text.isNotBlank()) out += ArticleBlock.Quote(text)
                node.select("img").forEach { image(it, null) }
            }
            "ul", "ol" -> list(node, 0)
            "pre" -> {
                flushParagraph()
                out += ArticleBlock.Code(node.wholeText().trimEnd())
            }
            "table" -> table(node)
            "iframe", "video", "audio", "embed", "object" -> {
                flushParagraph()
                val src = node.absUrl("src").ifEmpty {
                    node.selectFirst("source[src]")?.absUrl("src").orEmpty()
                }
                if (src.startsWith("http")) out += ArticleBlock.Embed(src)
            }
            else -> inline(node, line) // inline element directly in a block container
        }
    }

    private fun list(listEl: Element, depth: Int) {
        flushParagraph()
        var n = listEl.attr("start").toIntOrNull() ?: 1
        val ordered = listEl.normalName() == "ol"
        for (li in listEl.children()) {
            if (li.normalName() != "li") continue
            val nested = li.children().filter { it.normalName() == "ul" || it.normalName() == "ol" }
            nested.forEach { it.remove() }
            val text = inlineOf(li, blockBreaks = true)
            if (text.isNotBlank()) {
                out += ArticleBlock.ListItem(if (ordered) "${n}." else "•", depth, text)
            }
            n++
            nested.forEach { list(it, depth + 1) }
        }
    }

    private fun table(t: Element) {
        flushParagraph()
        val rows = t.select("tr").map { tr ->
            tr.children().filter { it.normalName() == "td" || it.normalName() == "th" }
                .map { inlineOf(it) }
        }.filter { it.isNotEmpty() }
        if (rows.isNotEmpty()) out += ArticleBlock.Table(rows)
    }

    private fun image(img: Element, caption: AnnotatedString?) {
        val url = img.absUrl("src").ifEmpty { img.attr("src") }
        if (!url.startsWith("http") || !seenImages.add(url)) return
        val w = img.attr("width").toIntOrNull()
        val h = img.attr("height").toIntOrNull()
        if ((w != null && w <= 2) || (h != null && h <= 2)) return
        flushParagraph()
        out += ArticleBlock.Image(url, img.attr("alt").takeIf { it.isNotBlank() },
            caption?.takeIf { it.isNotBlank() })
    }

    private fun inlineOf(el: Element, blockBreaks: Boolean = false): AnnotatedString {
        val saved = line
        val savedSpace = lastWasSpace
        line = AnnotatedString.Builder()
        lastWasSpace = true
        for (n in el.childNodes()) inlineNode(n, line, blockBreaks)
        val result = trim(line.toAnnotatedString())
        line = saved
        lastWasSpace = savedSpace
        return result
    }

    private fun inline(el: Element, b: AnnotatedString.Builder) = inlineNode(el, b, false)

    private fun inlineNode(node: Node, b: AnnotatedString.Builder, blockBreaks: Boolean) {
        if (node is TextNode) {
            appendText(node.text(), b)
            return
        }
        if (node !is Element) return
        val style: SpanStyle? = when (node.normalName()) {
            "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
            "i", "em", "cite", "dfn" -> SpanStyle(fontStyle = FontStyle.Italic)
            "u", "ins" -> SpanStyle(textDecoration = TextDecoration.Underline)
            "s", "del", "strike" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            "code", "kbd", "samp", "tt" -> SpanStyle(fontFamily = FontFamily.Monospace,
                background = linkColor.copy(alpha = 0.08f))
            "sup" -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em)
            "sub" -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em)
            "mark" -> SpanStyle(background = linkColor.copy(alpha = 0.2f))
            else -> null
        }
        when (node.normalName()) {
            "script", "style", "noscript", "svg", "img", "picture", "figure", "iframe",
            "video", "audio", "button", "form" -> return
            "br" -> { b.append('\n'); lastWasSpace = true; return }
            "p", "div", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6" ->
                if (blockBreaks && b.length > 0) { b.append("\n\n"); lastWasSpace = true }
        }
        val href = if (node.normalName() == "a") node.absUrl("href") else ""
        val pushed = mutableListOf<Int>()
        if (href.startsWith("http") || href.startsWith("mailto:")) {
            pushed += b.pushLink(LinkAnnotation.Url(href, TextLinkStyles(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))))
        }
        style?.let { pushed += b.pushStyle(it) }
        for (child in node.childNodes()) inlineNode(child, b, blockBreaks)
        repeat(pushed.size) { b.pop() }
    }

    private fun appendText(raw: String, b: AnnotatedString.Builder = line) {
        val collapsed = raw.replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) return
        val text = if (lastWasSpace) collapsed.trimStart() else collapsed
        if (text.isEmpty()) return
        b.append(text)
        lastWasSpace = text.endsWith(' ')
    }

    private fun newline() {
        line.append('\n')
        lastWasSpace = true
    }

    private fun flushParagraph() {
        val text = trim(line.toAnnotatedString())
        if (text.isNotBlank()) out += ArticleBlock.Paragraph(text)
        line = AnnotatedString.Builder()
        lastWasSpace = true
    }

    private fun trim(s: AnnotatedString): AnnotatedString {
        val start = s.text.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return AnnotatedString("")
        val end = s.text.indexOfLast { !it.isWhitespace() } + 1
        return s.subSequence(start, end)
    }
}

/** Adds an article's blocks to a lazy list. */
fun LazyListScope.articleBlocks(blocks: List<ArticleBlock>, body: TextStyle) {
    items(blocks.size) { i -> ArticleBlockView(blocks[i], body) }
}

@Composable
private fun ArticleBlockView(block: ArticleBlock, body: TextStyle) {
    val padding = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
    val colors = MaterialTheme.colorScheme
    when (block) {
        is ArticleBlock.Paragraph -> Text(block.text, padding, style = body)
        is ArticleBlock.Heading -> Text(
            block.text,
            padding.padding(top = 8.dp),
            style = when (block.level) {
                1, 2 -> MaterialTheme.typography.headlineSmall
                3 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }.copy(fontSize = body.fontSize * (if (block.level <= 2) 1.4f else 1.15f)),
        )
        is ArticleBlock.Image -> Column(padding) {
            RemoteImage(
                block.url, block.alt,
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth,
            )
            block.caption?.let {
                Text(it, Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        is ArticleBlock.Quote -> Row(padding.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight()
                .background(colors.primary, RoundedCornerShape(2.dp)))
            Text(block.text, Modifier.padding(start = 16.dp),
                style = body.copy(fontStyle = FontStyle.Italic),
                color = colors.onSurfaceVariant)
        }
        is ArticleBlock.ListItem -> Row(
            padding.padding(start = (block.depth * 20).dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(block.marker, style = body, color = colors.primary)
            Text(block.text, style = body)
        }
        is ArticleBlock.Code -> Surface(
            padding, shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerHighest,
        ) {
            Text(block.text,
                Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
                style = body.copy(fontFamily = FontFamily.Monospace,
                    fontSize = body.fontSize * 0.85f, lineHeight = body.lineHeight * 0.85f))
        }
        is ArticleBlock.Table -> Surface(
            padding, shape = RoundedCornerShape(12.dp), color = colors.surfaceContainer,
        ) {
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                block.rows.forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { cell ->
                            Text(cell, Modifier.width(140.dp),
                                style = MaterialTheme.typography.bodyMedium.let {
                                    if (r == 0) it.copy(fontWeight = FontWeight.Bold) else it
                                })
                        }
                    }
                    if (r < block.rows.lastIndex) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                }
            }
        }
        is ArticleBlock.Embed -> {
            val uri = LocalUriHandler.current
            OutlinedButton({ runCatching { uri.openUri(block.url) } }, padding) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                Text(" Open embedded media")
            }
        }
        ArticleBlock.Divider -> HorizontalDivider(padding.padding(vertical = 8.dp))
    }
}

/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.launcher3.R

/**
 * Reads an article out loud with the system's default text-to-speech engine (the one chosen in
 * Settings › Accessibility › Text-to-speech output).
 *
 * The article is spoken one block at a time, so reading can be paused and picked up again at
 * the same paragraph, and the article view can follow along ([block]).
 */
class FeedSpeaker(context: Context) {

    /** One piece of speech, and the article block it belongs to (-1: the title). */
    class Chunk(val block: Int, val text: String)

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingStart = false

    private var chunks: List<Chunk> = emptyList()
    private var next = 0

    /** The article being read (its item id), or null. */
    var articleId by mutableStateOf<Long?>(null)
        private set
    /** Whether it is speaking right now (false while paused). */
    var speaking by mutableStateOf(false)
        private set
    /** The article block being read (-1 for the title). */
    var block by mutableIntStateOf(-1)
        private set

    /** Starts reading [chunks] of article [id] from the beginning. */
    fun start(id: Long, chunks: List<Chunk>) {
        stop()
        if (chunks.isEmpty()) return
        this.chunks = chunks
        next = 0
        articleId = id
        speaking = true
        block = chunks[0].block
        if (ready) speakFromNext() else { pendingStart = true; ensureEngine() }
    }

    fun pause() {
        if (!speaking) return
        speaking = false
        tts?.stop()
    }

    fun resume() {
        if (articleId == null || speaking) return
        speaking = true
        if (ready) speakFromNext() else { pendingStart = true; ensureEngine() }
    }

    /** Stops and forgets the article. */
    fun stop() {
        pendingStart = false
        speaking = false
        articleId = null
        chunks = emptyList()
        next = 0
        block = -1
        tts?.stop()
    }

    /** Lets the engine go (when the panel goes away for good). */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun ensureEngine() {
        if (tts != null) return
        // No engine package given: the system default.
        tts = TextToSpeech(app) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts?.setOnUtteranceProgressListener(progress)
                    if (pendingStart) {
                        pendingStart = false
                        if (speaking) speakFromNext()
                    }
                } else {
                    noEngine()
                }
            }
        }
    }

    private fun noEngine() {
        tts?.shutdown()
        tts = null
        ready = false
        stop()
        Toast.makeText(app, R.string.feed_listen_no_engine, Toast.LENGTH_LONG).show()
        runCatching {
            app.startActivity(Intent("com.android.settings.TTS_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Queues everything from [next] on; the ids are the chunk indexes. */
    private fun speakFromNext() {
        val engine = tts ?: return
        engine.stop()
        if (next >= chunks.size) { finished(); return }
        for (i in next until chunks.size) {
            val mode = if (i == next) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = engine.speak(chunks[i].text, mode, Bundle(), i.toString())
            if (result == TextToSpeech.ERROR) { if (i == next) noEngine(); return }
        }
    }

    private fun finished() {
        stop()
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            val i = utteranceId.toIntOrNull() ?: return
            main.post {
                if (speaking && i < chunks.size) { next = i; block = chunks[i].block }
            }
        }

        override fun onDone(utteranceId: String) {
            val i = utteranceId.toIntOrNull() ?: return
            main.post {
                if (!speaking || i >= chunks.size) return@post
                next = i + 1
                if (next >= chunks.size) finished()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            main.post { if (speaking) stop() }
        }

        // Interrupted by pause() or stop(): keep [next] where it is.
        override fun onStop(utteranceId: String, interrupted: Boolean) {}
    }

    companion object {
        /**
         * The text to read for an article: its title, then each block that has words in it.
         * Long blocks are cut at sentence ends to stay under the engine's limit.
         */
        fun chunksOf(title: String, blocks: List<ArticleBlock>): List<Chunk> {
            val out = ArrayList<Chunk>()
            fun add(block: Int, raw: String) {
                val text = raw.replace(Regex("\\s+"), " ").trim()
                if (text.isEmpty()) return
                for (piece in split(text)) out.add(Chunk(block, piece))
            }
            add(-1, title)
            blocks.forEachIndexed { i, b ->
                when (b) {
                    is ArticleBlock.Paragraph -> add(i, b.text.text)
                    is ArticleBlock.Heading -> add(i, b.text.text)
                    is ArticleBlock.Quote -> add(i, b.text.text)
                    is ArticleBlock.ListItem -> add(i, b.text.text)
                    is ArticleBlock.Table -> add(i, b.rows.joinToString(". ") { row ->
                        row.joinToString(", ") { it.text }
                    })
                    is ArticleBlock.Image -> b.caption?.let { add(i, it.text) }
                    // Code, embeds and dividers aren't read.
                    else -> {}
                }
            }
            return out
        }

        private const val MAX = 1500

        private fun split(text: String): List<String> {
            if (text.length <= MAX) return listOf(text)
            val out = ArrayList<String>()
            var rest = text
            while (rest.length > MAX) {
                val window = rest.substring(0, MAX)
                var cut = maxOf(window.lastIndexOf(". "), window.lastIndexOf("? "),
                    window.lastIndexOf("! "))
                if (cut < MAX / 3) cut = window.lastIndexOf(' ')
                if (cut <= 0) cut = MAX - 1
                out.add(rest.substring(0, cut + 1).trim())
                rest = rest.substring(cut + 1)
            }
            if (rest.isNotBlank()) out.add(rest.trim())
            return out
        }
    }
}

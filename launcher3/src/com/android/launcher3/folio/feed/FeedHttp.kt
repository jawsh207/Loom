/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Plain HTTP(S) fetching with the platform client: no extra libraries, no tracking. */
object FeedHttp {

    private const val USER_AGENT = "Folio/0.2 (Android; RSS reader)"
    private const val TIMEOUT_MS = 15_000
    private const val MAX_REDIRECTS = 6

    class Response(
        val url: String,
        val code: Int,
        val body: ByteArray,
        val contentType: String?,
        val etag: String?,
        val lastModified: String?,
    ) {
        val notModified get() = code == HttpURLConnection.HTTP_NOT_MODIFIED
    }

    class HttpException(val code: Int, message: String) : IOException(message)

    /**
     * Fetches [url], following redirects (including http → https). Throws [IOException] on
     * network errors and non-2xx answers other than 304.
     */
    fun get(
        url: String,
        accept: String,
        maxBytes: Int,
        etag: String? = null,
        lastModified: String? = null,
    ): Response {
        var current = url
        repeat(MAX_REDIRECTS) {
            val conn = URL(current).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", accept)
                etag?.let { conn.setRequestProperty("If-None-Match", it) }
                lastModified?.let { conn.setRequestProperty("If-Modified-Since", it) }
                val code = conn.responseCode
                if (code in 300..399 && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                    val location = conn.getHeaderField("Location")
                        ?: throw HttpException(code, "Redirect without a location")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    return Response(current, code, ByteArray(0), null, etag, lastModified)
                }
                if (code !in 200..299) {
                    throw HttpException(code, "HTTP $code")
                }
                val body = conn.inputStream.use { readCapped(it, maxBytes) }
                return Response(current, code, body, conn.contentType,
                    conn.getHeaderField("ETag"), conn.getHeaderField("Last-Modified"))
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    private fun readCapped(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > maxBytes) throw IOException("Response too large")
        }
        return out.toByteArray()
    }
}

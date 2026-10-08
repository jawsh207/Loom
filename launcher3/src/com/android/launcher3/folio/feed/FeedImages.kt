/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Loads article and thumbnail images with a memory and a small disk cache. */
object FeedImages {

    private const val MAX_BYTES = 12 * 1024 * 1024
    private const val DISK_LIMIT = 60L * 1024 * 1024

    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val locks = HashMap<String, Mutex>()

    private fun lockFor(key: String) = synchronized(locks) { locks.getOrPut(key) { Mutex() } }

    /** Loads [url] scaled down to about [maxWidth] pixels wide; null if it can't be shown. */
    suspend fun load(context: Context, url: String, maxWidth: Int): Bitmap? {
        if (!url.startsWith("http")) return null
        val key = "$maxWidth:$url"
        memory.get(key)?.let { return it }
        return lockFor(key).withLock {
            memory.get(key) ?: withContext(Dispatchers.IO) {
                runCatching { decode(fetch(context, url), maxWidth) }.getOrNull()
            }?.also { memory.put(key, it) }
        }
    }

    private fun fetch(context: Context, url: String): File {
        val dir = File(context.cacheDir, "feed-images").apply { mkdirs() }
        val file = File(dir, sha1(url))
        if (file.exists() && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        val r = FeedHttp.get(url, "image/avif,image/webp,image/*;q=0.9", MAX_BYTES)
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeBytes(r.body)
        tmp.renameTo(file)
        trim(dir)
        return file
    }

    private fun decode(file: File, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 2 || bounds.outHeight <= 2) return null // tracking pixels
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        return BitmapFactory.decodeFile(file.path,
            BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun trim(dir: File) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= DISK_LIMIT) break
            total -= f.length()
            f.delete()
        }
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** An image from the web; shows nothing until (and unless) it loads. */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxWidth: Int = 1080,
    contentScale: ContentScale = ContentScale.Crop,
    onResult: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, url, maxWidth) {
        value = url?.let { FeedImages.load(context, it, maxWidth)?.asImageBitmap() }
        onResult(value != null)
    }
    bitmap?.let {
        Image(it, contentDescription, modifier, contentScale = contentScale)
    }
}

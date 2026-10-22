/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * Colourful icons for GrapheneOS's own apps (and Brave): their icons are a dark or coloured
 * symbol on a white background, and Loom turns them around into the app's own colour with the
 * symbol in white.
 *
 * The app's real icon is used, so the symbol stays the app's own. Only icons that really are a
 * symbol on a light background are changed; anything else is left as it is.
 */
object ColoredIcons {

    /** Background colour per app package. */
    private val COLORS: Map<String, Int> = mapOf(
        // GrapheneOS apps
        "app.grapheneos.camera" to 0xFF2A9AF2.toInt(),     // light blue, like Open Camera
        "app.grapheneos.apps" to 0xFF2E7D32.toInt(),       // green
        "app.grapheneos.pdfviewer" to 0xFFBF360C.toInt(),  // deep orange
        "app.grapheneos.info" to 0xFF37474F.toInt(),       // slate
        "app.attestation.auditor" to 0xFF6D4C41.toInt(),   // brown
        "app.vanadium.browser" to 0xFF3949AB.toInt(),      // indigo
        // Android apps GrapheneOS ships with its own icons
        "com.android.dialer" to 0xFF1E8E3E.toInt(),        // Phone: green
        "com.android.messaging" to 0xFF1A73E8.toInt(),     // Messaging: blue
        "com.android.contacts" to 0xFF00897B.toInt(),      // Contacts: teal
        "com.android.gallery3d" to 0xFF8E24AA.toInt(),     // Gallery: purple
        "app.grapheneos.gallery" to 0xFF8E24AA.toInt(),
        "com.android.deskclock" to 0xFF0097A7.toInt(),     // Clock: cyan
        "com.android.calculator2" to 0xFFD81B60.toInt(),   // Calculator: pink
        "com.android.documentsui" to 0xFFE37400.toInt(),   // Files: amber
        "com.android.settings" to 0xFF546E7A.toInt(),      // Settings: blue grey
        "com.android.emergency" to 0xFFC62828.toInt(),     // Emergency information: red
        // Brave: its orange, with the lion in white
        "com.brave.browser" to 0xFFFB542B.toInt(),
        "com.brave.browser_beta" to 0xFFFB542B.toInt(),
        "com.brave.browser_nightly" to 0xFFFB542B.toInt(),
    )

    /** Pixel size the icon is redrawn at (the full 108dp adaptive-icon canvas). */
    private const val SIZE = 432

    fun colorFor(packageName: String): Int? = COLORS[packageName]

    /**
     * The app's icon turned into a white symbol on [color], or null when the icon isn't a
     * symbol on a light background (so it's left alone).
     */
    fun recolor(res: Resources, icon: Drawable?, color: Int): Drawable? {
        val src = (icon?.constantState?.newDrawable(res)?.mutate() ?: icon)
            as? AdaptiveIconDrawable ?: return null
        val background = src.background ?: return null
        if (!isLight(background)) return null

        // The whole icon as one picture: background and symbol.
        val full = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(full).let { c ->
            background.setBounds(0, 0, SIZE, SIZE)
            background.draw(c)
            src.foreground?.let {
                it.setBounds(0, 0, SIZE, SIZE)
                it.draw(c)
            }
        }
        // Anything that isn't white becomes white symbol; white becomes see-through. A
        // pixel's "ink" is how far its lightest channel is from white, so a black symbol and
        // an orange one both turn solid white, and white details inside them stay open.
        val pixels = IntArray(SIZE * SIZE)
        full.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        full.recycle()
        var inked = 0
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = Color.alpha(p)
            val lightest = minOf(Color.red(p), Color.green(p), Color.blue(p))
            val ink = (a * (255 - lightest)) / 255
            if (ink > 128) inked++
            pixels[i] = Color.argb(ink, 255, 255, 255)
        }
        // Nothing (or almost everything) dark: not a symbol on white after all.
        val share = inked.toFloat() / pixels.size
        if (share < 0.01f || share > 0.6f) return null
        val glyph = Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val fg = BitmapDrawable(res, glyph)
        val bg = ColorDrawable(color)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AdaptiveIconDrawable(bg, fg, BitmapDrawable(res, glyph))
        } else {
            AdaptiveIconDrawable(bg, fg)
        }
    }

    /** Whether a background layer is (close to) plain white or light grey. */
    private fun isLight(background: Drawable): Boolean {
        val n = 24
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        background.setBounds(0, 0, n, n)
        background.draw(Canvas(bmp))
        val px = IntArray(n * n)
        bmp.getPixels(px, 0, n, 0, 0, n, n)
        bmp.recycle()
        var light = 0
        for (p in px) {
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val bright = (r * 299 + g * 587 + b * 114) / 1000
            val chroma = maxOf(r, g, b) - minOf(r, g, b)
            if (Color.alpha(p) > 200 && bright > 215 && chroma < 40) light++
        }
        return light > px.size * 0.85f
    }
}

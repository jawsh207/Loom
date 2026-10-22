/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.R

/**
 * Draws Home settings' rows as the system Settings app does: each group of rows is a column of
 * cards in the bright surface colour on the container-coloured page, with large rounded corners
 * at the top and bottom of the group and small ones between rows. Groups are separated by
 * (untitled) categories in launcher_preferences.xml; a nested screen's rows form one group.
 */
object SettingsCards {

    @JvmStatic
    fun attach(list: RecyclerView) {
        val res = list.resources
        val density = res.displayMetrics.density
        list.setBackgroundColor(list.context.getColor(R.color.materialColorSurfaceContainer))
        list.addItemDecoration(Decoration(
            color = list.context.getColor(R.color.materialColorSurfaceBright),
            margin = (16 * density).toInt(),
            gap = (3 * density).toInt(),
            big = 24 * density,
            small = 4 * density,
        ))
    }

    private class Decoration(
        color: Int,
        private val margin: Int,
        private val gap: Int,
        private val big: Float,
        private val small: Float,
    ) : RecyclerView.ItemDecoration() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        private val path = Path()
        private val rect = RectF()

        private fun isRow(parent: RecyclerView, position: Int): Boolean {
            val adapter = parent.adapter as? PreferenceGroupAdapter ?: return false
            if (position < 0 || position >= adapter.itemCount) return false
            return adapter.getItem(position) !is PreferenceCategory
        }

        override fun getItemOffsets(
            outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State,
        ) {
            val pos = parent.getChildAdapterPosition(view)
            if (pos == RecyclerView.NO_POSITION) return
            if (!isRow(parent, pos)) {
                // Untitled categories just make the space between groups.
                outRect.set(0, 0, 0, 0)
                return
            }
            outRect.left = margin
            outRect.right = margin
            outRect.bottom = if (isRow(parent, pos + 1)) gap else 0
            if (pos == 0) outRect.top = margin / 2
        }

        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val pos = parent.getChildAdapterPosition(child)
                if (pos == RecyclerView.NO_POSITION || !isRow(parent, pos)) continue
                val top = if (isRow(parent, pos - 1)) small else big
                val bottom = if (isRow(parent, pos + 1)) small else big
                rect.set(child.left.toFloat(), child.top + child.translationY,
                    child.right.toFloat(), child.bottom + child.translationY)
                path.reset()
                path.addRoundRect(rect, floatArrayOf(top, top, top, top,
                    bottom, bottom, bottom, bottom), Path.Direction.CW)
                c.drawPath(path, paint)
                clipRow(child, top, bottom)
            }
        }

        /** Keeps the row's touch ripple inside its rounded card. */
        private fun clipRow(row: View, top: Float, bottom: Float) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            val tag = top to bottom
            if (row.getTag(R.id.folio_settings_card) == tag) return
            row.setTag(R.id.folio_settings_card, tag)
            row.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val p = Path()
                    p.addRoundRect(RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()),
                        floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom),
                        Path.Direction.CW)
                    outline.setPath(p)
                }
            }
            row.clipToOutline = true
        }
    }
}

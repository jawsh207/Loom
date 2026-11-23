/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Notices when text is selected in an article, so the panel can tell a selection-handle drag
 * from the swipe that closes it.
 *
 * Article text can be selected and copied (it sits in a SelectionContainer). Dragging a
 * selection handle sideways looks just like the swipe towards the home screen that closes the
 * panel, so while text is selected, [FeedPanelView] leaves those drags to the article.
 *
 * Compose has no public "is something selected" signal, but it shows its Copy / Select all
 * toolbar whenever there's a selection and hides it while a handle is dragged. So this wraps the
 * system's toolbar: showing it marks a selection, and hiding it keeps the mark for a few seconds
 * (long enough for a handle drag, after which the toolbar comes back). Clearing the selection
 * hides the toolbar for good, and the mark runs out.
 */
class ArticleSelection(private val system: TextToolbar) : TextToolbar {

    override val status: TextToolbarStatus get() = system.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        selectedUntil = Long.MAX_VALUE
        system.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested,
            onSelectAllRequested)
    }

    override fun hide() {
        selectedUntil = SystemClock.uptimeMillis() + HOLD_MS
        system.hide()
    }

    companion object {
        /** How long a hidden toolbar still counts as a selection (a handle drag's length). */
        private const val HOLD_MS = 4000L

        @Volatile
        private var selectedUntil = 0L

        /** Whether article text is (or was just now) selected. */
        @JvmStatic
        fun isSelecting(): Boolean = SystemClock.uptimeMillis() < selectedUntil

        /** Forgets the selection (the article was closed). */
        @JvmStatic
        fun clear() {
            selectedUntil = 0L
        }
    }
}

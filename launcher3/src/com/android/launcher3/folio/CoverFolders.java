/*
 * Copyright (C) 2026 The Folio Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.folio;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Workspace;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;

/**
 * Cover mode for folders. When on, tapping the folder launches its first app (the "cover") and
 * swiping up on the folder opens it. The setting is stored as {@link FolderInfo#FLAG_COVER_MODE}
 * in the folder's existing options column, so it persists with the workspace database.
 */
public final class CoverFolders {

    private CoverFolders() { }

    public static boolean isCoverMode(@Nullable FolderInfo folder) {
        return folder != null && folder.hasOption(FolderInfo.FLAG_COVER_MODE);
    }

    public static void setCoverMode(Launcher launcher, Folder folder, boolean enabled) {
        folder.mInfo.setOption(FolderInfo.FLAG_COVER_MODE, enabled, launcher.getModelWriter());
        if (folder.getFolderIcon() != null) {
            // Redraw: the icon switches between the folder preview and the cover app's icon.
            folder.getFolderIcon().invalidate();
        }
    }

    /** The folder's first app (lowest rank), or null if it isn't a launchable app/shortcut. */
    @Nullable
    public static WorkspaceItemInfo getCover(FolderInfo folder) {
        ItemInfo first = null;
        for (ItemInfo item : folder.getContents()) {
            if (first == null || item.rank < first.rank) {
                first = item;
            }
        }
        return first instanceof WorkspaceItemInfo ? (WorkspaceItemInfo) first : null;
    }

    /** Opens the folder's contents, same as a normal folder tap. */
    public static void openContents(FolderIcon icon) {
        Folder folder = icon.getFolder();
        if (!folder.isOpen() && !folder.isDestroyed()) {
            folder.animateOpen();
        }
    }

    /**
     * Returns the cover-mode folder under a drag-layer touch, if any. Used to keep the
     * swipe-up-for-all-apps gesture from stealing a swipe that starts on a cover folder.
     */
    @Nullable
    public static FolderIcon findCoverFolderAt(Launcher launcher, MotionEvent ev) {
        FolderIcon icon = findIn(launcher, launcher.getHotseat(), ev);
        if (icon != null) {
            return icon;
        }
        Workspace<?> workspace = launcher.getWorkspace();
        for (int page : workspace.getVisiblePageIndices()) {
            View v = workspace.getPageAt(page);
            if (v instanceof CellLayout) {
                icon = findIn(launcher, (CellLayout) v, ev);
                if (icon != null) {
                    return icon;
                }
            }
        }
        return null;
    }

    @Nullable
    private static FolderIcon findIn(Launcher launcher, @Nullable CellLayout layout,
            MotionEvent ev) {
        if (layout == null) {
            return null;
        }
        ShortcutAndWidgetContainer container = layout.getShortcutsAndWidgets();
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof FolderIcon icon
                    && child.getVisibility() == View.VISIBLE
                    && isCoverMode(icon.mInfo)
                    && launcher.getDragLayer().isEventOverView(child, ev)) {
                return icon;
            }
        }
        return null;
    }
}

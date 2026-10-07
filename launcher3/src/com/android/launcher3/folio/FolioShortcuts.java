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

import android.view.View;

import androidx.annotation.NonNull;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.SystemShortcut;

/** Extra entries for an app's long-press menu. Registered in Launcher#getSupportedShortcuts. */
public final class FolioShortcuts {

    private FolioShortcuts() { }

    /** "Add to tab" on any personal app, in the drawer or on the home screen. */
    public static final SystemShortcut.Factory<Launcher> ADD_TO_TAB =
            (launcher, itemInfo, originalView) -> {
                if (itemInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                        || !DrawerTabDialogs.isPersonalApp(itemInfo)
                        || DrawerTabsStore.appKey(itemInfo) == null) {
                    return null;
                }
                return new AddToTab(launcher, itemInfo, originalView);
            };

    /** "Edit icon": pick this app's icon from any installed icon pack, or reset it. */
    public static final SystemShortcut.Factory<Launcher> EDIT_ICON =
            (launcher, itemInfo, originalView) -> {
                if (itemInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                        || itemInfo.getTargetComponent() == null) {
                    return null;
                }
                return new EditIcon(launcher, itemInfo, originalView);
            };

    /**
     * "Use as folder cover" / "Stop using as cover", shown only on the first app of the folder
     * that is currently open.
     */
    public static final SystemShortcut.Factory<Launcher> FOLDER_COVER =
            (launcher, itemInfo, originalView) -> {
                Folder folder = Folder.getOpen(launcher);
                if (folder == null || folder.mInfo == null
                        || itemInfo.container != folder.mInfo.id) {
                    return null;
                }
                WorkspaceItemInfo cover = CoverFolders.getCover(folder.mInfo);
                if (cover == null || cover.id != itemInfo.id) {
                    return null;
                }
                return new FolderCover(launcher, itemInfo, originalView, folder);
            };

    static class AddToTab extends SystemShortcut<Launcher> {
        AddToTab(Launcher launcher, ItemInfo info, @NonNull View originalView) {
            super(R.drawable.ic_plus, R.string.drawer_tab_add_app, launcher, info, originalView);
        }

        @Override
        public void onClick(View view) {
            AbstractFloatingView.closeAllOpenViews(mTarget);
            DrawerTabDialogs.showTabsForApp(mTarget, mItemInfo);
        }
    }

    static class EditIcon extends SystemShortcut<Launcher> {
        EditIcon(Launcher launcher, ItemInfo info, @NonNull View originalView) {
            super(R.drawable.ic_palette, R.string.icon_edit_title, launcher, info, originalView);
        }

        @Override
        public void onClick(View view) {
            AbstractFloatingView.closeAllOpenViews(mTarget);
            EditIconDialog.show(mTarget, mItemInfo.getTargetComponent(), mItemInfo.title);
        }
    }

    static class FolderCover extends SystemShortcut<Launcher> {
        private final Folder mFolder;

        FolderCover(Launcher launcher, ItemInfo info, @NonNull View originalView,
                Folder folder) {
            super(R.drawable.ic_apps,
                    CoverFolders.isCoverMode(folder.mInfo)
                            ? R.string.folder_cover_off : R.string.folder_cover_on,
                    launcher, info, originalView);
            mFolder = folder;
        }

        @Override
        public void onClick(View view) {
            boolean enable = !CoverFolders.isCoverMode(mFolder.mInfo);
            CoverFolders.setCoverMode(mTarget, mFolder, enable);
            AbstractFloatingView.closeAllOpenViews(mTarget);
        }
    }
}

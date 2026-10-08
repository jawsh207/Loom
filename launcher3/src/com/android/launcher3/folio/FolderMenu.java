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

import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.views.ActivityContext;

/**
 * The ⋮ button in an open folder's footer. Its menu turns cover mode on or off for the folder.
 */
public final class FolderMenu {

    private static final int ITEM_COVER = 1;
    private static final int ITEM_RENAME = 2;

    private FolderMenu() { }

    /** Wires up the footer button. Hidden outside the home screen (e.g. a taskbar folder). */
    public static void attach(Folder folder, @Nullable View button) {
        if (button == null) {
            return;
        }
        if (!(ActivityContext.lookupContextNoThrow(folder.getContext()) instanceof Launcher)) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setOnClickListener(v -> show(folder, v));
    }

    private static void show(Folder folder, View anchor) {
        if (folder.mInfo == null) {
            return;
        }
        Launcher launcher = Launcher.getLauncher(folder.getContext());
        boolean isCover = CoverFolders.isCoverMode(folder.mInfo);
        boolean hasCover = CoverFolders.getCover(folder.mInfo) != null;

        PopupMenu menu = new PopupMenu(new ContextThemeWrapper(folder.getContext(),
                android.R.style.Theme_DeviceDefault_DayNight), anchor);
        MenuItem cover = menu.getMenu().add(Menu.NONE, ITEM_COVER, Menu.NONE,
                isCover ? R.string.folder_cover_off_menu : R.string.folder_cover_on_menu);
        // The cover must be an app or shortcut; an app pair in the first slot can't be one.
        cover.setEnabled(isCover || hasCover);
        menu.getMenu().add(Menu.NONE, ITEM_RENAME, Menu.NONE, R.string.folder_rename);

        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == ITEM_RENAME) {
                CharSequence title = folder.mInfo.title;
                DrawerTabDialogs.showNameDialog(folder.getContext(), R.string.folder_rename,
                        R.string.folder_hint_text, title == null ? "" : title.toString(),
                        folder::renameFolder);
                return true;
            }
            if (item.getItemId() == ITEM_COVER) {
                CoverFolders.setCoverMode(launcher, folder, !isCover);
                // Close so the folder's new look on the home screen is visible right away.
                folder.close(true);
                return true;
            }
            return false;
        });
        menu.show();
    }
}

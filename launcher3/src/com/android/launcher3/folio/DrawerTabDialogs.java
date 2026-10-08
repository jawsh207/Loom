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

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.res.Configuration;
import android.os.Process;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Dialogs for creating, renaming, filling and deleting drawer tabs. */
public final class DrawerTabDialogs {

    private DrawerTabDialogs() { }

    static AlertDialog.Builder builder(Context context) {
        boolean dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return new AlertDialog.Builder(context, dark
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    /** New tab: ask for a name, then let the user pick its apps. */
    public static void createTab(Context context) {
        showNameDialog(context, R.string.drawer_tab_new, "", name -> {
            DrawerTabsStore store = DrawerTabsStore.get(context);
            DrawerTabsStore.Tab tab = store.createTab(name);
            store.select(tab.id);
            showAppPicker(context, tab.id);
        });
    }

    /** Long-press menu for a tab. */
    public static void showTabMenu(Context context, String tabId) {
        DrawerTabsStore store = DrawerTabsStore.get(context);
        DrawerTabsStore.Tab tab = store.getTab(tabId);
        if (tab == null) {
            return;
        }
        CharSequence[] items = {
                context.getString(R.string.drawer_tab_choose_apps),
                context.getString(R.string.drawer_tab_rename),
                context.getString(R.string.drawer_tab_move_left),
                context.getString(R.string.drawer_tab_move_right),
                context.getString(R.string.drawer_tab_delete),
        };
        builder(context)
                .setTitle(tab.name)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0 -> showAppPicker(context, tabId);
                        case 1 -> showNameDialog(context, R.string.drawer_tab_rename, tab.name,
                                name -> store.renameTab(tabId, name));
                        case 2 -> store.moveTab(tabId, -1);
                        case 3 -> store.moveTab(tabId, 1);
                        case 4 -> builder(context)
                                .setTitle(context.getString(R.string.drawer_tab_delete_confirm,
                                        tab.name))
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.drawer_tab_delete,
                                        (d2, w2) -> store.deleteTab(tabId))
                                .show();
                    }
                })
                .show();
    }

    /**
     * Multi-select list of every launchable app; checked apps belong to the tab. Reads apps from
     * LauncherApps so it also works from Folio's settings screen, outside the drawer.
     */
    public static void showAppPicker(Context context, String tabId) {
        DrawerTabsStore store = DrawerTabsStore.get(context);
        DrawerTabsStore.Tab tab = store.getTab(tabId);
        LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
        if (tab == null || launcherApps == null) {
            return;
        }
        // Tabs filter the personal list only; work and private-space apps keep their own pages.
        List<LauncherActivityInfo> apps = new ArrayList<>(
                launcherApps.getActivityList(null, Process.myUserHandle()));
        apps.sort(Comparator.comparing(a -> String.valueOf(a.getLabel()),
                String.CASE_INSENSITIVE_ORDER));

        int n = apps.size();
        CharSequence[] labels = new CharSequence[n];
        String[] keys = new String[n];
        boolean[] checked = new boolean[n];
        for (int i = 0; i < n; i++) {
            LauncherActivityInfo app = apps.get(i);
            keys[i] = DrawerTabsStore.appKey(app.getComponentName(), app.getUser());
            labels[i] = app.getLabel();
            checked[i] = tab.apps.contains(keys[i]);
        }

        builder(context)
                .setTitle(tab.name)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) ->
                        checked[which] = isChecked)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    // Rebuilding from the current app list also drops uninstalled apps.
                    Set<String> selected = new LinkedHashSet<>();
                    for (int i = 0; i < n; i++) {
                        if (checked[i] && keys[i] != null) {
                            selected.add(keys[i]);
                        }
                    }
                    store.setApps(tabId, selected);
                })
                .show();
    }

    /** "Add to tab" from an app's long-press menu: tick the tabs this app belongs to. */
    public static void showTabsForApp(Context context, ItemInfo app) {
        DrawerTabsStore store = DrawerTabsStore.get(context);
        String key = DrawerTabsStore.appKey(app);
        List<DrawerTabsStore.Tab> tabs = new ArrayList<>(store.getTabs());
        if (key == null) {
            return;
        }
        if (tabs.isEmpty()) {
            showNameDialog(context, R.string.drawer_tab_new, "", name -> {
                DrawerTabsStore.Tab tab = store.createTab(name);
                store.setAppInTab(tab.id, key, true);
            });
            return;
        }
        CharSequence[] names = new CharSequence[tabs.size()];
        boolean[] checked = new boolean[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            names[i] = tabs.get(i).name;
            checked[i] = tabs.get(i).apps.contains(key);
        }
        builder(context)
                .setTitle(app.title)
                .setMultiChoiceItems(names, checked, (d, which, isChecked) ->
                        store.setAppInTab(tabs.get(which).id, key, isChecked))
                .setNeutralButton(R.string.drawer_tab_new, (d, w) ->
                        showNameDialog(context, R.string.drawer_tab_new, "", name -> {
                            DrawerTabsStore.Tab tab = store.createTab(name);
                            store.setAppInTab(tab.id, key, true);
                        }))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    static void showNameDialog(Context context, int titleRes, String initial,
            Consumer<String> onName) {
        showNameDialog(context, titleRes, R.string.drawer_tab_name_hint, initial, onName);
    }

    static void showNameDialog(Context context, int titleRes, int hintRes, String initial,
            Consumer<String> onName) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSingleLine(true);
        input.setHint(hintRes);
        input.setText(initial);
        input.setSelection(initial.length());
        FrameLayout frame = new FrameLayout(context);
        int pad = context.getResources().getDimensionPixelSize(R.dimen.drawer_tab_dialog_padding);
        frame.setPadding(pad, pad / 2, pad, 0);
        frame.addView(input);

        AlertDialog dialog = builder(context)
                .setTitle(titleRes)
                .setView(frame)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        onName.accept(name);
                    }
                })
                .create();
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        input.requestFocus();
    }

    /** Apps belonging to the main user, which are the ones the drawer tabs can filter. */
    static boolean isPersonalApp(ItemInfo info) {
        return Process.myUserHandle().equals(info.user);
    }
}

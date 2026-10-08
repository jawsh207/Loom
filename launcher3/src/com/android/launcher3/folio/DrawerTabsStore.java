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

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.model.data.ItemInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Stores the user's drawer tabs: an ordered list of named tabs, each holding a set of apps.
 *
 * Tabs live in their own SharedPreferences file as JSON, so they survive restarts and are
 * independent of the workspace database. All methods must be called on the main thread.
 */
public final class DrawerTabsStore {

    private static final String PREFS_NAME = "drawer_tabs";
    private static final String KEY_TABS = "tabs";
    private static final String KEY_SELECTED = "selected_tab";

    /** Id of the built-in main tab (apps not in any tab), which is not stored. */
    public static final String ALL_TAB_ID = "";

    /** One user-created tab. */
    public static final class Tab {
        public final String id;
        public String name;
        /** App keys, see {@link #appKey(ItemInfo)}. */
        public final Set<String> apps = new LinkedHashSet<>();

        Tab(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** Notified whenever tabs are added, renamed, removed, re-filled or re-selected. */
    public interface Listener {
        void onDrawerTabsChanged();
    }

    private static DrawerTabsStore sInstance;

    private final SharedPreferences mPrefs;
    private final List<Tab> mTabs = new ArrayList<>();
    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();
    private String mSelectedId;

    public static DrawerTabsStore get(Context context) {
        if (sInstance == null) {
            sInstance = new DrawerTabsStore(context.getApplicationContext());
        }
        return sInstance;
    }

    private DrawerTabsStore(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }

    /**
     * Key identifying an app for a given user, e.g.
     * {@code com.example/.MainActivity#0}. Work-profile copies of an app get a different key.
     */
    @Nullable
    public static String appKey(ItemInfo info) {
        ComponentName cn = info.getTargetComponent();
        if (cn == null || info.user == null) {
            return null;
        }
        return cn.flattenToString() + "#" + info.user.hashCode();
    }

    /** Same key as {@link #appKey(ItemInfo)}, for an app known by component and user. */
    public static String appKey(@NonNull ComponentName cn, @NonNull UserHandle user) {
        return cn.flattenToString() + "#" + user.hashCode();
    }

    public List<Tab> getTabs() {
        return Collections.unmodifiableList(mTabs);
    }

    @Nullable
    public Tab getTab(String id) {
        for (Tab t : mTabs) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** The selected tab, or null when "All apps" is selected. */
    @Nullable
    public Tab getSelectedTab() {
        return getTab(mSelectedId);
    }

    public String getSelectedId() {
        return getSelectedTab() == null ? ALL_TAB_ID : mSelectedId;
    }

    public void select(String id) {
        mSelectedId = id;
        mPrefs.edit().putString(KEY_SELECTED, id).apply();
        notifyChanged();
    }

    public Tab createTab(@NonNull String name) {
        Tab tab = new Tab(UUID.randomUUID().toString(), name.trim());
        mTabs.add(tab);
        save();
        return tab;
    }

    public void renameTab(String id, @NonNull String name) {
        Tab tab = getTab(id);
        if (tab != null) {
            tab.name = name.trim();
            save();
        }
    }

    public void deleteTab(String id) {
        mTabs.removeIf(t -> t.id.equals(id));
        if (id.equals(mSelectedId)) {
            mSelectedId = ALL_TAB_ID;
            mPrefs.edit().putString(KEY_SELECTED, ALL_TAB_ID).apply();
        }
        save();
    }

    /** Moves a tab one place left (-1) or right (+1). */
    public void moveTab(String id, int direction) {
        for (int i = 0; i < mTabs.size(); i++) {
            if (mTabs.get(i).id.equals(id)) {
                int j = i + direction;
                if (j >= 0 && j < mTabs.size()) {
                    Collections.swap(mTabs, i, j);
                    save();
                }
                return;
            }
        }
    }

    public void setApps(String id, Set<String> appKeys) {
        Tab tab = getTab(id);
        if (tab != null) {
            tab.apps.clear();
            tab.apps.addAll(appKeys);
            save();
        }
    }

    public void setAppInTab(String id, String appKey, boolean inTab) {
        Tab tab = getTab(id);
        if (tab == null || appKey == null) {
            return;
        }
        boolean changed = inTab ? tab.apps.add(appKey) : tab.apps.remove(appKey);
        if (changed) {
            save();
        }
    }

    /**
     * Filter for the drawer's list. A tab shows its own apps; the main tab shows the apps that
     * aren't in any tab. Null means no filtering (main tab with no apps assigned anywhere).
     * The drawer asks for a new filter whenever the tabs change.
     */
    @Nullable
    public Predicate<ItemInfo> getSelectedFilter() {
        Tab tab = getSelectedTab();
        if (tab != null) {
            Set<String> apps = new HashSet<>(tab.apps);
            return info -> apps.contains(appKey(info));
        }
        Set<String> assigned = new HashSet<>();
        for (Tab t : mTabs) {
            assigned.addAll(t.apps);
        }
        return assigned.isEmpty() ? null : info -> !assigned.contains(appKey(info));
    }

    /** Ids of the main tab followed by every user tab, in order (for swiping between them). */
    public List<String> getTabIdsInOrder() {
        List<String> ids = new ArrayList<>();
        ids.add(ALL_TAB_ID);
        for (Tab t : mTabs) {
            ids.add(t.id);
        }
        return ids;
    }

    /** Selects the tab next to the current one; returns false at either end. */
    public boolean selectAdjacent(int direction) {
        List<String> ids = getTabIdsInOrder();
        int next = ids.indexOf(getSelectedId()) + direction;
        if (next < 0 || next >= ids.size()) {
            return false;
        }
        select(ids.get(next));
        return true;
    }

    public void addListener(Listener l) {
        mListeners.add(l);
    }

    public void removeListener(Listener l) {
        mListeners.remove(l);
    }

    private void notifyChanged() {
        for (Listener l : mListeners) {
            l.onDrawerTabsChanged();
        }
    }

    private void load() {
        mTabs.clear();
        mSelectedId = mPrefs.getString(KEY_SELECTED, ALL_TAB_ID);
        try {
            JSONArray arr = new JSONArray(mPrefs.getString(KEY_TABS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Tab tab = new Tab(o.getString("id"), o.getString("name"));
                JSONArray apps = o.optJSONArray("apps");
                if (apps != null) {
                    for (int j = 0; j < apps.length(); j++) {
                        tab.apps.add(apps.getString(j));
                    }
                }
                mTabs.add(tab);
            }
        } catch (JSONException e) {
            mTabs.clear();
        }
    }

    private void save() {
        JSONArray arr = new JSONArray();
        try {
            for (Tab t : mTabs) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("name", t.name);
                o.put("apps", new JSONArray(t.apps));
                arr.put(o);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        mPrefs.edit().putString(KEY_TABS, arr.toString()).apply();
        notifyChanged();
    }
}

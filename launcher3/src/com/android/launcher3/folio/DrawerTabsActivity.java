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

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.android.launcher3.R;

/**
 * "Drawer tabs" screen in Folio's settings: lists the tabs, creates new ones, and opens each
 * tab's options (choose apps, rename, move, delete). Changes show up in the drawer right away.
 */
public class DrawerTabsActivity extends FragmentActivity {

    /** Key of the entry in launcher_preferences.xml that opens this screen. */
    public static final String PREF_KEY = "pref_drawer_tabs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Same frame as the main settings screen: toolbar + content.
        setContentView(R.layout.settings_activity);
        setActionBar(findViewById(R.id.action_bar));
        getActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(R.string.drawer_tabs_settings_title);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, new TabsFragment())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** The list of tabs, rebuilt whenever a tab changes. */
    public static class TabsFragment extends PreferenceFragmentCompat
            implements DrawerTabsStore.Listener {

        private DrawerTabsStore mStore;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            mStore = DrawerTabsStore.get(requireContext());
            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));
            rebuild();
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            // Keep the last row clear of the navigation bar, as the main settings screen does.
            View listView = getListView();
            int bottomPadding = listView.getPaddingBottom();
            listView.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                        bottomPadding + insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            });
        }

        @Override
        public void onStart() {
            super.onStart();
            mStore.addListener(this);
            rebuild();
        }

        @Override
        public void onStop() {
            mStore.removeListener(this);
            super.onStop();
        }

        @Override
        public void onDrawerTabsChanged() {
            rebuild();
        }

        private void rebuild() {
            Context context = getPreferenceManager().getContext();
            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return;
            }
            screen.removeAll();

            Preference add = new Preference(context);
            add.setKey("folio_new_tab");
            add.setTitle(R.string.drawer_tab_new);
            add.setIcon(R.drawable.ic_plus);
            add.setOnPreferenceClickListener(p -> {
                DrawerTabDialogs.createTab(requireActivity());
                return true;
            });
            screen.addPreference(add);

            PreferenceCategory tabs = new PreferenceCategory(context);
            tabs.setKey("folio_tabs");
            tabs.setTitle(R.string.drawer_tabs_settings_category);
            screen.addPreference(tabs);

            if (mStore.getTabs().isEmpty()) {
                Preference empty = new Preference(context);
                empty.setSummary(R.string.drawer_tabs_settings_empty);
                empty.setSelectable(false);
                tabs.addPreference(empty);
            }
            for (DrawerTabsStore.Tab tab : mStore.getTabs()) {
                Preference row = new Preference(context);
                row.setKey("folio_tab_" + tab.id);
                row.setTitle(tab.name);
                row.setSummary(getResources().getQuantityString(
                        R.plurals.drawer_tab_app_count, tab.apps.size(), tab.apps.size()));
                row.setOnPreferenceClickListener(p -> {
                    DrawerTabDialogs.showTabMenu(requireActivity(), tab.id);
                    return true;
                });
                tabs.addPreference(row);
            }

            Preference hint = new Preference(context);
            hint.setSummary(R.string.drawer_tabs_settings_hint);
            hint.setSelectable(false);
            screen.addPreference(hint);
        }
    }
}

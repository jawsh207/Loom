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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.WindowCompat;

import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Grid of every icon in one icon pack, with search, for picking an app's icon by hand.
 * Opened from the "Edit icon" long-press entry via {@link EditIconDialog}.
 */
public class IconPickerActivity extends Activity {

    static final String EXTRA_COMPONENT = "folio.component";
    static final String EXTRA_PACK = "folio.pack";
    static final String EXTRA_LABEL = "folio.label";

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mLoader = Executors.newSingleThreadExecutor();
    private final LruCache<String, Drawable> mCache = new LruCache<>(400);

    private ComponentName mComponent;
    private IconPack mPack;
    private List<String> mAll = new ArrayList<>();
    private final List<String> mShown = new ArrayList<>();
    private IconAdapter mAdapter;
    private int mDensity;

    static Intent newIntent(Context context, ComponentName component, String packPackage,
            CharSequence appLabel) {
        return new Intent(context, IconPickerActivity.class)
                .putExtra(EXTRA_COMPONENT, component)
                .putExtra(EXTRA_PACK, packPackage)
                .putExtra(EXTRA_LABEL, appLabel == null ? null : appLabel.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mComponent = getComponentExtra(getIntent());
        String packPkg = getIntent().getStringExtra(EXTRA_PACK);
        String label = getIntent().getStringExtra(EXTRA_LABEL);
        mPack = packPkg == null ? null : IconPackManager.get(this).getPack(packPkg);
        if (mComponent == null || mPack == null) {
            finish();
            return;
        }
        mDensity = getResources().getDisplayMetrics().densityDpi;

        setContentView(R.layout.settings_activity);
        setActionBar(findViewById(R.id.action_bar));
        getActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(label != null ? label : getString(R.string.icon_edit_title));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        int pad = getResources().getDimensionPixelSize(R.dimen.drawer_tab_dialog_padding);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        EditText search = new EditText(this);
        search.setHint(R.string.icon_picker_search_hint);
        search.setSingleLine(true);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(pad, pad / 2, pad, pad / 2);
        content.addView(search, searchLp);

        TextView empty = new TextView(this);
        empty.setText(R.string.icon_picker_empty);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(pad, pad, pad, pad);
        empty.setVisibility(View.GONE);

        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(getResources().getDimensionPixelSize(R.dimen.folio_picker_cell));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(pad / 2);
        grid.setClipToPadding(false);
        grid.setPadding(pad / 2, 0, pad / 2, pad);
        mAdapter = new IconAdapter();
        grid.setAdapter(mAdapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            IconPackManager.get(this).setOverride(mComponent,
                    mPack.packageName + "/" + mShown.get(position));
            finish();
        });
        grid.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    pad + insets.getSystemWindowInsetBottom());
            return insets;
        });

        FrameLayout gridFrame = new FrameLayout(this);
        gridFrame.addView(grid);
        gridFrame.addView(empty);
        content.addView(gridFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        ((ViewGroup) findViewById(R.id.content_frame)).addView(content);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                filter(s.toString());
            }
        });

        // Listing a big pack takes a moment; do it off the main thread.
        String suggestionKey = label == null ? "" : normalize(label);
        mLoader.execute(() -> {
            List<String> names = mPack.listDrawables();
            List<String> ordered = orderBySuggestion(names, mPack.getMappedName(mComponent),
                    suggestionKey);
            mMainHandler.post(() -> {
                mAll = ordered;
                // Only show "no matching icons" once the list has loaded.
                grid.setEmptyView(empty);
                filter(search.getText().toString());
            });
        });
    }

    @SuppressWarnings("deprecation")
    private static ComponentName getComponentExtra(Intent intent) {
        return intent.getParcelableExtra(EXTRA_COMPONENT);
    }

    /** Puts the pack's own icon for this app, then names matching the app's label, first. */
    private static List<String> orderBySuggestion(List<String> names, String mapped,
            String labelKey) {
        List<String> first = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String name : names) {
            if (name.equals(mapped)) {
                first.add(0, name);
            } else if (!labelKey.isEmpty() && normalize(name).contains(labelKey)) {
                first.add(name);
            } else {
                rest.add(name);
            }
        }
        first.addAll(rest);
        return first;
    }

    private void filter(String query) {
        String q = normalize(query);
        mShown.clear();
        for (String name : mAll) {
            if (q.isEmpty() || normalize(name).contains(q)) {
                mShown.add(name);
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    /** Lowercase letters and digits only, so "Signal" matches "ic_signal" and "signal_2". */
    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        mLoader.shutdownNow();
        super.onDestroy();
    }

    private class IconAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mShown.size();
        }

        @Override
        public Object getItem(int position) {
            return mShown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView view = (ImageView) convertView;
            if (view == null) {
                int size = getResources().getDimensionPixelSize(R.dimen.folio_picker_icon);
                view = new ImageView(IconPickerActivity.this);
                view.setLayoutParams(new GridView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, size));
                view.setScaleType(ImageView.ScaleType.FIT_CENTER);
                view.setBackgroundResource(android.R.drawable.list_selector_background);
            }
            String name = mShown.get(position);
            view.setTag(name);
            view.setContentDescription(name.replace('_', ' '));
            Drawable cached = mCache.get(name);
            view.setImageDrawable(cached);
            if (cached == null) {
                ImageView target = view;
                mLoader.execute(() -> {
                    Drawable d = mPack.getDrawableByName(name, mDensity);
                    if (d == null) {
                        return;
                    }
                    mMainHandler.post(() -> {
                        mCache.put(name, d);
                        // The view may have been reused for another icon meanwhile.
                        if (name.equals(target.getTag())) {
                            target.setImageDrawable(d);
                        }
                    });
                });
            }
            return view;
        }
    }
}

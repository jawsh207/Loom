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
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.android.launcher3.R;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.FloatingHeaderRow;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.views.ActivityContext;

/**
 * Row of tab chips shown under the search bar in the app drawer: "All", one chip per user tab,
 * and "+" to create a new tab. Tap a chip to filter the drawer, long-press it to edit the tab.
 *
 * Declared in all_apps_content.xml inside FloatingHeaderView, which picks it up as a fixed
 * header row and scrolls it away with the list like the prediction row.
 */
public class DrawerTabStrip extends HorizontalScrollView
        implements FloatingHeaderRow, DrawerTabsStore.Listener {

    private final LinearLayout mChips;
    private final DrawerTabsStore mStore;
    private final int mHeight;

    public DrawerTabStrip(Context context) {
        this(context, null);
    }

    public DrawerTabStrip(Context context, AttributeSet attrs) {
        super(context, attrs);
        mStore = DrawerTabsStore.get(context);
        mHeight = getResources().getDimensionPixelSize(R.dimen.drawer_tab_strip_height);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        mChips = new LinearLayout(context);
        mChips.setOrientation(LinearLayout.HORIZONTAL);
        mChips.setGravity(Gravity.CENTER_VERTICAL);
        addView(mChips, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mStore.addListener(this);
        onDrawerTabsChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        mStore.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onDrawerTabsChanged() {
        rebuildChips();
        ActivityAllAppsContainerView<?> appsView =
                ActivityContext.lookupContext(getContext()).getAppsView();
        if (appsView != null) {
            appsView.setDrawerTabFilter(mStore.getSelectedFilter());
        }
    }

    private void rebuildChips() {
        mChips.removeAllViews();
        String selected = mStore.getSelectedId();

        Button all = addChip(getContext().getString(R.string.drawer_tab_all),
                DrawerTabsStore.ALL_TAB_ID.equals(selected));
        all.setOnClickListener(v -> mStore.select(DrawerTabsStore.ALL_TAB_ID));

        for (DrawerTabsStore.Tab tab : mStore.getTabs()) {
            Button chip = addChip(tab.name, tab.id.equals(selected));
            chip.setOnClickListener(v -> mStore.select(tab.id));
            chip.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                DrawerTabDialogs.showTabMenu(getContext(), tab.id);
                return true;
            });
        }

        Button add = addChip("+", false);
        add.setContentDescription(getContext().getString(R.string.drawer_tab_new));
        add.setOnClickListener(v -> DrawerTabDialogs.createTab(getContext()));

        // Keep the selected chip in view after a rebuild.
        post(() -> {
            for (int i = 0; i < mChips.getChildCount(); i++) {
                View c = mChips.getChildAt(i);
                if (c.isSelected()) {
                    smoothScrollTo(Math.max(0, c.getLeft() - getPaddingLeft()), 0);
                    break;
                }
            }
        });
    }

    private Button addChip(CharSequence label, boolean selected) {
        Resources res = getResources();
        Button chip = new Button(getContext(), null, android.R.attr.borderlessButtonStyle);
        chip.setText(label);
        chip.setAllCaps(false);
        chip.setSingleLine(true);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setTextColor(getContext().getColorStateList(R.color.all_apps_tab_text));
        chip.setBackgroundResource(R.drawable.all_apps_tabs_background);
        chip.setMinWidth(res.getDimensionPixelSize(R.dimen.drawer_tab_chip_min_width));
        chip.setMinimumWidth(res.getDimensionPixelSize(R.dimen.drawer_tab_chip_min_width));
        int padH = res.getDimensionPixelSize(R.dimen.drawer_tab_chip_padding);
        chip.setPadding(padH, 0, padH, 0);
        chip.setSelected(selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                res.getDimensionPixelSize(R.dimen.drawer_tab_chip_height));
        lp.setMarginEnd(res.getDimensionPixelSize(R.dimen.drawer_tab_chip_spacing));
        mChips.addView(chip, lp);
        return chip;
    }

    // FloatingHeaderRow

    @Override
    public void setup(FloatingHeaderView parent, FloatingHeaderRow[] allRows,
            boolean tabsHidden) { }

    @Override
    public int getExpectedHeight() {
        return mHeight;
    }

    @Override
    public boolean shouldDraw() {
        return true;
    }

    @Override
    public boolean hasVisibleContent() {
        return true;
    }

    @Override
    public void setVerticalScroll(int scroll, boolean isScrolledOut) {
        setVisibility(isScrolledOut ? INVISIBLE : VISIBLE);
        if (!isScrolledOut) {
            setTranslationY(scroll);
        }
    }

    @Override
    public Class<DrawerTabStrip> getTypeClass() {
        return DrawerTabStrip.class;
    }

    @Override
    public View getFocusedChild() {
        return null;
    }
}

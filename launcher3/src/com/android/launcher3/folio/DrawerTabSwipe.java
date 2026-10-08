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
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.allapps.AllAppsPagedView;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Swipe left or right on the app drawer's list to move to the next or previous drawer tab.
 * Not used when a work profile exists: then the drawer already swipes between Personal and Work.
 */
public final class DrawerTabSwipe extends GestureDetector.SimpleOnGestureListener
        implements RecyclerView.OnItemTouchListener {

    /** Lists that already have the swipe (the drawer can rebuild its list). */
    private static final Set<RecyclerView> sAttached =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final long ANIM_DURATION = 180;

    private final RecyclerView mList;
    private final GestureDetector mDetector;
    private final int mMinDistance;
    private final int mMinVelocity;

    /** Adds the swipe to the drawer's main list (once per list). */
    public static void attach(@NonNull RecyclerView list) {
        if (list.getParent() instanceof AllAppsPagedView || !sAttached.add(list)) {
            return;
        }
        list.addOnItemTouchListener(new DrawerTabSwipe(list));
    }

    private DrawerTabSwipe(RecyclerView list) {
        mList = list;
        Context context = list.getContext();
        mDetector = new GestureDetector(context, this);
        float density = context.getResources().getDisplayMetrics().density;
        mMinDistance = (int) (56 * density);
        mMinVelocity = ViewConfiguration.get(context).getScaledMinimumFlingVelocity() * 4;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        // Only watch; scrolling and taps keep working normally.
        mDetector.onTouchEvent(e);
        return false;
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) { }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) { }

    @Override
    public boolean onFling(MotionEvent down, @NonNull MotionEvent up, float vx, float vy) {
        if (down == null) {
            return false;
        }
        float dx = up.getX() - down.getX();
        float dy = up.getY() - down.getY();
        if (Math.abs(dx) < mMinDistance || Math.abs(vx) < mMinVelocity
                || Math.abs(dx) < 2 * Math.abs(dy)) {
            return false;
        }
        // Swipe left (finger moves left) goes to the next tab, like turning a page.
        int direction = dx < 0 ? 1 : -1;
        if (!DrawerTabsStore.get(mList.getContext()).selectAdjacent(direction)) {
            return false;
        }
        mList.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        mList.stopScroll();
        mList.setTranslationX(direction * mMinDistance);
        mList.setAlpha(0.4f);
        mList.animate().translationX(0).alpha(1f).setDuration(ANIM_DURATION).start();
        return true;
    }
}

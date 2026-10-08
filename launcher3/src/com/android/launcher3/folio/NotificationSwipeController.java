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

import static com.android.launcher3.LauncherState.NORMAL;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.util.TouchController;

/**
 * Swipe down anywhere on the home screen to open the notification shade.
 *
 * The system launcher gets this from SystemUI; Folio is a regular app, so it opens the shade
 * through its accessibility service when that's on, or through StatusBarManager (allowed with
 * the normal EXPAND_STATUS_BAR permission) otherwise.
 */
public class NotificationSwipeController implements TouchController {

    /** Matches the key in launcher_preferences.xml. */
    public static final String PREF_KEY = "pref_swipe_down_notifications";

    private final Launcher mLauncher;
    private final int mTouchSlop;
    private float mDownX;
    private float mDownY;
    private boolean mTracking;

    public NotificationSwipeController(Launcher launcher) {
        mLauncher = launcher;
        mTouchSlop = ViewConfiguration.get(launcher).getScaledTouchSlop();
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY,
                Context.MODE_PRIVATE).getBoolean(PREF_KEY, true);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                mTracking = mLauncher.isInState(NORMAL)
                        && AbstractFloatingView.getTopOpenView(mLauncher) == null
                        && isEnabled(mLauncher);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!mTracking) {
                    return false;
                }
                float dx = Math.abs(ev.getX() - mDownX);
                float dy = ev.getY() - mDownY;
                if (dy > 2 * mTouchSlop && dy > 2 * dx) {
                    mTracking = false;
                    openNotificationShade(mLauncher);
                    return true; // Take the rest of the gesture so nothing else reacts.
                }
                if (dx > 2 * mTouchSlop || dy < -mTouchSlop) {
                    mTracking = false; // A page swipe or a swipe up for the drawer.
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTracking = false;
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return true;
    }

    /** Pulls down the notification shade. */
    public static void openNotificationShade(Context context) {
        if (LockScreenService.openNotifications()) {
            return;
        }
        try {
            Object statusBar = context.getSystemService("statusbar");
            statusBar.getClass().getMethod("expandNotificationsPanel").invoke(statusBar);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Not available on this OS; nothing else a regular app can do.
        }
    }
}

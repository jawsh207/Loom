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

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.Nullable;

/**
 * Accessibility service whose only job is to turn the screen off and lock the device, using
 * {@link AccessibilityService#GLOBAL_ACTION_LOCK_SCREEN}. Unlike a device-admin lockNow(), this
 * keeps fingerprint and face unlock available for the next unlock. It listens to no events and
 * reads no window content.
 */
public class LockScreenService extends AccessibilityService {

    @Nullable
    private static LockScreenService sInstance;

    /** True when the user has enabled the service in Accessibility settings. */
    public static boolean isRunning() {
        return sInstance != null;
    }

    /** Locks the device. Returns false if the service isn't enabled. */
    public static boolean lockScreen() {
        LockScreenService service = sInstance;
        return service != null && service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    /** Opens the notification shade. Returns false if the service isn't enabled. */
    public static boolean openNotifications() {
        LockScreenService service = sInstance;
        return service != null && service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        sInstance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }
}

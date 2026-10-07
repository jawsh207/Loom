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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;

/** Double-tap on an empty part of the home screen to turn the screen off. */
public final class DoubleTapToLock {

    /** Matches the key in launcher_preferences.xml. */
    public static final String PREF_KEY = "pref_double_tap_to_lock";

    private DoubleTapToLock() { }

    /** Read from the same prefs file the launcher settings screen writes to. */
    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY,
                Context.MODE_PRIVATE).getBoolean(PREF_KEY, true);
    }

    /** Called from the workspace on a double tap. */
    public static void onDoubleTap(Context context) {
        if (!isEnabled(context)) {
            return;
        }
        if (LockScreenService.lockScreen()) {
            return;
        }
        // Service not enabled yet: explain once and offer to open Accessibility settings.
        DrawerTabDialogs.builder(context)
                .setTitle(R.string.double_tap_lock_setup_title)
                .setMessage(R.string.double_tap_lock_setup_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.double_tap_lock_open_settings, (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(intent);
                    } catch (ActivityNotFoundException ignored) { }
                })
                .show();
    }
}

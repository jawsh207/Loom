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
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;

import com.android.launcher3.settings.SettingsActivity;

/**
 * Folio's icon in other launchers' app drawers. Opening it asks Android to make Folio the
 * default home app; if Folio already is, it opens Folio's settings instead.
 */
public class SetDefaultHomeActivity extends Activity {

    private static final int REQUEST_HOME_ROLE = 1;
    /** A result faster than this means Android didn't show its prompt (asked too often). */
    private static final long NO_PROMPT_MS = 400;

    private long mRequestedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            return; // Recreated while the system prompt is showing; wait for the result.
        }
        RoleManager roles = getSystemService(RoleManager.class);
        if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
            openHomeSettings();
        } else if (roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        } else {
            mRequestedAt = SystemClock.uptimeMillis();
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME),
                    REQUEST_HOME_ROLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_HOME_ROLE) {
            return;
        }
        if (resultCode == RESULT_OK) {
            // Folio is now the home app: go home, which shows Folio.
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        } else if (SystemClock.uptimeMillis() - mRequestedAt < NO_PROMPT_MS) {
            // Android skipped its prompt (it stops asking after repeated "no"s).
            openHomeSettings();
        } else {
            finish();
        }
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            // No settings screen for it; nothing more to do.
        }
        finish();
    }
}

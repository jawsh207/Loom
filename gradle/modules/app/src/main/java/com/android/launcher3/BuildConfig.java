/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3;

/**
 * Launcher3's build-time switches (Soong generates this from tools/buildconfig.sh).
 * APPLICATION_ID must match applicationId in gradle/modules/app/build.gradle.kts.
 */
public final class BuildConfig {
    public static final String APPLICATION_ID = "app.folio.launcher";

    public static final boolean IS_STUDIO_BUILD = false;
    public static final boolean QSB_ON_FIRST_SCREEN = false;
    public static final boolean IS_DEBUG_DEVICE = false;
    public static final boolean WIDGETS_ENABLED = true;
    public static final boolean NOTIFICATION_DOTS_ENABLED = true;
}

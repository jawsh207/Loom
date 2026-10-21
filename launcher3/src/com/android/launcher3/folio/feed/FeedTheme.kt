/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Material 3 Expressive with the system's wallpaper-based colours, in light or dark. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // The system's own colour roles (Android 14+ publishes them), so Loom's screens match
    // Settings and the other system apps exactly, in light and dark.
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

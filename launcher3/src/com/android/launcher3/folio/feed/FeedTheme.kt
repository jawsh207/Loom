/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Material 3 with the wallpaper-based colours of the system, in light or dark. */
@Composable
fun FeedTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, content = content)
}

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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

/**
 * Where a row sits in a group of settings rows. Like the system Settings app, a group is a
 * column of cards in the bright surface colour, with large rounded corners at the group's top
 * and bottom and small ones between rows, on the container-coloured page.
 */
enum class Group { Single, Top, Middle, Bottom }

private fun groupShape(g: Group): Shape {
    val big = 24.dp
    val small = 4.dp
    return when (g) {
        Group.Single -> RoundedCornerShape(big)
        Group.Top -> RoundedCornerShape(big, big, small, small)
        Group.Middle -> RoundedCornerShape(small)
        Group.Bottom -> RoundedCornerShape(small, small, big, big)
    }
}

/** A settings row's card (apply before any click handling, so the ripple is clipped). */
@Composable
fun Modifier.groupCard(g: Group): Modifier = this
    .padding(horizontal = 16.dp)
    .padding(bottom = if (g == Group.Top || g == Group.Middle) 3.dp else 0.dp)
    .clip(groupShape(g))
    .background(MaterialTheme.colorScheme.surfaceBright)

/** List rows inside [groupCard]s: transparent, so the card shows. */
@Composable
fun groupItemColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** Position of row [index] in a group of [count]. */
fun groupOf(index: Int, count: Int): Group = when {
    count <= 1 -> Group.Single
    index == 0 -> Group.Top
    index == count - 1 -> Group.Bottom
    else -> Group.Middle
}

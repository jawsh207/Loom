/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.folio.feed.FeedTheme

/** Home screen and app drawer grid sizes, with a live preview. */
class GridSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Makes sure the device's own grid has been worked out (FolioGrid.stock).
        LauncherAppState.getIDP(this)
        setContent { FeedTheme { Screen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen() {
        val saved = remember { FolioGrid.read(this) }
        val stock = FolioGrid.stock
        var customHome by remember { mutableStateOf(saved.columns > 0 && saved.rows > 0) }
        var columns by remember { mutableIntStateOf(saved.columns.takeIf { it > 0 } ?: stock.columns) }
        var rows by remember { mutableIntStateOf(saved.rows.takeIf { it > 0 } ?: stock.rows) }
        var customDock by remember { mutableStateOf(saved.hotseat > 0) }
        var dock by remember { mutableIntStateOf(saved.hotseat.takeIf { it > 0 } ?: stock.hotseat) }
        var customDrawer by remember { mutableStateOf(saved.drawerColumns > 0) }
        var drawer by remember {
            mutableIntStateOf(saved.drawerColumns.takeIf { it > 0 } ?: stock.drawerColumns)
        }

        val shownColumns = if (customHome) columns else stock.columns
        val shownRows = if (customHome) rows else stock.rows
        val shownDock = when {
            customDock -> dock
            customHome -> columns
            else -> stock.hotseat
        }
        val shownDrawer = if (customDrawer) drawer else stock.drawerColumns
        val result = FolioGrid.Sizes(
            if (customHome) columns else 0, if (customHome) rows else 0,
            if (customDock) dock else 0, if (customDrawer) drawer else 0)
        val changed = result != saved

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(getString(R.string.folio_grid_title)) },
                    navigationIcon = {
                        IconButton(::finish) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                getString(R.string.feed_back))
                        }
                    },
                )
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        Text(getString(R.string.folio_grid_apply_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                FolioGrid.write(this@GridSettingsActivity, result)
                                // Back to the home screen to see the new grid.
                                startActivity(Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_HOME).setPackage(packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                finish()
                            },
                            enabled = changed,
                            modifier = Modifier.fillMaxWidth().testTag("folio_grid_apply"),
                        ) { Text(getString(R.string.folio_grid_apply)) }
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    PhonePreview(getString(R.string.folio_grid_home), Modifier.weight(1f),
                        getString(R.string.folio_grid_preview_home, shownColumns, shownRows,
                            shownDock)) {
                        drawHome(shownColumns, shownRows, shownDock, it)
                    }
                    PhonePreview(getString(R.string.folio_grid_drawer), Modifier.weight(1f),
                        getString(R.string.folio_grid_preview_drawer, shownDrawer)) {
                        drawDrawer(shownDrawer, it)
                    }
                }

                Section(getString(R.string.folio_grid_home))
                SwitchRow(getString(R.string.folio_grid_custom_home),
                    if (customHome) null else getString(R.string.folio_grid_default_is,
                        stock.columns, stock.rows),
                    customHome, "folio_grid_custom_home") { customHome = it }
                Stepper(getString(R.string.folio_grid_columns), columns,
                    FolioGrid.COLUMN_RANGE, customHome, "columns") { columns = it }
                Stepper(getString(R.string.folio_grid_rows), rows,
                    FolioGrid.ROW_RANGE, customHome, "rows") { rows = it }
                SwitchRow(getString(R.string.folio_grid_custom_dock),
                    if (customDock) null else getString(R.string.folio_grid_dock_follows),
                    customDock, "folio_grid_custom_dock") { customDock = it }
                Stepper(getString(R.string.folio_grid_dock), dock,
                    FolioGrid.COLUMN_RANGE, customDock, "dock") { dock = it }

                Section(getString(R.string.folio_grid_drawer))
                SwitchRow(getString(R.string.folio_grid_custom_drawer),
                    if (customDrawer) null else getString(R.string.folio_grid_drawer_default,
                        stock.drawerColumns),
                    customDrawer, "folio_grid_custom_drawer") { customDrawer = it }
                Stepper(getString(R.string.folio_grid_columns), drawer,
                    FolioGrid.COLUMN_RANGE, customDrawer, "drawer") { drawer = it }

                if (saved != FolioGrid.Sizes(0, 0, 0, 0)) {
                    TextButton(
                        {
                            customHome = false; customDock = false; customDrawer = false
                            columns = stock.columns; rows = stock.rows
                            dock = stock.hotseat; drawer = stock.drawerColumns
                        },
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) { Text(getString(R.string.folio_grid_reset)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    @Composable
    private fun Section(title: String) {
        Text(title, Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
    }

    @Composable
    private fun SwitchRow(
        title: String, summary: String?, checked: Boolean, tag: String,
        onChange: (Boolean) -> Unit,
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = summary?.let { { Text(it) } },
            trailingContent = { Switch(checked, null, Modifier.testTag(tag)) },
            modifier = Modifier.clickable { onChange(!checked) }.padding(horizontal = 8.dp),
        )
    }

    @Composable
    private fun Stepper(
        label: String, value: Int, range: IntRange, enabled: Boolean, tag: String,
        onChange: (Int) -> Unit,
    ) {
        val alpha = if (enabled) 1f else 0.38f
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            FilledTonalIconButton(
                { onChange(value - 1) }, enabled = enabled && value > range.first,
                modifier = Modifier.semantics { contentDescription = "Fewer $tag" },
            ) { Icon(Icons.Filled.Remove, null) }
            Text("$value", Modifier.width(48.dp).testTag("folio_grid_$tag"),
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            FilledTonalIconButton(
                { onChange(value + 1) }, enabled = enabled && value < range.last,
                modifier = Modifier.semantics { contentDescription = "More $tag" },
            ) { Icon(Icons.Filled.Add, null) }
        }
    }

    @Composable
    private fun PhonePreview(
        title: String, modifier: Modifier, description: String,
        draw: DrawScope.(Color) -> Unit,
    ) {
        val colors = MaterialTheme.colorScheme
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(9f / 18.5f)
                    .semantics { contentDescription = description },
            ) {
                Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(22.dp),
                    color = colors.surfaceContainerHigh) {}
                Canvas(Modifier.fillMaxSize().padding(10.dp)) { draw(colors.primary) }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }

    /** Icons as dots: the home screen grid, then the dock along the bottom. */
    private fun DrawScope.drawHome(columns: Int, rows: Int, dock: Int, color: Color) {
        val dockHeight = size.height * 0.13f
        val gridHeight = size.height - dockHeight - size.height * 0.04f
        drawGrid(columns, rows, Offset(0f, size.height * 0.04f),
            Size(size.width, gridHeight), color)
        drawRoundRect(color.copy(alpha = 0.12f), Offset(0f, size.height - dockHeight),
            Size(size.width, dockHeight), CornerRadius(dockHeight / 2))
        drawGrid(dock, 1, Offset(0f, size.height - dockHeight), Size(size.width, dockHeight),
            color)
    }

    private fun DrawScope.drawDrawer(columns: Int, color: Color) {
        val rows = 7
        drawRoundRect(color.copy(alpha = 0.12f), Offset.Zero,
            Size(size.width, size.height * 0.06f), CornerRadius(size.height * 0.03f))
        drawGrid(columns, rows, Offset(0f, size.height * 0.1f),
            Size(size.width, size.height * 0.9f), color)
    }

    private fun DrawScope.drawGrid(columns: Int, rows: Int, origin: Offset, area: Size,
            color: Color) {
        val cellW = area.width / columns
        val cellH = area.height / rows
        val radius = minOf(cellW, cellH) * 0.3f
        for (r in 0 until rows) {
            for (c in 0 until columns) {
                drawCircle(color.copy(alpha = 0.75f), radius,
                    Offset(origin.x + cellW * (c + 0.5f), origin.y + cellH * (r + 0.5f)))
            }
        }
    }
}

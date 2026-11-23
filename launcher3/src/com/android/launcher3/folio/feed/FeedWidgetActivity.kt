/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.launcher3.R

/**
 * Manage feeds › At the top › Widget: lists the phone's widgets, and puts the chosen one at the
 * top of the feed panel.
 *
 * Placing a widget takes up to three steps, as on the home screen: allocate an id on
 * [FeedWidget]'s host, get Android's permission to bind it (asked once per app, unless Loom is
 * already allowed), and run the widget's own setup screen if it has one. Cancelling any step
 * releases the id and leaves the current widget as it was.
 */
class FeedWidgetActivity : ComponentActivity() {

    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingId = savedInstanceState?.getInt(KEY_PENDING, -1) ?: -1
        if (pendingId != -1) pendingInfo = FeedWidget.info(this, pendingId)
        val manager = AppWidgetManager.getInstance(this)
        val widgets = manager.getInstalledProvidersForProfile(Process.myUserHandle())
            .map { it to it.loadLabel(packageManager) }
            .sortedWith(compareBy({ appLabel(it.first).lowercase() }, { it.second.lowercase() }))
        setContent { FeedTheme { Picker(widgets) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING, pendingId)
    }

    private fun appLabel(info: AppWidgetProviderInfo): String = runCatching {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(info.provider.packageName, 0)).toString()
    }.getOrDefault(info.provider.packageName)

    private fun appIcon(info: AppWidgetProviderInfo): Drawable? = runCatching {
        packageManager.getApplicationIcon(info.provider.packageName)
    }.getOrNull()

    // --- Placing the chosen widget ---

    private fun begin(info: AppWidgetProviderInfo) {
        val id = FeedWidget.host(this).allocateAppWidgetId()
        pendingId = id
        pendingInfo = info
        val manager = AppWidgetManager.getInstance(this)
        if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            configure()
        } else {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
                REQUEST_BIND)
        }
    }

    private fun configure() {
        val info = pendingInfo ?: FeedWidget.info(this, pendingId)
        val optional = info != null &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info?.configure != null && !optional) {
            try {
                FeedWidget.host(this).startAppWidgetConfigureActivityForResult(
                    this, pendingId, 0, REQUEST_CONFIGURE, null)
                return
            } catch (e: Exception) {
                android.util.Log.w("FeedWidget", "Couldn't open the widget's setup", e)
            }
        }
        done()
    }

    private fun done() {
        FeedWidget.set(this, pendingId)
        pendingId = -1
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun cancel() {
        if (pendingId != -1) runCatching { FeedWidget.host(this).deleteAppWidgetId(pendingId) }
        pendingId = -1
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND -> if (resultCode == Activity.RESULT_OK) configure() else cancel()
            REQUEST_CONFIGURE -> if (resultCode == Activity.RESULT_OK) done() else cancel()
        }
    }

    // --- The list ---

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Picker(widgets: List<Pair<AppWidgetProviderInfo, String>>) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                TopAppBar(
                    title = { Text(getString(R.string.feed_widget_pick_title)) },
                    navigationIcon = {
                        IconButton(::finish) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, getString(R.string.feed_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
                itemsIndexed(widgets) { i, (info, label) ->
                    val icon = remember(info) { appIcon(info)?.toBitmap(96, 96)?.asImageBitmap() }
                    val app = remember(info) { appLabel(info) }
                    ListItem(
                        headlineContent = { Text(label) },
                        supportingContent = {
                            Text(getString(R.string.feed_widget_pick_size, app,
                                maxOf(1, info.targetCellWidth), maxOf(1, info.targetCellHeight)))
                        },
                        leadingContent = {
                            icon?.let { Image(it, null, Modifier.size(40.dp)) }
                        },
                        colors = groupItemColors(),
                        modifier = Modifier.groupCard(groupOf(i, widgets.size))
                            .clickable { begin(info) },
                    )
                }
            }
        }
    }

    companion object {
        private const val KEY_PENDING = "pending_widget"
        private const val REQUEST_BIND = 1
        private const val REQUEST_CONFIGURE = 2
    }
}

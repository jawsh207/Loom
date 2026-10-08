/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import kotlinx.coroutines.launch

/** Feed settings and subscriptions: on/off, refresh, adding, OPML, renaming, folders. */
class FeedSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { FeedTheme { SettingsScreen(onBack = ::finish) } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen(onBack: () -> Unit) {
        val repo = remember { FeedRepository.get(this) }
        val prefs = remember { FeedPrefs.prefs(this) }
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val version by repo.version.collectAsState()
        val feeds by produceState(emptyList<Feed>(), version) { value = repo.feeds() }

        var enabled by remember { mutableStateOf(FeedPrefs.isEnabled(this)) }
        var hours by remember { mutableIntStateOf(FeedPrefs.refreshHours(this)) }
        var wifiOnly by remember { mutableStateOf(FeedPrefs.wifiOnly(this)) }
        var keepDays by remember { mutableIntStateOf(FeedPrefs.keepDays(this)) }
        var dialog by remember { mutableStateOf<String?>(null) }
        var editing by remember { mutableStateOf<Feed?>(null) }

        val importer = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                val msg = try {
                    val n = contentResolver.openInputStream(uri)!!.use { repo.importOpml(it) }
                    resources.getQuantityString(R.plurals.feed_imported, n, n)
                } catch (t: Throwable) {
                    getString(R.string.feed_import_failed)
                }
                snackbar.showSnackbar(msg)
            }
        }
        val exporter = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
            if (uri != null) scope.launch {
                val msg = try {
                    contentResolver.openOutputStream(uri)!!.use { repo.exportOpml(it) }
                    getString(R.string.feed_exported)
                } catch (t: Throwable) {
                    getString(R.string.feed_export_failed)
                }
                snackbar.showSnackbar(msg)
            }
        }
        LaunchedEffect(Unit) {
            if (intent.getBooleanExtra(EXTRA_IMPORT, false)) {
                importer.launch(OPML_TYPES)
            }
        }

        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text(getString(R.string.feed_settings_title)) },
                    navigationIcon = {
                        IconButton(onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                getString(R.string.feed_back))
                        }
                    },
                    scrollBehavior = scroll,
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().testTag("folio_feed_settings"),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text(getString(R.string.feed_enable_title)) },
                        supportingContent = { Text(getString(R.string.feed_enable_summary)) },
                        trailingContent = {
                            Switch(enabled, null, Modifier.testTag("folio_feed_enable"))
                        },
                        modifier = Modifier.clickable {
                            enabled = !enabled
                            prefs.edit().putBoolean(FeedPrefs.ENABLED, enabled).apply()
                        },
                    )
                }
                item { SectionHeader(getString(R.string.feed_section_refresh)) }
                item {
                    ListItem(
                        headlineContent = { Text(getString(R.string.feed_refresh_interval)) },
                        supportingContent = { Text(intervalLabel(hours)) },
                        modifier = Modifier.clickable { dialog = "interval" },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(getString(R.string.feed_wifi_only)) },
                        supportingContent = { Text(getString(R.string.feed_wifi_only_summary)) },
                        trailingContent = { Switch(wifiOnly, null) },
                        modifier = Modifier.clickable {
                            wifiOnly = !wifiOnly
                            prefs.edit().putBoolean(FeedPrefs.WIFI_ONLY, wifiOnly).apply()
                            FeedRefreshJob.schedule(this@FeedSettingsActivity)
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(getString(R.string.feed_keep)) },
                        supportingContent = { Text(keepLabel(keepDays)) },
                        modifier = Modifier.clickable { dialog = "keep" },
                    )
                }
                item {
                    SectionHeader(resources.getQuantityString(R.plurals.feed_section_feeds,
                        feeds.size, feeds.size))
                }
                item {
                    ListItem(
                        headlineContent = { Text(getString(R.string.feed_add)) },
                        leadingContent = { Icon(Icons.Outlined.Add, null) },
                        modifier = Modifier.clickable { dialog = "add" }
                            .testTag("folio_feed_settings_add"),
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(getString(R.string.feed_import_opml)) },
                        supportingContent = { Text(getString(R.string.feed_import_summary)) },
                        leadingContent = { Icon(Icons.Outlined.FileDownload, null) },
                        modifier = Modifier.clickable { importer.launch(OPML_TYPES) },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(getString(R.string.feed_export_opml)) },
                        leadingContent = { Icon(Icons.Outlined.FileUpload, null) },
                        modifier = Modifier.clickable(enabled = feeds.isNotEmpty()) {
                            exporter.launch("folio-feeds.opml")
                        },
                    )
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                items(feeds, key = { it.id }) { feed ->
                    ListItem(
                        headlineContent = {
                            Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(feed.lastError ?: listOfNotNull(feed.folder,
                                    feed.url.substringAfter("://")).joinToString(" · "),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (feed.lastError != null)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        leadingContent = {
                            Icon(if (feed.lastError != null) Icons.Outlined.ErrorOutline
                                else Icons.Outlined.RssFeed, null)
                        },
                        modifier = Modifier.clickable { editing = feed },
                    )
                }
            }
        }

        when (dialog) {
            "interval" -> ChoiceDialog(getString(R.string.feed_refresh_interval),
                FeedPrefs.REFRESH_CHOICES.toList(), hours, ::intervalLabel,
                onDismiss = { dialog = null }) {
                hours = it
                prefs.edit().putInt(FeedPrefs.REFRESH_HOURS, it).apply()
                FeedRefreshJob.schedule(this)
                dialog = null
            }
            "keep" -> ChoiceDialog(getString(R.string.feed_keep),
                FeedPrefs.KEEP_CHOICES.toList(), keepDays, ::keepLabel,
                onDismiss = { dialog = null }) {
                keepDays = it
                prefs.edit().putInt(FeedPrefs.KEEP_DAYS, it).apply()
                dialog = null
            }
            "add" -> AddFeedDialog(repo, onDismiss = { dialog = null })
        }
        editing?.let { feed ->
            EditFeedDialog(feed, onDismiss = { editing = null },
                onSave = { title, folder ->
                    editing = null
                    scope.launch {
                        if (title.isNotBlank() && title != feed.title) repo.rename(feed.id, title)
                        if (folder != (feed.folder ?: "")) repo.setFolder(feed.id, folder)
                    }
                },
                onDelete = {
                    editing = null
                    scope.launch { repo.delete(feed.id) }
                })
        }
    }

    @Composable
    private fun SectionHeader(text: String) {
        Text(text, Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
    }

    @Composable
    private fun ChoiceDialog(
        title: String,
        choices: List<Int>,
        selected: Int,
        label: (Int) -> String,
        onDismiss: () -> Unit,
        onChoose: (Int) -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    for (c in choices) {
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(c == selected, role = Role.RadioButton) { onChoose(c) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(c == selected, null)
                            Text(label(c), Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onDismiss) { Text(getString(android.R.string.cancel)) }
            },
        )
    }

    @Composable
    private fun EditFeedDialog(
        feed: Feed,
        onDismiss: () -> Unit,
        onSave: (String, String) -> Unit,
        onDelete: () -> Unit,
    ) {
        var title by remember { mutableStateOf(feed.title) }
        var folder by remember { mutableStateOf(feed.folder ?: "") }
        var confirmDelete by remember { mutableStateOf(false) }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(getString(R.string.feed_delete_title)) },
                text = { Text(getString(R.string.feed_delete_body, feed.title)) },
                confirmButton = {
                    TextButton(onDelete) { Text(getString(R.string.feed_delete)) }
                },
                dismissButton = {
                    TextButton({ confirmDelete = false }) {
                        Text(getString(android.R.string.cancel))
                    }
                },
            )
            return
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(getString(R.string.feed_edit_title)) },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, singleLine = true,
                        label = { Text(getString(R.string.feed_name)) },
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(folder, { folder = it }, singleLine = true,
                        label = { Text(getString(R.string.feed_folder_optional)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(feed.url, Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (feed.lastChecked > 0) getString(R.string.feed_last_checked,
                            DateUtils.getRelativeTimeSpanString(feed.lastChecked))
                        else getString(R.string.feed_never_checked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    feed.lastError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton({ onSave(title.trim(), folder.trim()) }) {
                    Text(getString(R.string.feed_save))
                }
            },
            dismissButton = {
                Row {
                    TextButton({ confirmDelete = true }) {
                        Text(getString(R.string.feed_delete),
                            color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onDismiss) { Text(getString(android.R.string.cancel)) }
                }
            },
        )
    }

    private fun intervalLabel(hours: Int): String = if (hours <= 0)
        getString(R.string.feed_refresh_manual)
    else resources.getQuantityString(R.plurals.feed_refresh_hours, hours, hours)

    private fun keepLabel(days: Int): String =
        resources.getQuantityString(R.plurals.feed_keep_days, days, days)

    companion object {
        const val EXTRA_IMPORT = "import_opml"
        private val OPML_TYPES = arrayOf("text/x-opml", "text/xml", "application/xml",
            "application/octet-stream", "*/*")
    }
}

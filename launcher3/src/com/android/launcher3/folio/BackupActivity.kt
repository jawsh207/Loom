/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.launcher3.folio.feed.FeedTheme
import com.android.launcher3.folio.feed.Group
import com.android.launcher3.folio.feed.groupCard
import com.android.launcher3.folio.feed.groupItemColors
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR

/** Home settings › Backup & restore. */
class BackupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { FeedTheme { Screen() } }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen() {
        var busy by remember { mutableStateOf(false) }
        var pending by remember { mutableStateOf<Uri?>(null) }

        val create = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            busy = true
            THREAD_POOL_EXECUTOR.execute {
                val ok = runCatching { FolioBackup.backup(this, uri) }
                    .onFailure { android.util.Log.e("FolioBackup", "Backup failed", it) }
                    .isSuccess
                runOnUiThread {
                    busy = false
                    toast(if (ok) R.string.folio_backup_done else R.string.folio_backup_failed)
                }
            }
        }
        val open = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()) { uri -> pending = uri }

        pending?.let { uri ->
            AlertDialog(
                onDismissRequest = { pending = null },
                icon = { Icon(Icons.Outlined.Warning, null) },
                title = { Text(getString(R.string.folio_restore_confirm_title)) },
                text = { Text(getString(R.string.folio_restore_confirm_body)) },
                confirmButton = {
                    TextButton({
                        pending = null
                        busy = true
                        THREAD_POOL_EXECUTOR.execute {
                            val result = runCatching { FolioBackup.restore(this, uri) }
                            runOnUiThread {
                                val error = result.exceptionOrNull()
                                if (error == null) {
                                    toast(R.string.folio_restore_done)
                                    FolioBackup.restart(this)
                                } else {
                                    android.util.Log.e("FolioBackup", "Restore failed", error)
                                    busy = false
                                    toast(when ((error as? FolioBackup.BackupException)?.message) {
                                        "newer" -> R.string.folio_restore_newer
                                        "not_a_backup" -> R.string.folio_restore_not_backup
                                        else -> R.string.folio_restore_failed
                                    })
                                }
                            }
                        }
                    }, Modifier.testTag("folio_restore_confirm")) {
                        Text(getString(R.string.folio_restore_confirm),
                            color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton({ pending = null }) { Text(getString(android.R.string.cancel)) }
                },
            )
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                TopAppBar(
                    title = { Text(getString(R.string.folio_backup_title)) },
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
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
            ) {
                Text(getString(R.string.folio_backup_intro),
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ListItem(
                    headlineContent = { Text(getString(R.string.folio_backup_now)) },
                    supportingContent = { Text(getString(R.string.folio_backup_now_summary)) },
                    leadingContent = { Icon(Icons.Outlined.Backup, null) },
                    colors = groupItemColors(),
                    modifier = Modifier.groupCard(Group.Top).fillMaxWidth()
                        .clickable(enabled = !busy) { create.launch(FolioBackup.suggestedName()) },
                )
                ListItem(
                    headlineContent = { Text(getString(R.string.folio_restore_now)) },
                    supportingContent = { Text(getString(R.string.folio_restore_now_summary)) },
                    leadingContent = { Icon(Icons.Outlined.Restore, null) },
                    colors = groupItemColors(),
                    modifier = Modifier.groupCard(Group.Bottom).fillMaxWidth()
                        .clickable(enabled = !busy) {
                            open.launch(arrayOf("application/zip", "application/octet-stream"))
                        },
                )
                if (busy) {
                    CircularProgressIndicator(Modifier.padding(24.dp))
                }
                Text(getString(R.string.folio_backup_note),
                    Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

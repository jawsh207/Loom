/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/** What the panel is showing; kept outside Compose so the launcher can handle Back. */
class FeedPanelState(context: Context) {
    val repo = FeedRepository.get(context)
    var filter by mutableStateOf(FeedFilter(
        unreadOnly = FeedPrefs.prefs(context).getBoolean(FeedPrefs.UNREAD_ONLY, false)))
    var openItem by mutableStateOf<FeedItem?>(null)
    var textScale by mutableFloatStateOf(FeedPrefs.textScale(context))
    var showAddDialog by mutableStateOf(false)
    /** System bar sizes, from the launcher (which keeps window insets to itself). */
    var topInset by mutableStateOf(0.dp)
    var bottomInset by mutableStateOf(0.dp)

    /** Handles Back inside the panel; false when the panel itself should close. */
    fun onBack(): Boolean {
        if (showAddDialog) { showAddDialog = false; return true }
        if (openItem != null) { openItem = null; return true }
        return false
    }
}

/** A list row: the item plus its summary as plain text, worked out off the main thread. */
private data class Entry(val item: FeedItem, val snippet: String)

@Composable
fun FeedPanel(state: FeedPanelState, onOpenSettings: () -> Unit, onImport: () -> Unit) {
    FeedTheme {
        Surface(Modifier.fillMaxSize().testTag("folio_feed_panel"),
            color = MaterialTheme.colorScheme.surface) {
            AnimatedContent(
                targetState = state.openItem,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250)))
                            .togetherWith(fadeOut(tween(150)))
                    } else {
                        fadeIn(tween(200)).togetherWith(
                            slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(200)))
                    }
                },
                contentKey = { it?.id },
                label = "feed",
            ) { item ->
                if (item == null) {
                    FeedList(state, onOpenSettings, onImport)
                } else {
                    ArticleScreen(state, item)
                }
            }
        }
        if (state.showAddDialog) {
            AddFeedDialog(state.repo, onDismiss = { state.showAddDialog = false })
        }
    }
}

// --- The list ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedList(state: FeedPanelState, onOpenSettings: () -> Unit, onImport: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by state.repo.version.collectAsState()
    val refreshing by state.repo.refreshing.collectAsState()
    val feeds by produceState<List<Feed>?>(null, version) { value = state.repo.feeds() }
    val entries by produceState<List<Entry>?>(null, version, state.filter) {
        val items = state.repo.items(state.filter)
        value = withContext(Dispatchers.Default) {
            items.map { Entry(it, snippetOf(it)) }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.filter) { listState.scrollToItem(0) }
    // Check for new items when the panel opens (skipped if checked a few minutes ago).
    LaunchedEffect(Unit) { state.repo.refresh() }

    Column(Modifier.fillMaxSize().padding(top = state.topInset)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourcePicker(state, feeds.orEmpty(), Modifier.weight(1f))
            if (refreshing) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton({ scope.launch { state.repo.refresh(force = true) } }) {
                    Icon(Icons.Outlined.Refresh, stringResource(context, R.string.feed_refresh))
                }
            }
            OverflowMenu(state, onOpenSettings)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.filter.unreadOnly,
                onClick = {
                    val unread = !state.filter.unreadOnly
                    state.filter = state.filter.copy(unreadOnly = unread)
                    FeedPrefs.prefs(context).edit().putBoolean(FeedPrefs.UNREAD_ONLY, unread)
                        .apply()
                },
                label = { Text(stringResource(context, R.string.feed_filter_unread)) },
            )
            FilterChip(
                selected = state.filter.starredOnly,
                onClick = {
                    state.filter = state.filter.copy(starredOnly = !state.filter.starredOnly)
                },
                label = { Text(stringResource(context, R.string.feed_filter_starred)) },
                leadingIcon = if (state.filter.starredOnly) {
                    { Icon(Icons.Filled.Star, null, Modifier.size(18.dp)) }
                } else null,
            )
        }
        val list = entries
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { state.repo.refresh(force = true) } },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                feeds?.isEmpty() == true -> EmptyState(
                    title = stringResource(context, R.string.feed_empty_title),
                    body = stringResource(context, R.string.feed_empty_body),
                    primary = stringResource(context, R.string.feed_add) to
                        { state.showAddDialog = true },
                    secondary = stringResource(context, R.string.feed_import_opml) to onImport,
                )
                list != null && list.isEmpty() -> EmptyState(
                    title = stringResource(context, R.string.feed_caught_up_title),
                    body = stringResource(context, R.string.feed_caught_up_body),
                    primary = null, secondary = null,
                )
                list != null -> LazyColumn(
                    Modifier.fillMaxSize().testTag("folio_feed_list"),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp,
                        bottom = 16.dp + state.bottomInset),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.item.id }) { e ->
                        ItemCard(state, e, onOpen = {
                            state.openItem = e.item
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePicker(state: FeedPanelState, feeds: List<Feed>, modifier: Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val f = state.filter
    val title = when {
        f.feedId != null -> feeds.firstOrNull { it.id == f.feedId }?.title
        f.folder != null -> f.folder
        else -> null
    } ?: stringResource(context, R.string.feed_title)
    Box(modifier) {
        TextButton({ open = true }, Modifier.semantics {
            contentDescription = context.getString(R.string.feed_choose_source)
        }) {
            Text(title, style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
            Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(open, { open = false }) {
            val totalUnread = feeds.sumOf { it.unread }
            SourceRow(stringResource(context, R.string.feed_all), totalUnread) {
                state.filter = f.copy(feedId = null, folder = null); open = false
            }
            val folders = feeds.mapNotNull { it.folder }.distinct()
            if (folders.isNotEmpty()) HorizontalDivider()
            for (folder in folders) {
                SourceRow(folder, feeds.filter { it.folder == folder }.sumOf { it.unread }) {
                    state.filter = f.copy(feedId = null, folder = folder); open = false
                }
            }
            if (feeds.isNotEmpty()) HorizontalDivider()
            for (feed in feeds) {
                SourceRow(feed.title, feed.unread, error = feed.lastError) {
                    state.filter = f.copy(feedId = feed.id, folder = null); open = false
                }
            }
        }
    }
}

@Composable
private fun SourceRow(name: String, unread: Int, error: String? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        trailingIcon = if (unread > 0) {
            { Text("$unread", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary) }
        } else null,
        onClick = onClick,
    )
}

@Composable
private fun OverflowMenu(state: FeedPanelState, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) {
            Icon(Icons.Outlined.MoreVert, stringResource(context, R.string.feed_more))
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(context, R.string.feed_mark_all_read)) },
                leadingIcon = { Icon(Icons.Outlined.DoneAll, null) },
                onClick = {
                    open = false
                    scope.launch { state.repo.markAllRead(state.filter.copy(unreadOnly = false)) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(context, R.string.feed_add)) },
                leadingIcon = { Icon(Icons.Outlined.Add, null) },
                onClick = { open = false; state.showAddDialog = true },
            )
            DropdownMenuItem(
                text = { Text(stringResource(context, R.string.feed_manage)) },
                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                onClick = { open = false; onOpenSettings() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(state: FeedPanelState, e: Entry, onOpen: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val item = e.item
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (item.read) colors.surface else colors.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
        ) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.feedTitle + " · " + relativeTime(item.published),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.read) colors.onSurfaceVariant else colors.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, false),
                        )
                        if (item.starred) {
                            Icon(Icons.Filled.Star, null, Modifier.padding(start = 4.dp)
                                .size(14.dp), tint = colors.tertiary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (item.read) colors.onSurfaceVariant else colors.onSurface,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    if (e.snippet.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(e.snippet, style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                if (item.image != null) {
                    RemoteImage(item.image, null,
                        Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)), maxWidth = 240)
                }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(context,
                    if (item.read) R.string.feed_mark_unread else R.string.feed_mark_read)) },
                onClick = { menu = false; scope.launch { state.repo.setRead(item.id, !item.read) } },
            )
            DropdownMenuItem(
                text = { Text(stringResource(context,
                    if (item.starred) R.string.feed_unstar else R.string.feed_star)) },
                onClick = {
                    menu = false
                    scope.launch { state.repo.setStarred(item.id, !item.starred) }
                },
            )
            item.link?.let { link ->
                DropdownMenuItem(
                    text = { Text(stringResource(context, R.string.feed_share)) },
                    onClick = { menu = false; share(context, item.title, link) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(context, R.string.feed_open_browser)) },
                    onClick = { menu = false; openInBrowser(context, link) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>?,
) {
    // Inside a scrollable so pull-to-refresh still works on an empty list.
    LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(32.dp).padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.RssFeed, null, Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                primary?.let { (label, action) -> Button(action) { Text(label) } }
                secondary?.let { (label, action) -> OutlinedButton(action) { Text(label) } }
            }
        }
    }
}

// --- Adding a feed ---

@Composable
fun AddFeedDialog(repo: FeedRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.RssFeed, null) },
        title = { Text(stringResource(context, R.string.feed_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    url, { url = it; error = null },
                    label = { Text(stringResource(context, R.string.feed_add_url)) },
                    placeholder = { Text("example.com/feed") },
                    singleLine = true, enabled = !busy,
                    isError = error != null,
                    supportingText = {
                        Text(error ?: stringResource(context, R.string.feed_add_url_hint))
                    },
                    modifier = Modifier.fillMaxWidth().testTag("folio_feed_add_url"),
                )
                OutlinedTextField(
                    folder, { folder = it },
                    label = { Text(stringResource(context, R.string.feed_folder_optional)) },
                    singleLine = true, enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            repo.addFeed(url, folder.trim().ifEmpty { null })
                            onDismiss()
                        } catch (t: Throwable) {
                            error = FeedRepository.messageFor(t)
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(stringResource(context, R.string.feed_add_confirm)) }
        },
        dismissButton = {
            TextButton({ onDismiss() }, enabled = !busy) {
                Text(stringResource(context, android.R.string.cancel))
            }
        },
    )
}

// --- Reading an article ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleScreen(state: FeedPanelState, post: FeedItem) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    var starred by remember(post.id) { mutableStateOf(post.starred) }
    LaunchedEffect(post.id) { if (!post.read) state.repo.setRead(post.id, true) }

    // The feed's own text right away; the extracted article replaces it when it's ready.
    var loading by remember(post.id) { mutableStateOf(true) }
    val article by produceState<Article?>(null, post.id) {
        value = try {
            state.repo.article(post)
        } catch (t: Throwable) {
            null
        }
        loading = false
    }
    val linkColor = colors.primary
    val shown = article
    val html = shown?.html?.takeIf { it.isNotBlank() } ?: post.content ?: post.summary ?: ""
    val heroImage = post.image
    val blocks by produceState(emptyList<ArticleBlock>(), html, linkColor) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                ArticleHtml(linkColor).parse(html, shown?.baseUrl ?: post.link, heroImage)
            }.getOrDefault(emptyList())
        }
    }
    val body = MaterialTheme.typography.bodyLarge.let {
        it.copy(fontSize = it.fontSize * state.textScale * 1.05f,
            lineHeight = it.lineHeight * state.textScale * 1.2f)
    }

    Column(Modifier.fillMaxSize().padding(top = state.topInset)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton({ state.openItem = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(context, R.string.feed_back))
            }
            Text(post.feedTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton({
                starred = !starred
                scope.launch { state.repo.setStarred(post.id, starred) }
            }) {
                if (starred) {
                    Icon(Icons.Filled.Star, stringResource(context, R.string.feed_unstar),
                        tint = colors.tertiary)
                } else {
                    Icon(Icons.Outlined.StarOutline, stringResource(context, R.string.feed_star))
                }
            }
            TextSizeMenu(state)
            post.link?.let { link ->
                IconButton({ share(context, post.title, link) }) {
                    Icon(Icons.Outlined.Share, stringResource(context, R.string.feed_share))
                }
                IconButton({ openInBrowser(context, link) }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew,
                        stringResource(context, R.string.feed_open_browser))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LazyColumn(
            Modifier.fillMaxSize().testTag("folio_feed_article"),
            contentPadding = PaddingValues(bottom = 24.dp + state.bottomInset),
        ) {
            item {
                Text(post.title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.headlineSmall.let {
                        it.copy(fontSize = it.fontSize * state.textScale)
                    })
            }
            item {
                val meta = listOfNotNull(shown?.byline ?: post.author,
                    if (post.published > 0) DateUtils.formatDateTime(context, post.published,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                            DateUtils.FORMAT_ABBREV_MONTH) else null)
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString(" · "),
                        Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant)
                }
            }
            if (heroImage != null) {
                item {
                    RemoteImage(heroImage, null,
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
                }
            }
            if (!loading && shown?.extracted != true && post.link != null) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp), color = colors.secondaryContainer,
                    ) {
                        Text(stringResource(context, R.string.feed_article_fallback),
                            Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSecondaryContainer)
                    }
                }
            }
            articleBlocks(blocks, body)
            post.link?.let { link ->
                item {
                    FilledTonalButton({ openInBrowser(context, link) },
                        Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(context, R.string.feed_open_original))
                    }
                }
            }
        }
    }
}

@Composable
private fun TextSizeMenu(state: FeedPanelState) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) {
            Icon(Icons.Outlined.FormatSize, stringResource(context, R.string.feed_text_size))
        }
        DropdownMenu(open, { open = false }) {
            for ((label, scale) in listOf(R.string.feed_text_small to 0.9f,
                    R.string.feed_text_normal to 1f, R.string.feed_text_large to 1.15f,
                    R.string.feed_text_larger to 1.3f, R.string.feed_text_largest to 1.5f)) {
                DropdownMenuItem(
                    text = { Text(stringResource(context, label)) },
                    trailingIcon = if (state.textScale == scale) {
                        { Icon(Icons.Outlined.DoneAll, null) }
                    } else null,
                    onClick = {
                        open = false
                        state.textScale = scale
                        FeedPrefs.prefs(context).edit().putFloat(FeedPrefs.TEXT_SCALE, scale)
                            .apply()
                    },
                )
            }
        }
    }
}

// --- Helpers ---

private fun stringResource(context: Context, id: Int) = context.getString(id)

private fun snippetOf(item: FeedItem): String {
    val html = item.summary ?: item.content ?: return ""
    val text = runCatching { Jsoup.parse(html).text() }.getOrDefault("")
    return text.take(240).trim()
}

private fun relativeTime(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun share(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    runCatching {
        context.startActivity(Intent.createChooser(send, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

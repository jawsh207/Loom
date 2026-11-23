/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.folio.feed

import android.Manifest
import android.app.AlarmManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.launcher3.R
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * What can sit above the stories in the feed panel (Manage feeds › At the top):
 *
 * - **Coming up**: today's date, the next alarm, and calendar events for today and tomorrow.
 *   The alarm needs no permission (Android tells any app the next alarm); events need calendar
 *   access, asked for when it's turned on. Nothing leaves the phone: events are read from the
 *   phone's own calendar storage, which calendar apps and sync services fill.
 * - **A widget**: any one widget, chosen in [FeedWidgetActivity] and kept by [FeedWidget].
 */
@Composable
fun FeedTop(state: FeedPanelState) {
    if (!state.topUpcoming && state.topWidget == -1) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.topUpcoming) UpcomingCard()
        // A new widget needs a new view, not the old one rebound.
        if (state.topWidget != -1) key(state.topWidget) { WidgetCard(state.topWidget) }
    }
}

// --- Coming up ---

private class Upcoming(
    val alarm: AlarmManager.AlarmClockInfo?,
    val events: List<Event>,
    val calendarAllowed: Boolean,
)

private class Event(
    val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean,
    val color: Int,
)

private const val MAX_EVENTS = 4

private fun calendarAllowed(context: Context) =
    context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

private fun loadUpcoming(context: Context): Upcoming {
    val alarm = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
    if (!calendarAllowed(context)) return Upcoming(alarm, emptyList(), false)
    val now = System.currentTimeMillis()
    // Up to the end of tomorrow.
    val until = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 2)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, now - DateUtils.DAY_IN_MILLIS) // all-day events run in UTC
        ContentUris.appendId(it, until)
    }.build()
    val events = ArrayList<Event>()
    try {
        context.contentResolver.query(uri, arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
        ), "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != " +
            "${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}",
            null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(4) == 1
                val begin = c.getLong(2)
                val end = c.getLong(3)
                // All-day events are stored as UTC midnights: compare them as local days.
                val endLocal = if (allDay) end - java.util.TimeZone.getDefault().getOffset(end) else end
                val beginLocal = if (allDay) begin - java.util.TimeZone.getDefault().getOffset(begin) else begin
                if (endLocal <= now || beginLocal >= until) continue
                events.add(Event(c.getLong(0), c.getString(1).orEmpty(), beginLocal, endLocal,
                    allDay, c.getInt(5)))
            }
        }
    } catch (e: Exception) {
        Log.w("FeedTop", "Couldn't read the calendar", e)
    }
    return Upcoming(alarm, events.sortedWith(compareBy({ !it.allDay }, { it.begin }))
        .take(MAX_EVENTS), true)
}

@Composable
private fun UpcomingCard() {
    val context = LocalContext.current
    var upcoming by remember { mutableStateOf<Upcoming?>(null) }
    LaunchedEffect(Unit) {
        // Kept current while the panel exists: the clock ticks, alarms and events change.
        while (true) {
            upcoming = withContext(Dispatchers.IO) { loadUpcoming(context) }
            delay(60_000)
        }
    }
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().testTag("folio_feed_upcoming"),
        shape = RoundedCornerShape(24.dp), color = colors.surfaceBright,
        contentColor = colors.onSurface) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(DateUtils.formatDateTime(context, System.currentTimeMillis(),
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_NO_YEAR),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleLarge)
            val u = upcoming ?: return@Column
            u.alarm?.let { alarm ->
                Line(
                    leading = { Icon(Icons.Outlined.Alarm, null, Modifier.size(20.dp)) },
                    title = context.getString(R.string.feed_top_alarm),
                    detail = whenLabel(context, alarm.triggerTime, alarm.triggerTime, false),
                ) {
                    runCatching { alarm.showIntent?.send() ?: error("no intent") }.onFailure {
                        runCatching {
                            context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            }
            u.events.forEach { e ->
                Line(
                    leading = {
                        Box(Modifier.size(12.dp).clip(CircleShape)
                            .background(Color(e.color or 0xFF000000.toInt())))
                    },
                    title = e.title.ifBlank { context.getString(R.string.feed_top_untitled) },
                    detail = whenLabel(context, e.begin, e.end, e.allDay),
                ) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            if (u.alarm == null && u.events.isEmpty()) {
                Text(context.getString(if (u.calendarAllowed) R.string.feed_top_nothing
                    else R.string.feed_top_nothing_alarm),
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (!u.calendarAllowed) {
                Line(
                    leading = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(20.dp)) },
                    title = context.getString(R.string.feed_top_calendar_allow),
                    detail = null,
                ) {
                    context.startActivity(Intent(context, FeedSettingsActivity::class.java)
                        .putExtra(FeedSettingsActivity.EXTRA_ASK_CALENDAR, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

@Composable
private fun Line(
    leading: @Composable () -> Unit, title: String, detail: String?, onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Today · 2:30 – 3:00 PM", "Tomorrow · All day", "Wed · 7:00 AM". */
private fun whenLabel(context: Context, begin: Long, end: Long, allDay: Boolean): String {
    val day = when {
        DateUtils.isToday(begin) || (allDay && begin < System.currentTimeMillis()) ->
            context.getString(R.string.feed_top_today)
        DateUtils.isToday(begin - DateUtils.DAY_IN_MILLIS) ->
            context.getString(R.string.feed_top_tomorrow)
        else -> DateUtils.formatDateTime(context, begin, DateUtils.FORMAT_SHOW_WEEKDAY or
            DateUtils.FORMAT_ABBREV_WEEKDAY)
    }
    val time = when {
        allDay -> context.getString(R.string.feed_top_all_day)
        end > begin -> DateUtils.formatDateRange(context, begin, end, DateUtils.FORMAT_SHOW_TIME)
        else -> DateFormat.getTimeFormat(context).format(java.util.Date(begin))
    }
    return "$day · $time"
}

// --- The widget ---

@Composable
private fun WidgetCard(id: Int) {
    val context = LocalContext.current
    val info = remember(id) { FeedWidget.info(context, id) } ?: return
    val heightDp = remember(info) { FeedWidget.heightDp(context, info) }
    Surface(Modifier.fillMaxWidth().testTag("folio_feed_widget"),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(heightDp.dp)) {
            val widthDp = maxWidth.value
            LaunchedEffect(id, widthDp) { FeedWidget.reportSize(context, id, widthDp, heightDp) }
            AndroidView(
                factory = { c -> FeedWidget.createView(c, id, info) },
                modifier = Modifier.fillMaxWidth().height(heightDp.dp),
            )
        }
    }
}

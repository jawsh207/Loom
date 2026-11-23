#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test of the feed panel's newer parts: selectable article text and the top of the feed.

1. Selecting text: long-pressing a word in an article selects it (its handles and Copy menu
   open as popup windows); pressing, holding and then dragging sideways selects instead of
   closing the panel, while a quick sideways swipe still closes it.
2. Coming up (Manage feeds › At the top): with an alarm set (through the Clock app) and an event
   added to a local calendar, turning it on asks for calendar access, and the panel then shows
   the date, the alarm and the events, including one three weeks away.
3. Widget (Manage feeds › At the top › Widget): choosing Clock's analog clock puts it at the top
   of the feed (bound to the feed's own widget host); removing it takes it away.

usage: devtest_feedtop.py OUT_DIR LOOM_APK"""

import re
import time

from devtest import find, log, open_home_settings, save_logs, screen_size, setup_home, sh, snap, \
    tap, tap_text
from devtest_feed import LOCAL_RSS, add_feed, choose_source, scroll_to
from devtest_grid import center, go_home, tap_scrolled

FEED_HOST = 0x4c6f6f6d


def open_panel(name):
    """Swipes right from the first page; tries again if the home screen wasn't ready for it."""
    w, h = screen_size()
    for attempt in range(3):
        go_home()
        time.sleep(2)
        sh(f"input swipe {w // 8} {h // 2} {w * 7 // 8} {h // 2} 350")
        time.sleep(5)
        nodes = snap(f"{name}-{attempt}")
        if find(nodes, r"^choose which feeds to show$"):
            return nodes
    log(f"  !! the feed panel didn't open ({name})")
    return nodes


def open_manage_feeds(name):
    open_home_settings()
    tap_scrolled(r"^feeds$", name, wait=4)


def panel_open():
    return "app.folio.launcher" in sh("dumpsys window | grep -m1 mCurrentFocus") and \
        bool(find(snap("panel-check"), r"^refresh$|^share$|^listen$"))


def popups():
    """Loom's popup windows: the selection handles and the Copy menu are each one."""
    out = sh("dumpsys window windows | grep -E 'Window #.*(PopupWindow|FloatingToolbar|Magnifier)'")
    return sum(1 for l in out.splitlines() if l.strip())


def add_calendar_event():
    """A local calendar with one event two hours from now, through the calendar provider."""
    out = sh("content insert --uri 'content://com.android.calendar/calendars?caller_is_syncadapter=true"
             "&account_name=test&account_type=LOCAL' --bind account_name:s:test "
             "--bind account_type:s:LOCAL --bind name:s:Test --bind calendar_displayName:s:Test "
             "--bind calendar_color:i:-16776961 --bind calendar_access_level:i:700 "
             "--bind ownerAccount:s:test --bind visible:i:1 --bind sync_events:i:1")
    cal = sh("content query --uri content://com.android.calendar/calendars --projection _id")
    m = re.findall(r"_id=(\d+)", cal)
    log(f"  calendar: {out.strip()[:80]!r} ids {m}")
    if not m:
        return False
    start = int(time.time() * 1000) + 2 * 3600 * 1000
    out = sh(f"content insert --uri content://com.android.calendar/events "
             f"--bind calendar_id:i:{m[-1]} --bind title:s:Dentist --bind dtstart:l:{start} "
             f"--bind dtend:l:{start + 3600 * 1000} --bind eventTimezone:s:UTC")
    log(f"  event added: {out.strip()[:80]!r}")
    # And one three weeks away: Coming up shows the next events however far ahead they are.
    later = start + 21 * 24 * 3600 * 1000
    sh(f"content insert --uri content://com.android.calendar/events "
       f"--bind calendar_id:i:{m[-1]} --bind title:s:Passport --bind dtstart:l:{later} "
       f"--bind dtend:l:{later + 3600 * 1000} --bind eventTimezone:s:UTC")
    return True


def main():
    setup_home()
    w, h = screen_size()

    # The feed panel on, with the local test feed.
    open_manage_feeds("feeds")
    tap_text(r"^feed panel$", "feed-on", wait=2)
    scroll_to(r"^add feed$", "find-add")  # below the settings, at the end of the list
    add_feed(LOCAL_RSS, "rss")
    go_home()

    # --- 1. Selecting article text ---
    open_panel("panel")
    choose_source(r"^folio test gazette")
    if tap_text(r"lighthouse keepers", "article", wait=6):
        nodes = snap("article-open")
        para = find(nodes, r"a life measured in hours")
        if para:
            x, y = center(para)
            before = popups()
            sh(f"input swipe {x} {y} {x} {y} 1200")  # long-press a word
            time.sleep(2)
            snap("selected")
            log(f"  long-press selects (handles and the Copy menu open): {popups() > before} "
                f"({before} -> {popups()} popup windows)")
            tap(find(nodes, r"^ada marlow"))  # a tap elsewhere clears the selection
            time.sleep(1)
            log(f"  still reading the article: {panel_open()}")
            # Press and hold, then drag sideways in the same touch: extends a selection.
            sh(f"input motionevent DOWN {x} {y}")
            time.sleep(1.2)
            for step in range(1, 9):
                sh(f"input motionevent MOVE {x - step * w // 20} {y}")
            sh(f"input motionevent UP {x - 8 * w // 20} {y}")
            time.sleep(2)
            snap("hold-drag")
            log(f"  press-hold-drag sideways keeps the panel open: {panel_open()}")
            tap(find(nodes, r"^ada marlow"))
            time.sleep(1)
            # A quick sideways swipe still closes it.
            sh(f"input swipe {w * 7 // 8} {y} {w // 8} {y} 250")
            time.sleep(3)
            log(f"  a quick swipe still closes the panel: {not panel_open()}")
        else:
            log("  !! no paragraph to select")
    go_home()

    # --- 2. Coming up ---
    sh("am start -W -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR 7 "
       "--ei android.intent.extra.alarm.MINUTES 30 --ez android.intent.extra.alarm.SKIP_UI true")
    time.sleep(3)
    log(f"  next alarm set: {'7:30' in sh('dumpsys alarm | grep -iA2 nextAlarmClock') or bool(sh('dumpsys alarm | grep -i alarm_clock'))}")
    go_home()
    add_calendar_event()
    open_manage_feeds("feeds-top")
    tap_scrolled(r"^coming up$", "upcoming-on", wait=3)
    nodes = snap("calendar-permission")
    allow = find(nodes, r"^allow$|^while using the app$")
    log(f"  turning it on asks for calendar access: {bool(allow)}")
    if allow:
        tap(allow)
        time.sleep(2)
    go_home()
    nodes = open_panel("panel-upcoming")
    log(f"  shows the date: {bool(find(nodes, time.strftime('%A')))}")
    log(f"  shows the alarm: {bool(find(nodes, r'^alarm$'))}")
    log(f"  shows the event: {bool(find(nodes, r'^dentist$'))}")
    far = find(nodes, r'^passport$')
    log(f"  shows the event three weeks away: {bool(far)}; "
        f"dated: {bool(find(nodes, time.strftime('%b', time.localtime(time.time() + 21 * 86400 + 7200))))}")
    go_home()

    # --- 3. A widget ---
    open_manage_feeds("feeds-widget")
    tap_scrolled(r"^widget$", "widget-row", wait=3)
    picked = False
    for _ in range(6):
        nodes = snap("widget-picker")
        clock = find(nodes, r"^analog clock$")
        if clock:
            tap(clock)
            picked = True
            time.sleep(4)
            break
        sh(f"input swipe {w // 2} {h * 3 // 4} {w // 2} {h // 3} 400")
        time.sleep(1.5)
    log(f"  Clock's analog clock in the list: {picked}")
    nodes = snap("widget-bind")
    always = find(nodes, r"always allow|allow .* to create widgets")
    if always:
        tap(always)
        time.sleep(1)
    create = find(nodes, r"^create$|^allow$")
    log(f"  Android asks to allow the widget: {bool(create)}")
    if create:
        tap(create)
        time.sleep(4)
    nodes = snap("after-widget")
    log(f"  Widget row names it: {bool(find(nodes, r'^analog clock$'))}")
    hosts = sh("dumpsys appwidget")
    log(f"  bound to the feed's widget host: "
        f"{str(FEED_HOST) in hosts or ('%x' % FEED_HOST) in hosts.lower()}")
    go_home()
    nodes = open_panel("panel-widget")
    log(f"  widget shows in the panel (Coming up still there): "
        f"{bool(find(nodes, r'^alarm$'))}; views {sorted(set(n['id'] for n in nodes if 'deskclock' in n['id'] or 'analog' in n['id'].lower()))[:3]}")
    go_home()
    open_manage_feeds("feeds-remove")
    tap_scrolled(r"^remove widget$", "widget-remove", wait=2)
    nodes = snap("after-remove")
    log(f"  removed: {bool(find(nodes, r'^none\. tap to choose one\.$'))}")
    go_home()
    save_logs()


if __name__ == "__main__":
    main()

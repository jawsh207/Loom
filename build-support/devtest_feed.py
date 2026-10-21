#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test of the feed panel: turn it on in Settings › Feeds, add feeds (a local RSS feed, a
local site found through autodiscovery, and a real one), open the panel with a swipe, read an
article, and swipe the panel away. Screenshots and logs go to the output directory.

usage: devtest_feed.py OUT_DIR FOLIO_APK
The local test site (build-support/devtest-feed) must be served on the host at port 8000,
which the emulator reaches as 10.0.2.2."""

import re
import time

from devtest import (PKG, find, log, save_logs, screen_size, setup_home, sh, snap, tap,
                     tap_text, top_activity)

LOCAL_RSS = "http://10.0.2.2:8000/rss.xml"
LOCAL_SITE = "http://10.0.2.2:8000/"
REAL_FEED = "https://feeds.arstechnica.com/arstechnica/index"


def type_text(text):
    # `input text` needs spaces escaped; URLs have none.
    sh(f"input text '{text}'")


def open_folio_settings():
    sh(f"am start -a android.intent.action.APPLICATION_PREFERENCES -p {PKG}")
    time.sleep(4)


def scroll_to(pattern, name, tries=6):
    w, h = screen_size()
    for i in range(tries):
        nodes = snap(f"{name}-find{i}")
        n = find(nodes, pattern)
        if n:
            return n
        sh(f"input swipe {w // 2} {h * 3 // 4} {w // 2} {h // 3} 400")
        time.sleep(1.5)
    return None


def back_gesture(name):
    """A back swipe in from the right edge (gesture navigation), not the Back key."""
    w, h = screen_size()
    sh(f"input swipe {w - 2} {h // 2} {w * 3 // 5} {h // 2} 250")
    time.sleep(2)
    return snap(name)


def add_feed(url, label):
    if not tap_text(r"^add feed$", f"add-{label}", wait=2):
        return False
    nodes = snap(f"add-{label}-dialog")
    field = find(nodes, r"feed or website address")
    if field:
        tap(field)
        time.sleep(1)
    type_text(url)
    time.sleep(1)
    tap_text(r"^add$", f"add-{label}-confirm", wait=12)
    nodes = snap(f"add-{label}-done")
    err = find(nodes, r"couldn|no feed|server|doesn.t look")
    log(f"  add {url}: {'ERROR ' + (err['text'] or err['desc']) if err else 'ok'}")
    if find(nodes, r"^add$"):
        sh("input keyevent KEYCODE_BACK")  # close a dialog that stayed open
        time.sleep(1)
    return err is None


def choose_source(pattern):
    if tap_text(r"choose which feeds", "source-menu", wait=2):
        tap_text(pattern, "source-pick", wait=3)


def main():
    setup_home()
    # Gesture navigation, so Back is the edge swipe the home screen can switch off.
    sh("cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural")
    time.sleep(4)
    log(f"  navigation mode: {sh('settings get secure navigation_mode').strip()} (2 = gestures)")
    snap("home")

    # 1. Open Feeds in Folio's settings and turn the feed panel on there.
    open_folio_settings()
    n = scroll_to(r"^feeds$", "settings")
    if not n:
        log("!! no Feeds setting")
        save_logs()
        return
    tap(n)
    time.sleep(4)
    if not tap_text(r"^feed panel$", "feeds-enable", wait=2):
        save_logs()
        return
    snap("settings-enabled")

    # 2. Add feeds.
    snap("feeds-screen")
    add_feed(LOCAL_RSS, "rss")
    add_feed(LOCAL_SITE, "site")
    add_feed(REAL_FEED, "real")
    snap("feeds-added")

    # 3. Go home and swipe right from the first page to open the panel.
    sh("input keyevent KEYCODE_HOME")
    time.sleep(3)
    sh("input keyevent KEYCODE_HOME")  # back to the first page
    time.sleep(2)
    w, h = screen_size()
    # Halfway through the swipe: the home screen fading behind the panel's rounded edge.
    sh(f"input motionevent DOWN {w // 8} {h // 2}")
    for x in range(w // 8, w // 2, w // 16):
        sh(f"input motionevent MOVE {x} {h // 2}")
    time.sleep(1)
    snap("panel-half")
    sh(f"input motionevent MOVE {w * 7 // 8} {h // 2}")
    sh(f"input motionevent UP {w * 7 // 8} {h // 2}")
    time.sleep(4)
    nodes = snap("panel-open")
    log(f"  panel visible: {bool(find(nodes, 'lighthouse|bread|atom'))}; top={top_activity()}")

    # 4. Show only the local feed, then open its article and read it.
    choose_source(r"^folio test gazette")
    if tap_text(r"lighthouse keepers", "panel-tap-article", wait=6):
        nodes = snap("article")
        for pattern in (r"a life measured in hours", r"you never really sleep",
                        r"wind the clockwork", r"the north light at dusk"):
            log(f"  article shows /{pattern}/: {bool(find(nodes, pattern))}")
        log(f"  boilerplate leaked: {bool(find(nodes, 'subscribe now|section 3|copyright'))}")
        sh(f"input swipe {w // 2} {h * 3 // 4} {w // 2} {h // 5} 500")
        time.sleep(2)
        nodes = snap("article-scrolled")
        log(f"  boilerplate leaked: {bool(find(nodes, 'subscribe now|section 3|copyright'))}")
        nodes = back_gesture("panel-after-back")  # back to the list
        log(f"  back gesture closes the article: {not find(nodes, 'a life measured in hours')}")
        log(f"  ... and the feed list is still showing: {bool(find(nodes, 'lighthouse keepers'))}")

    # 5. A real article from the web.
    choose_source(r"^ars technica")
    nodes = snap("panel-before-real")
    real = next((n for n in nodes if len(n["text"]) > 25 and n["bounds"][1] > 500
                 and "·" not in n["text"]), None)
    if real:
        log(f"  opening real article {real['text']!r}")
        tap(real)
        time.sleep(12)
        snap("real-article")
        sh(f"input swipe {w // 2} {h * 3 // 4} {w // 2} {h // 5} 500")
        time.sleep(2)
        snap("real-article-scrolled")
        sh("input keyevent KEYCODE_BACK")
        time.sleep(2)

    # 5b. "Back to top": hidden while scrolling down, shown when scrolling back up.
    top_before = snap("list-top")
    first = next((n for n in top_before if len(n["text"]) > 25 and n["bounds"][1] > 400), None)
    for i in range(3):
        sh(f"input swipe {w // 2} {h * 4 // 5} {w // 2} {h // 5} 300")
        time.sleep(1.5)
    nodes = snap("list-scrolled-down")
    log(f"  no top button while scrolling down: {not find(nodes, '^back to top$')}")
    sh(f"input swipe {w // 2} {h // 2} {w // 2} {h * 3 // 5} 300")
    time.sleep(1.5)
    nodes = snap("list-scrolled-up")
    button = find(nodes, '^back to top$')
    log(f"  top button after scrolling up a little: {bool(button)}")
    if button:
        tap(button)
        time.sleep(3)
        nodes = snap("list-after-top")
        log(f"  back at the top: {bool(first) and bool(find(nodes, '^' + re.escape(first['text']) + '$'))}"
            f"; button hidden again: {not find(nodes, '^back to top$')}")

    # 5c. Back from an article returns to the same place in the list, not the top.
    for i in range(2):
        sh(f"input swipe {w // 2} {h * 4 // 5} {w // 2} {h // 5} 300")
        time.sleep(1.5)
    nodes = snap("list-down-before-article")
    pick = next((n for n in nodes if len(n["text"]) > 25 and h // 3 < n["bounds"][1] < h * 2 // 3
                 and "·" not in n["text"]), None)
    if pick:
        tap(pick)
        time.sleep(6)
        snap("mid-list-article")
        nodes = back_gesture("list-after-article")
        log(f"  list kept its place after an article: {bool(find(nodes, '^' + re.escape(pick['text']) + '$'))}"
            f" ({pick['text']!r}); not at the top: {not (first and find(nodes, '^' + re.escape(first['text']) + '$'))}")
    else:
        log("  !! nothing to open mid-list")

    # 5d. Unread / Starred / Large images live in the ⋮ menu now.
    sh(f"input swipe {w // 2} {h // 4} {w // 2} {h * 4 // 5} 300")  # back near the top
    time.sleep(1.5)
    nodes = snap("list-compact")
    log(f"  no Unread/Starred chips on the list: "
        f"{not find(nodes, '^unread$') and not find(nodes, '^starred$')}")
    if tap_text(r"^more options$", "menu", wait=1.5):
        nodes = snap("menu-open")
        log(f"  menu has Unread, Starred and Large images: "
            f"{bool(find(nodes, '^unread$')) and bool(find(nodes, '^starred$')) and bool(find(nodes, '^large images$'))}")
        tap_text(r"^large images$", "large-on", wait=1)
        sh("input keyevent KEYCODE_BACK")  # close the menu
        time.sleep(3)
        nodes = snap("list-large")
        titles = [n for n in nodes if len(n["text"]) > 25 and "·" not in n["text"]]
        # Large cards: the first title sits well below the top of its card (picture above it).
        log(f"  large-image layout: {bool(titles)} (first title at y={titles[0]['bounds'][1] if titles else '?'})")

    # 6. Feed-only item (no link) shows the feed's own text.
    choose_source(r"^folio test gazette")
    if tap_text(r"full text in the feed", "panel-feed-only", wait=4):
        nodes = snap("feed-only-article")
        log(f"  feed-only text shown: {bool(find(nodes, 'second point'))}")
        sh("input keyevent KEYCODE_BACK")
        time.sleep(2)

    # 7. A back gesture on the list closes the panel; open it again.
    nodes = back_gesture("panel-back-gesture")
    log(f"  back gesture closes the panel: {not find(nodes, 'lighthouse|full text in the feed')}")
    sh(f"input swipe {w // 8} {h // 2} {w * 7 // 8} {h // 2} 350")
    time.sleep(4)

    # 8. Swipe the panel away to the left.
    sh(f"input swipe {w * 7 // 8} {h // 2} {w // 8} {h // 2} 300")
    time.sleep(3)
    nodes = snap("panel-closed")
    log(f"  panel closed: {not find(nodes, 'lighthouse')}")
    save_logs()


if __name__ == "__main__":
    main()

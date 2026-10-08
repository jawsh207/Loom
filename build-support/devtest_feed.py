#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test of the feed panel: turn it on in Settings › Feeds, add feeds (a local RSS feed, a
local site found through autodiscovery, and a real one), open the panel with a swipe, read an
article, and swipe the panel away. Screenshots and logs go to the output directory.

usage: devtest_feed.py OUT_DIR FOLIO_APK
The local test site (build-support/devtest-feed) must be served on the host at port 8000,
which the emulator reaches as 10.0.2.2."""

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
    sh(f"input swipe {w // 8} {h // 2} {w * 7 // 8} {h // 2} 350")
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
        sh("input keyevent KEYCODE_BACK")  # back to the list
        time.sleep(2)
        snap("panel-after-back")

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

    # 6. Feed-only item (no link) shows the feed's own text.
    choose_source(r"^folio test gazette")
    if tap_text(r"full text in the feed", "panel-feed-only", wait=4):
        nodes = snap("feed-only-article")
        log(f"  feed-only text shown: {bool(find(nodes, 'second point'))}")
        sh("input keyevent KEYCODE_BACK")
        time.sleep(2)

    # 7. Swipe the panel away to the left.
    sh(f"input swipe {w * 7 // 8} {h // 2} {w // 8} {h // 2} 300")
    time.sleep(3)
    nodes = snap("panel-closed")
    log(f"  panel closed: {not find(nodes, 'lighthouse')}")
    save_logs()


if __name__ == "__main__":
    main()

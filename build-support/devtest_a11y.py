#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator check that TalkBack can name everything you can tap on Loom's own screens.

Opens the home screen, an app's long-press menu, the app drawer with its tabs, the feed panel
and an article, Home settings and each of Loom's settings screens (Grid, Drawer tabs, Manage
feeds and its Style screen, Backup & restore), and the lock popup. On each it lists the
controls that can be tapped or toggled but have no text or description, on themselves or
inside them, so a screen reader would only say "button".

usage: devtest_a11y.py OUT_DIR LOOM_APK"""

import glob
import time
import xml.etree.ElementTree as ET

from devtest import OUT, PKG, find, log, save_logs, screen_size, setup_home, sh, snap, tap_text
from devtest import open_home_settings as open_settings
from devtest_grid import center, close_popup, go_home, long_press, open_drawer, tap_scrolled

PROBLEMS = []


def has_label(e):
    if (e.get("text") or "").strip() or (e.get("content-desc") or "").strip():
        return True
    return any(has_label(c) for c in e)


def audit(name):
    """Snaps the screen and logs Loom's unlabelled controls on it."""
    snap(name)
    xml = sorted(glob.glob(str(OUT / f"*-{name}.xml")))
    if not xml:
        log(f"  {name}: no UI dump")
        return
    root = ET.parse(xml[-1]).getroot()
    bad = []
    for e in root.iter("node"):
        if e.get("package") != PKG:
            continue
        # What TalkBack stops on: things you can tap or focus. A switch inside a settings row
        # isn't one (the row is, and is read with the switch's state).
        actionable = e.get("clickable") == "true" or e.get("long-clickable") == "true" or (
            e.get("checkable") == "true" and e.get("focusable") == "true")
        x1, y1, x2, y2 = map(int, e.get("bounds", "[0,0][0,0]").replace("][", ",")
                             .strip("[]").split(","))
        if y2 - y1 < 60:  # cut off at the screen's edge; checked once scrolled into view
            continue
        if actionable and not has_label(e):
            bad.append(f"{e.get('class', '').split('.')[-1]} {e.get('resource-id', '')} "
                       f"{e.get('bounds')}")
    log(f"  {name}: {'all controls named' if not bad else f'{len(bad)} unnamed: ' + '; '.join(bad[:6])}")
    PROBLEMS.extend(f"{name}: {b}" for b in bad)


def main():
    setup_home()
    w, h = screen_size()
    go_home()
    audit("home")
    nodes = snap("home-icons")
    app = next((n for n in nodes if n["desc"] and n["bounds"][1] > h * 0.8), None)
    if app:
        long_press(*center(app))
        audit("app-menu")
        close_popup()
    sh(f"input swipe {w // 2} {h // 3} {w // 2} {h // 3} 1500")  # empty spot: home menu
    time.sleep(2)
    audit("home-menu")
    close_popup()

    open_drawer("drawer", r"search apps")
    audit("drawer")
    go_home()

    # The feed panel: on, with the local test feed.
    from devtest_feed import LOCAL_RSS, add_feed
    open_settings()
    if tap_scrolled(r"^feeds$", "feeds", wait=4):
        audit("manage-feeds")
        tap_text(r"^feed panel$", "feed-on", wait=2)
        add_feed(LOCAL_RSS, "rss")
        if tap_scrolled(r"^style$", "style", wait=3):
            audit("feed-style")
            sh("input keyevent KEYCODE_BACK")
            time.sleep(1)
    go_home()
    sh(f"input swipe {w // 8} {h // 2} {w * 7 // 8} {h // 2} 350")
    time.sleep(5)
    audit("feed-panel")
    if tap_text(r"lighthouse keepers", "article", wait=6):
        audit("article")
        sh("input keyevent KEYCODE_BACK")
        time.sleep(2)
    go_home()

    open_settings()
    audit("home-settings")
    for label, name in ((r"^grid$", "grid"), (r"^drawer tabs$", "drawer-tabs"),
                        (r"^backup & restore$", "backup")):
        open_settings()
        if tap_scrolled(label, name, wait=3, up=name == "grid"):
            audit(f"{name}-screen")
        sh("input keyevent KEYCODE_BACK")
        time.sleep(1)
    go_home()

    # The lock popup.
    open_settings()
    tap_scrolled(r"^lock home screen$", "lock-on")
    go_home()
    long_press(w // 2, h // 3)
    audit("lock-popup")
    close_popup()
    open_settings()
    tap_scrolled(r"^lock home screen$", "lock-off")
    go_home()

    log(f"  RESULT: {'every control is named' if not PROBLEMS else f'{len(PROBLEMS)} unnamed controls'}")
    save_logs()


if __name__ == "__main__":
    main()

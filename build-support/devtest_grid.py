#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test of the home screen lock and grid changes.

1. Lock the home screen: long-press shows the icon menu but nothing moves; apps dragged from
   the drawer aren't added; the Grid screen's home screen controls are disabled.
2. Unlock, change the home screen grid: a warning appears; confirming clears the home screen.
3. Change only the drawer columns: no warning, nothing cleared.

usage: devtest_grid.py OUT_DIR FOLIO_APK"""

import time

from devtest import (PKG, find, log, save_logs, screen_size, setup_home, sh, snap, tap,
                     tap_text)


def home_icons(nodes):
    return sorted(n["text"] for n in nodes if n["text"] and n["click"] == "true"
                  and n["bounds"][1] > 150)


def open_settings():
    sh(f"am start -a android.intent.action.APPLICATION_PREFERENCES -p {PKG}")
    time.sleep(4)


def go_home():
    sh("input keyevent KEYCODE_HOME")
    time.sleep(3)


def scroll_down():
    w, h = screen_size()
    sh(f"input swipe {w // 2} {h * 2 // 3} {w // 2} {h // 4} 400")
    time.sleep(1.5)


def press(desc, times, name):
    for i in range(times):
        n = find(snap(f"{name}-{i}"), desc)
        if not n:
            log(f"  !! no /{desc}/")
            return
        tap(n)
        time.sleep(0.6)


def center(n):
    x1, y1, x2, y2 = n["bounds"]
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    setup_home()
    w, h = screen_size()
    before = home_icons(snap("home-before"))
    log(f"  home icons before: {before}")

    # --- 1. Lock ---
    open_settings()
    tap_text(r"^lock home screen$", "lock-on", wait=2)
    go_home()
    nodes = snap("locked-home")
    gallery = find(nodes, r"^gallery$")
    if gallery:
        x, y = center(gallery)
        sh(f"input swipe {x} {y} {x} {y} 1200")  # long press
        time.sleep(2)
        menu = snap("locked-long-press")
        log(f"  locked long-press shows menu: {bool(find(menu, 'app info'))}")
        sh("input keyevent KEYCODE_BACK")
        time.sleep(1)
        sh(f"input draganddrop {x} {y} {w // 2} {h // 3} 2500")
        time.sleep(3)
        after_drag = find(snap("locked-after-drag"), r"^gallery$")
        log(f"  icon stayed put: {bool(after_drag) and after_drag['bounds'] == gallery['bounds']}")
    # Drag an app from the drawer onto the home screen.
    sh(f"input swipe {w // 2} {h * 4 // 5} {w // 2} {h // 5} 300")
    time.sleep(3)
    clock = find(snap("locked-drawer"), r"^clock$")
    if clock:
        x, y = center(clock)
        sh(f"input draganddrop {x} {y} {w // 2} {h // 2} 3000")
        time.sleep(4)
    go_home()
    locked_after = home_icons(snap("locked-home-after"))
    log(f"  nothing added while locked: {locked_after == before}")
    open_settings()
    tap_text(r"^grid$", "locked-grid", wait=3)
    log(f"  grid says locked: {bool(find(snap('locked-grid-screen'), 'home screen is locked'))}")
    sh("input keyevent KEYCODE_BACK")
    time.sleep(2)
    tap_text(r"^lock home screen$", "lock-off", wait=2)

    # --- 2. Grid change clears the home screen ---
    tap_text(r"^grid$", "settings-grid", wait=4)
    tap_text(r"choose home screen size", "custom-home", wait=1)
    press(r"^more columns$", 1, "cols")
    tap_text(r"^apply$", "apply", wait=2)
    nodes = snap("warning")
    log(f"  warning shown: {bool(find(nodes, 'clear your home screen'))}")
    tap_text(r"clear and change grid", "confirm", wait=10)
    go_home()
    cleared = home_icons(snap("home-cleared"))
    log(f"  home screen cleared: {cleared == []} ({cleared})")
    sh(f"input swipe {w // 2} {h * 4 // 5} {w // 2} {h // 5} 300")
    time.sleep(3)
    log(f"  apps still in drawer: {bool(find(snap('drawer-after-clear'), '^gallery$'))}")
    go_home()

    # --- 3. Drawer-only change: no warning ---
    open_settings()
    tap_text(r"^grid$", "settings-grid-2", wait=4)
    scroll_down()
    scroll_down()
    tap_text(r"choose app drawer columns", "custom-drawer", wait=1)
    press(r"^more drawer$", 1, "drawer")
    tap_text(r"^apply$", "apply-drawer", wait=6)
    nodes = snap("after-drawer-apply")
    log(f"  no warning for drawer change: {not find(nodes, 'clear your home screen')}")
    save_logs()


if __name__ == "__main__":
    main()

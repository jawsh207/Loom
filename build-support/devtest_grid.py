#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test of the grid settings: change the home screen to the largest grid (8 x 10, 8-icon
dock) and a 7-column drawer, check the icons survived the move, open the drawer, then go back to the
defaults.  usage: devtest_grid.py OUT_DIR FOLIO_APK"""

import time

from devtest import (PKG, find, log, save_logs, screen_size, setup_home, sh, snap, tap,
                     tap_text)


def home_icons(nodes):
    return sorted(n["text"] for n in nodes if n["text"] and n["click"] == "true"
                  and n["bounds"][1] > 150)


def open_grid_settings():
    sh(f"am start -a android.intent.action.APPLICATION_PREFERENCES -p {PKG}")
    time.sleep(4)
    return tap_text(r"^grid$", "settings-grid", wait=4)


def scroll_down():
    w, h = screen_size()
    sh(f"input swipe {w // 2} {h * 2 // 3} {w // 2} {h // 4} 400")
    time.sleep(1.5)


def press(desc, times, name):
    for i in range(times):
        nodes = snap(f"{name}-{i}")
        n = find(nodes, desc)
        if not n:
            log(f"  !! no /{desc}/")
            return
        tap(n)
        time.sleep(0.6)


def open_drawer(name):
    w, h = screen_size()
    sh(f"input swipe {w // 2} {h * 4 // 5} {w // 2} {h // 5} 300")
    time.sleep(3)
    snap(name)
    sh("input keyevent KEYCODE_HOME")
    time.sleep(2)


def main():
    setup_home()
    before = home_icons(snap("home-before"))
    log(f"  home icons before: {before}")
    open_drawer("drawer-before")

    if not open_grid_settings():
        save_logs()
        return
    snap("grid-screen")
    tap_text(r"choose home screen size", "custom-home", wait=1)
    press(r"^more columns$", 4, "cols")   # the most: 8
    press(r"^more rows$", 5, "rows")      # the most: 10
    scroll_down()
    scroll_down()
    tap_text(r"choose app drawer columns", "custom-drawer", wait=1)
    press(r"^more drawer$", 3, "drawer")
    snap("grid-chosen")
    tap_text(r"^apply$", "apply", wait=8)

    after_nodes = snap("home-after")
    after = home_icons(after_nodes)
    log(f"  home icons after: {after}")
    log(f"  icons kept: {set(before) <= set(after)}")
    open_drawer("drawer-after")

    # Back to the defaults.
    if open_grid_settings():
        scroll_down()
        scroll_down()
        tap_text(r"back to the defaults", "reset", wait=1)
        tap_text(r"^apply$", "apply-reset", wait=8)
        restored = home_icons(snap("home-restored"))
        log(f"  home icons restored: {restored}")
        log(f"  icons kept after reset: {set(before) <= set(restored)}")
        open_drawer("drawer-restored")
    save_logs()


if __name__ == "__main__":
    main()

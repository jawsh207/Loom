#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test of per-app features in the drawer and on the home screen.

1. Drawer tabs keep an app whose update renames its launcher activity ("Test Bank", built by
   the workflow in two versions: .MainOld, then .MainNew).
2. Rename an app from the drawer and from the home screen; reset it.
3. Hide an app; it's gone from the drawer and normal search, listed when searching "hidden",
   and Unhide brings it back.
4. Locked home screen: an icon's long press shows its menu; an empty spot offers to unlock.

usage: devtest_apps.py OUT_DIR LOOM_APK TESTBANK_V1_APK TESTBANK_V2_APK"""

import sys
import time

from devtest import adb, find, log, save_logs, screen_size, setup_home, sh, snap, tap, tap_text
from devtest_grid import (center, close_popup, go_home, home_icons, long_press, open_drawer,
                          open_settings, tap_scrolled)

BANK_V1, BANK_V2 = sys.argv[3], sys.argv[4]


def type_text(text):
    sh("input text '" + text.replace(" ", "%s") + "'")
    time.sleep(1)


def clear_field():
    sh("input keyevent KEYCODE_MOVE_END")
    for _ in range(30):
        sh("input keyevent KEYCODE_DEL")


def drawer_has(nodes, name):
    return bool(find(nodes, "^" + name + "$"))


def menu_for(name, where, nodes=None):
    """Long-presses the icon called [name]; returns the menu's nodes (or None)."""
    nodes = nodes or snap(f"{where}-before-menu")
    icon = find(nodes, "^" + name + "$")
    if not icon:
        log(f"  !! no {name!r} to long-press ({where})")
        return None
    x, y = center(icon)
    long_press(x, y)
    return snap(f"{where}-menu")


def search(text, name):
    w, h = screen_size()
    nodes = snap(f"{name}-before-search")
    bar = find(nodes, "search apps")
    if bar:
        tap(bar)
        time.sleep(1.5)
    clear_field()
    type_text(text)
    time.sleep(2.5)
    sh("input keyevent KEYCODE_BACK")  # hide the keyboard so results are visible
    time.sleep(1)
    return snap(name)


def main():
    setup_home()
    w, h = screen_size()

    # --- 1. Tabs survive an update that renames the launcher activity ---
    log("  installing Test Bank v1 (launcher activity .MainOld)")
    log("    " + adb("install", "-r", BANK_V1).strip()[-120:])
    time.sleep(5)
    nodes = open_drawer("drawer-bank", r"^test bank$")
    log(f"  Test Bank in the drawer: {bool(nodes)}")
    menu = menu_for("Test Bank", "bank", nodes) if nodes else None
    if menu and find(menu, "^add to tab$"):
        tap(find(menu, "^add to tab$"))
        time.sleep(2)
        type_text("Bank")
        tap_text(r"^ok$", "new-tab-ok", wait=2)
    nodes = snap("drawer-after-tab")
    if tap_text(r"^bank$", "tab-bank", wait=2):
        log(f"  in the Bank tab before the update: {drawer_has(snap('bank-tab-v1'), 'Test Bank')}")
    log("  updating Test Bank to v2 (launcher activity renamed to .MainNew)")
    log("    " + adb("install", "-r", BANK_V2).strip()[-120:])
    log("    " + sh("cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "
                   "com.loom.testbank").strip().replace("\n", " "))
    time.sleep(8)
    go_home()
    nodes = open_drawer("drawer-after-update", r"^(apps|bank)$")
    tap_text(r"^bank$", "tab-bank-2", wait=2)
    after = snap("bank-tab-v2")
    log(f"  still in the Bank tab after the update: {drawer_has(after, 'Test Bank')}")
    tap_text(r"^apps$", "tab-apps", wait=2)
    log(f"  not back in the main Apps tab: {not drawer_has(snap('apps-tab-v2'), 'Test Bank')}")

    # --- 2. Rename ---
    go_home()
    nodes = open_drawer("drawer-rename")
    menu = menu_for("Clock", "rename-clock", nodes)
    if menu and find(menu, "^rename$"):
        tap(find(menu, "^rename$"))
        time.sleep(2)
        clear_field()
        type_text("Alarms")
        tap_text(r"^ok$", "rename-ok", wait=4)
        nodes = snap("drawer-renamed")
        log(f"  renamed in the drawer: {drawer_has(nodes, 'Alarms') and not drawer_has(nodes, 'Clock')}")
        nodes = search("alarms", "search-renamed")
        log(f"  search finds the new name: {drawer_has(nodes, 'Alarms')}")
    else:
        log("  !! no Rename in the drawer menu")
    go_home()
    menu = menu_for("Gallery", "rename-home", snap("home-rename"))
    if menu and find(menu, "^rename$"):
        tap(find(menu, "^rename$"))
        time.sleep(2)
        clear_field()
        type_text("Photos")
        tap_text(r"^ok$", "rename-home-ok", wait=4)
        go_home()
        nodes = snap("home-renamed")
        log(f"  renamed on the home screen: {'Photos' in home_icons(nodes)} ({home_icons(nodes)})")
        menu = menu_for("Photos", "reset-home", nodes)
        if menu and find(menu, "^rename$"):
            tap(find(menu, "^rename$"))
            time.sleep(2)
            tap_text(r"^original name$", "rename-reset", wait=4)
            go_home()
            log(f"  original name back: {'Gallery' in home_icons(snap('home-reset-name'))}")
    else:
        log("  !! no Rename on the home screen menu")

    # --- 3. Hide ---
    go_home()
    nodes = open_drawer("drawer-hide", r"^calendar$")
    menu = menu_for("Calendar", "hide", nodes)
    if menu and find(menu, "^hide$"):
        tap(find(menu, "^hide$"))
        time.sleep(3)
        nodes = snap("drawer-hidden")
        log(f"  hidden from the drawer: {not drawer_has(nodes, 'Calendar')}")
        nodes = search("calendar", "search-hidden-app")
        log(f"  not in normal search: {not drawer_has(nodes, 'Calendar')}")
        nodes = search("hidden", "search-hidden")
        log(f"  listed when searching 'hidden': {drawer_has(nodes, 'Calendar')}")
        menu = menu_for("Calendar", "unhide", nodes)
        if menu and find(menu, "^unhide$"):
            log("  long-press offers Unhide: True")
            tap(find(menu, "^unhide$"))
            time.sleep(3)
            go_home()
            nodes = open_drawer("drawer-unhidden", r"^calendar$")
            log(f"  back in the drawer after Unhide: {bool(nodes)}")
        else:
            log("  long-press offers Unhide: False")
    else:
        log("  !! no Hide in the drawer menu")

    # --- 4. Locked home screen ---
    go_home()
    open_settings()
    tap_scrolled(r"^lock home screen$", "lock-on")
    go_home()
    menu = menu_for("Gallery", "locked-icon", snap("locked-home"))
    if menu:
        log(f"  locked: icon menu shown: {bool(find(menu, 'app info'))}; "
            f"no Remove: {not find(menu, '^remove$')}; "
            f"no unlock popup: {not find(menu, 'home screen locked')}")
        close_popup()
    long_press(w // 2, h // 3)
    log(f"  locked: empty spot offers to unlock: "
        f"{bool(find(snap('locked-empty'), 'home screen locked'))}")
    close_popup()
    open_settings()
    tap_scrolled(r"^lock home screen$", "lock-off")
    save_logs()


if __name__ == "__main__":
    main()

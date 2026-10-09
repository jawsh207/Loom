#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test of the home screen lock and grid changes.

1. Lock the home screen: a long press shows the unlock popup and nothing moves; holding the
   popup's button unlocks it, and it locks again after 45 seconds; apps dragged from the
   drawer aren't added; the Grid screen's home screen controls are disabled.
2. Unlock, change the home screen grid: a warning appears; confirming resets the home screen
   to the stock layout.
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


def scroll_to(pattern, name, tries=5, up=False):
    w, h = screen_size()
    for i in range(tries):
        n = find(snap(f"{name}-find{i}"), pattern)
        # Only a row that's well clear of the toolbar and the navigation bar counts.
        if n and n["bounds"][1] > h // 8 and n["bounds"][3] < h - h // 8:
            return n
        a, b = (h // 3, h * 3 // 4) if up else (h * 3 // 4, h // 3)
        sh(f"input swipe {w // 2} {a} {w // 2} {b} 400")
        time.sleep(1.5)
    return None


def tap_scrolled(pattern, name, wait=2, up=False):
    n = scroll_to(pattern, name, up=up)
    if not n:
        log(f"  !! no /{pattern}/")
        return False
    tap(n)
    time.sleep(wait)
    return True


def press(desc, times, name):
    for i in range(times):
        n = find(snap(f"{name}-{i}"), desc)
        if not n:
            log(f"  !! no /{desc}/")
            return
        tap(n)
        time.sleep(0.6)


def long_press(x, y, ms=1200):
    sh(f"input swipe {x} {y} {x} {y} {ms}")
    time.sleep(2)


def tap_xy(x, y):
    sh(f"input tap {x} {y}")
    time.sleep(1)


def close_popup():
    sh("input keyevent KEYCODE_BACK")
    time.sleep(1)


def open_drawer(name, marker=r"^clock$", tries=3):
    """Swipes up to the app drawer, retrying while the home screen is still settling."""
    w, h = screen_size()
    for attempt in range(tries):
        sh(f"input swipe {w // 2} {h * 4 // 5} {w // 2} {h // 5} 300")
        time.sleep(3)
        nodes = snap(f"{name}-{attempt}")
        if find(nodes, marker):
            return nodes
        go_home()
        time.sleep(2)
    return None


def center(n):
    x1, y1, x2, y2 = n["bounds"]
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    setup_home()
    w, h = screen_size()
    before = home_icons(snap("home-before"))
    log(f"  home icons before: {before}")

    # --- 0. Settings layout ---
    open_settings()
    nodes = snap("settings-top")
    log(f"  no Feed panel switch on settings: {not find(nodes, '^feed panel$')}")
    log(f"  Feeds entry present: {bool(find(nodes, '^feeds$'))}")
    log(f"  icon pack not on main screen: {not find(nodes, '^icon pack$')}")
    if tap_text(r"^icons$", "settings-icons", wait=3):
        nodes = snap("icons-screen")
        log(f"  Icons screen has Icon pack and Style other icons: "
            f"{bool(find(nodes, '^icon pack$')) and bool(find(nodes, '^style other icons$'))}")
        sh("input keyevent KEYCODE_BACK")
        time.sleep(2)
    log(f"  gesture switches not on main screen: "
        f"{not find(nodes, '^double-tap to lock$') and not find(nodes, '^swipe down for notifications$')}")
    if tap_scrolled(r"^gestures$", "settings-gestures", wait=3):
        nodes = snap("gestures-screen")
        log(f"  Gestures screen has both switches: "
            f"{bool(find(nodes, '^double-tap to lock$')) and bool(find(nodes, '^swipe down for notifications$'))}")
        sh("input keyevent KEYCODE_BACK")
        time.sleep(2)
    lock = scroll_to(r"^lock home screen$", "settings-bottom")
    titles = [n for n in snap("settings-bottom-order") if n["text"] and n["bounds"][1] > 200]
    below = [n["text"] for n in titles if lock and n["bounds"][1] > lock["bounds"][3]
             and not n["text"].startswith(("Shortcuts, folders", "Apps still"))]
    log(f"  Lock home screen is last: {bool(lock)} {below}")

    # --- 0b. Long-press menus ---
    go_home()
    long_press(w // 2, h // 3)
    menu = snap("home-menu")
    log(f"  home menu shown: {bool(find(menu, 'wallpaper'))}")
    log(f"  home menu has no Apps list: {not find(menu, '^apps list$')}")
    close_popup()
    drawer = open_drawer("drawer-for-menu") or []
    log(f"  app is named Loom in the drawer: {bool(find(drawer, '^loom$'))}"
        f" (no Folio: {not find(drawer, '^folio$')})")
    clock = find(drawer, r"^clock$")
    if clock:
        cx, cy = center(clock)
        long_press(cx, cy)
        menu = snap("drawer-menu")
        log(f"  drawer menu shown: {bool(find(menu, 'app info'))}")
        log(f"  drawer menu has no Add to home screen: {not find(menu, 'add to home screen')}")
        close_popup()
    go_home()
    open_settings()

    # --- 1. Lock ---
    tap_scrolled(r"^lock home screen$", "lock-on")
    go_home()
    nodes = snap("locked-home")
    gallery = find(nodes, r"^gallery$")
    if gallery:
        x, y = center(gallery)
        long_press(x, y)
        popup = snap("locked-long-press")
        log(f"  long-press shows unlock popup: {bool(find(popup, 'home screen locked'))}")
        log(f"  popup has hold button: {bool(find(popup, '^hold to unlock$'))}")
        tap_xy(w // 2, h * 4 // 5)  # outside the card (above the dock) closes it
        log(f"  tap outside closes popup: {not find(snap('popup-closed'), 'home screen locked')}")
        sh(f"input draganddrop {x} {y} {w // 2} {h // 3} 2500")
        time.sleep(3)
        close_popup()
        after_drag = find(snap("locked-after-drag"), r"^gallery$")
        log(f"  icon stayed put: {bool(after_drag) and after_drag['bounds'] == gallery['bounds']}")

        # Empty space.
        long_press(w // 2, h // 3)
        log(f"  empty-space long-press shows popup: "
            f"{bool(find(snap('locked-empty-press'), 'home screen locked'))}")
        close_popup()

        # A quick tap on the button doesn't unlock; holding it does.
        long_press(x, y)
        button = find(snap("popup-again"), r"^hold to unlock$")
        if button:
            bx, by = center(button)
            tap_xy(bx, by)
            time.sleep(1)
            log(f"  short tap keeps it locked: "
                f"{bool(find(snap('after-short-tap'), 'home screen locked'))}")
            long_press(bx, by, 1800)
            log(f"  hold closes popup: {not find(snap('after-hold'), 'home screen locked')}")
            unlocked_at = time.time()
            sh(f"input draganddrop {x} {y} {w // 2} {h // 3} 2500")
            time.sleep(3)
            moved = find(snap("unlocked-after-drag"), r"^gallery$")
            log(f"  icon moves while unlocked: {bool(moved) and moved['bounds'] != gallery['bounds']}")
            if moved:
                x, y = center(moved)
            time.sleep(max(0, unlocked_at + 48 - time.time()))
            snap("relocked-toast")
            long_press(x, y)
            log(f"  locked again after 45 s: "
                f"{bool(find(snap('relocked-press'), 'home screen locked'))}")
            close_popup()
            before = home_icons(snap("home-before-drawer"))
        else:
            log("  !! no hold button")
    # Drag an app from the drawer onto the home screen.
    clock = find(open_drawer("locked-drawer") or [], r"^clock$")
    if clock:
        x, y = center(clock)
        sh(f"input draganddrop {x} {y} {w // 2} {h // 2} 3000")
        time.sleep(4)
    go_home()
    locked_after = home_icons(snap("locked-home-after"))
    log(f"  nothing added while locked: {locked_after == before}")
    open_settings()
    tap_scrolled(r"^grid$", "locked-grid", wait=3, up=True)
    log(f"  grid says locked: {bool(find(snap('locked-grid-screen'), 'home screen is locked'))}")
    sh("input keyevent KEYCODE_BACK")
    time.sleep(2)
    tap_scrolled(r"^lock home screen$", "lock-off")

    # --- 2. Grid change resets the home screen to the stock layout ---
    tap_scrolled(r"^grid$", "settings-grid", wait=4, up=True)
    tap_text(r"choose home screen size", "custom-home", wait=1)
    press(r"^more columns$", 1, "cols")
    tap_text(r"^apply$", "apply", wait=2)
    nodes = snap("warning")
    log(f"  warning shown: {bool(find(nodes, 'reset your home screen'))}")
    tap_text(r"reset and change grid", "confirm", wait=10)
    go_home()
    time.sleep(3)
    nodes = snap("home-reset")
    reset = home_icons(nodes)
    log(f"  home icons after reset: {reset}")
    log(f"  stock layout loaded: {set(['Phone', 'Messaging', 'Camera', 'Gallery']) <= set(reset)}")
    gallery = find(nodes, r"^gallery$")
    # Moved to the upper part while unlocked; the stock layout has it in the last row.
    log(f"  gallery back in its stock spot: {bool(gallery) and gallery['bounds'][1] > h * 0.6}")
    # The home screen may still be reloading after the reset.
    log(f"  apps still in drawer: {bool(open_drawer('drawer-after-clear', '^gallery$'))}")
    go_home()

    # --- 3. Drawer-only change: no warning ---
    open_settings()
    tap_scrolled(r"^grid$", "settings-grid-2", wait=4, up=True)
    scroll_down()
    scroll_down()
    tap_text(r"choose app drawer columns", "custom-drawer", wait=1)
    press(r"^more drawer$", 1, "drawer")
    tap_text(r"^apply$", "apply-drawer", wait=6)
    nodes = snap("after-drawer-apply")
    log(f"  no warning for drawer change: {not find(nodes, 'reset your home screen')}")
    save_logs()


if __name__ == "__main__":
    main()

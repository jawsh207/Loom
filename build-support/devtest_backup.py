#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test of Home settings › Allow home screen rotation and › Backup & restore.

Rotation: with auto-rotate on and the emulator turned on its side, the home screen stays
upright with the setting off and turns with it on.

Backup & restore: makes a folder in the dock, turns the rotation setting on, backs up to a
file, wipes Loom's data (as after reinstalling it), restores the file, and checks that the
folder and the setting came back.

usage: devtest_backup.py OUT_DIR LOOM_APK"""

import time

from devtest import PKG, adb, find, log, save_logs, screen_size, setup_home, sh, snap, tap, \
    tap_text
from devtest_grid import center, go_home, open_settings, tap_scrolled


def landscape():
    out = sh("dumpsys window displays")
    for token in ("mCurrentRotation=ROTATION_90", "mCurrentRotation=ROTATION_270",
                  "mCurrentRotation=1", "mCurrentRotation=3"):
        if token in out:
            return True
    return False


def rotation_switch_checked():
    open_settings()
    n = None
    nodes = snap("settings-rotation")
    w, h = screen_size()
    for _ in range(4):
        n = find(nodes, r"^allow home screen rotation$")
        if n:
            break
        sh(f"input swipe {w // 2} {h * 2 // 3} {w // 2} {h // 4} 400")
        time.sleep(1.5)
        nodes = snap("settings-rotation-scroll")
    if not n:
        return None
    # The switch is on the same row.
    row = [m for m in nodes if m["checkable"] and abs(center(m)[1] - center(n)[1]) < 80]
    return row[0]["checked"] if row else None


def set_rotation(on):
    open_settings()
    tap_scrolled(r"^allow home screen rotation$", f"rotation-{'on' if on else 'off'}", wait=2)
    go_home()


def dock_folder(nodes, names, h):
    return next((n for n in nodes if n["bounds"][1] > h * 0.8 and n["desc"]
                 and n["desc"] not in names), None)


def pick_in_files(pattern, name, wait=3):
    """Taps an item in the system file picker, looking in a couple of places."""
    for _ in range(3):
        nodes = snap(name)
        n = find(nodes, pattern)
        if n:
            tap(n)
            time.sleep(wait)
            return True
        # Open the side menu and try Downloads.
        menu = find(nodes, r"^show roots$|^navigate up$")
        if menu:
            tap(menu)
            time.sleep(2)
            tap_text(r"^downloads$", f"{name}-downloads", wait=2)
    log(f"  !! file picker: no /{pattern}/")
    return False


def main():
    setup_home()
    w, h = screen_size()

    # --- Rotation ---
    sh("settings put system accelerometer_rotation 1")
    go_home()
    adb("emu", "rotate")
    time.sleep(4)
    snap("rotated-off")
    log(f"  rotation off: home stays upright: {not landscape()}")
    for _ in range(3):  # back upright (each rotate turns 90 degrees)
        adb("emu", "rotate")
        time.sleep(1)
    time.sleep(4)
    set_rotation(True)
    adb("emu", "rotate")
    time.sleep(5)
    go_home()
    time.sleep(3)
    snap("rotated-on")
    log(f"  rotation on: home turns with the phone: {landscape()}")
    for _ in range(3):
        adb("emu", "rotate")
        time.sleep(1)
    time.sleep(5)
    go_home()
    w, h = screen_size()

    # --- A setup to back up: a folder in the dock ---
    nodes = snap("home")
    names = [n["desc"] for n in nodes if n["desc"] and n["bounds"][1] > h * 0.8]
    log(f"  dock before: {names}")
    phone = find(nodes, r"^phone$")
    messaging = find(nodes, r"^messag(es|ing)$")
    if phone and messaging:
        (x1, y1), (x2, y2) = center(messaging), center(phone)
        sh(f"input draganddrop {x1} {y1} {x2} {y2} 1500")
        time.sleep(3)
        go_home()
    nodes = snap("home-with-folder")
    folder = dock_folder(nodes, names, h)
    log(f"  folder made: {folder['desc'] if folder else None!r}")

    # --- Back up ---
    open_settings()
    tap_scrolled(r"^backup & restore$", "backup-screen", wait=3)
    tap_text(r"^back up$", "backup-tap", wait=4)
    nodes = snap("save-picker")
    save = find(nodes, r"^save$")
    if save:
        tap(save)
        time.sleep(5)
    else:
        log("  !! no Save button in the file picker")
    snap("after-save")
    files = sh("ls -l /sdcard/Download/ 2>/dev/null; ls -l /storage/emulated/0/Download/ 2>/dev/null")
    log(f"  backup file: {[l for l in files.splitlines() if 'Loom backup' in l][:1]}")
    go_home()

    # --- Wipe Loom (as after reinstalling it) ---
    sh(f"pm clear {PKG}")
    time.sleep(3)
    setup_home()
    nodes = snap("home-wiped")
    log(f"  after wiping: folder gone: {dock_folder(nodes, names, h) is None}; "
        f"rotation setting off: {rotation_switch_checked() is not True}")
    go_home()

    # --- Restore ---
    open_settings()
    tap_scrolled(r"^backup & restore$", "restore-screen", wait=3)
    tap_text(r"^restore$", "restore-tap", wait=4)
    pick_in_files(r"^loom backup", "open-picker", wait=3)
    nodes = snap("restore-confirm")
    confirm = [n for n in nodes if n["text"].lower() == "restore"]
    if confirm:
        tap(confirm[-1])  # the dialog's button (the screen row has the same word)
    else:
        log("  !! no Restore button in the dialog")
    time.sleep(12)
    go_home()
    time.sleep(4)
    nodes = snap("home-restored")
    folder = dock_folder(nodes, names, h)
    log(f"  restored: folder back: {folder is not None} ({folder['desc'] if folder else None!r})")
    log(f"  restored: rotation setting back on: {rotation_switch_checked() is True}")
    go_home()
    save_logs()


if __name__ == "__main__":
    main()

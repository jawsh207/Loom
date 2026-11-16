#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test of a work profile and Private Space with Loom.

Creates a work profile and a Private Space with shell commands (no device admin app needed),
then checks in the app drawer that:

- the work profile's apps appear (Launcher3 shows them under a Work tab), open, and have a
  long-press menu; Loom's own drawer tabs still work alongside;
- Private Space shows at the bottom of the drawer, can be unlocked to list its apps, and
  locked again;
- nothing crashes along the way.

usage: devtest_profiles.py OUT_DIR LOOM_APK"""

import re
import time

from devtest import find, log, save_logs, screen_size, setup_home, sh, snap, tap, tap_text
from devtest_grid import center, close_popup, go_home, long_press, open_drawer


def create(kind, args):
    out = sh(f"pm create-user {args}").strip()
    m = re.search(r"id (\d+)", out)
    log(f"  create {kind}: {out[:120]!r}")
    if not m:
        return None
    uid = int(m.group(1))
    log(f"  start {kind} (user {uid}): {sh(f'am start-user -w {uid}').strip()[:80]!r}")
    # A few apps the profile can show: the system ones it already has.
    for pkg in ("com.android.settings", "com.android.contacts", "com.android.calculator2",
                "com.google.android.calculator", "com.android.deskclock"):
        sh(f"pm install-existing --user {uid} {pkg}")
    return uid


def scroll_drawer(times=6):
    w, h = screen_size()
    for _ in range(times):
        sh(f"input swipe {w // 2} {h * 3 // 4} {w // 2} {h // 4} 300")
        time.sleep(0.8)


def main():
    setup_home()
    w, h = screen_size()
    work = create("work profile", '--profileOf 0 --managed "Work"')
    private = create("Private Space",
                     '--profileOf 0 --user-type android.os.usertype.profile.PRIVATE "Private"')
    time.sleep(8)
    log(f"  users: {sh('pm list users').strip()}")

    # --- Work profile ---
    nodes = open_drawer("drawer", r"search apps") or []
    work_tab = find(nodes, r"^work$|work apps")
    log(f"  Work tab shown: {bool(work_tab)}")
    if work_tab:
        tap(work_tab)
        time.sleep(3)
        nodes = snap("work-tab")
        apps = [n for n in nodes if n["id"].endswith("/icon") and n["desc"]
                and h * 0.2 < n["bounds"][1] < h * 0.9]
        log(f"  work apps listed: {[a['desc'] for a in apps][:8]}")
        if apps:
            target = next((a for a in apps if re.search("settings|contacts", a["desc"], re.I)),
                          apps[0])
            x, y = center(target)
            long_press(x, y)
            menu = snap("work-app-menu")
            log(f"  work app menu: {bool(find(menu, r'^app info$|^rename$|^hide$'))}")
            close_popup()
            nodes = open_drawer("drawer-2", r"search apps") or []
            work_tab = find(nodes, r"^work$|work apps")
            if work_tab:
                tap(work_tab)
                time.sleep(2)
            nodes = snap("work-tab-2")
            t = next((n for n in nodes if n["desc"] == target["desc"]
                      and n["id"].endswith("/icon")), None)
            if t:
                tap(t)
                time.sleep(5)
                top = sh("dumpsys activity activities | grep -m1 topResumedActivity")
                log(f"  work app opens in the work profile: {f'u{work}' in top or 'u10' in top} "
                    f"({top.strip()[:110]!r})")
        go_home()
        nodes = open_drawer("drawer-3", r"search apps") or []
        personal = find(nodes, r"^personal$")
        if personal:
            tap(personal)
            time.sleep(2)
        log(f"  personal apps back: {bool(find(snap('personal-tab'), r'^(camera|clock|settings)$'))}")
    go_home()

    # --- Private Space ---
    open_drawer("drawer-private", r"search apps")
    scroll_drawer()
    nodes = snap("drawer-bottom")
    ps = find(nodes, r"^private$|private space")
    log(f"  Private Space shown in the drawer: {bool(ps)}")
    lock = find(nodes, r"unlock|^private space.*locked|lock private")
    if lock:
        tap(lock)
        time.sleep(6)
        nodes = snap("private-unlock")
        # No lock screen credential on the emulator: it unlocks straight away.
        scroll_drawer(3)
        nodes = snap("private-open")
        apps = [n["desc"] for n in nodes if n["id"].endswith("/icon") and n["desc"]
                and n["bounds"][1] > h * 0.2]
        log(f"  Private Space apps after unlocking: {apps[-6:]}")
        lock2 = find(nodes, r"^lock$|lock private")
        if lock2:
            tap(lock2)
            time.sleep(4)
            log(f"  locks again: {not find(snap('private-locked'), r'^lock$')}")
    go_home()
    snap("end")
    save_logs()


if __name__ == "__main__":
    main()

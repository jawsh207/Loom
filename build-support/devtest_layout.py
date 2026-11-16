#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test that changing the grid size (Home settings › Grid) keeps the whole layout.

Builds a home screen with a widget, app icons, a folder on the home screen and a folder in the
dock, then changes the grid three times: to the smallest size (3×3, dock of 3), to the
largest (8×10, dock of 8) and back to the phone's default. After each change it saves a
backup (Home settings › Backup & restore), pulls it off the emulator and reads the layout
database inside it, checking that:

- every item that was there before is still there (same ids, same apps, same widget),
- folder contents are unchanged,
- every home screen item is inside the new grid and nothing overlaps,
- every dock icon fits the new dock,
- the widget keeps its id and isn't waiting to be set up again.

usage: devtest_layout.py OUT_DIR LOOM_APK"""

import os
import re
import sqlite3
import time
import zipfile

from devtest import OUT, adb, find, log, open_home_settings, save_logs, screen_size, setup_home, sh, snap, \
    tap, tap_text
from devtest_grid import center, go_home, open_drawer, tap_scrolled

DESKTOP, HOTSEAT = -100, -101
APPWIDGET = 4
FAILED = []


open_settings = open_home_settings


def check(label, ok, detail=""):
    log(f"  {label}: {ok}{f' ({detail})' if detail else ''}")
    if not ok:
        FAILED.append(label)


# --- Reading the layout through a backup ---

def backup(tag):
    """Backs up through the app, pulls the newest backup file and returns its rows."""
    open_settings()
    tap_scrolled(r"^backup & restore$", f"{tag}-backup-screen", wait=3)
    tap_text(r"^back up$", f"{tag}-backup-tap", wait=4)
    save = find(snap(f"{tag}-save-picker"), r"^save$")
    if save:
        tap(save)
        time.sleep(5)
    go_home()
    listing = sh("ls -t /sdcard/Download/").splitlines()
    newest = next((l.strip() for l in listing if l.strip().startswith("Loom backup")), None)
    if not newest:
        log(f"  !! {tag}: no backup file")
        return None
    local = OUT / f"layout-{tag}.zip"
    adb("pull", f"/sdcard/Download/{newest}", str(local))
    sh(f"rm '/sdcard/Download/{newest}'")
    with zipfile.ZipFile(local) as z:
        z.extract("launcher.db", OUT / f"layout-{tag}")
    db = sqlite3.connect(OUT / f"layout-{tag}" / "launcher.db")
    db.row_factory = sqlite3.Row
    rows = [dict(r) for r in db.execute(
        "SELECT _id, title, intent, container, screen, cellX, cellY, spanX, spanY, itemType, "
        "appWidgetId, appWidgetProvider, restored FROM favorites")]
    db.close()
    log(f"  {tag}: {len(rows)} items")
    for r in sorted(rows, key=lambda r: (r["container"], r["screen"], r["cellY"], r["cellX"])):
        log(f"    {r['_id']:>3} c={r['container']:>4} s={r['screen']:>2} "
            f"({r['cellX']},{r['cellY']}) {r['spanX']}x{r['spanY']} t={r['itemType']} "
            f"{(r['title'] or r['appWidgetProvider'] or '')[:40]}")
    return rows


def identity(r):
    what = r["appWidgetProvider"] if r["itemType"] == APPWIDGET else r["intent"]
    folder_child = r["container"] not in (DESKTOP, HOTSEAT)
    return (r["itemType"], what, r["container"] if folder_child else None)


def verify(tag, before, after, columns, rows, dock):
    if after is None:
        check(f"{tag}: layout read", False)
        return
    a = {r["_id"]: r for r in after}
    b = {r["_id"]: r for r in before}
    missing = [b[i]["title"] or b[i]["appWidgetProvider"] for i in b if i not in a]
    check(f"{tag}: nothing lost", not missing, f"missing {missing}" if missing else
          f"{len(b)} items kept")
    check(f"{tag}: nothing added", set(a) <= set(b), str(sorted(set(a) - set(b))))
    changed = [i for i in b if i in a and identity(a[i]) != identity(b[i])]
    check(f"{tag}: items and folder contents unchanged", not changed, str(changed))

    occupied = {}
    outside, overlap = [], []
    for r in after:
        if r["container"] == DESKTOP:
            if (columns and r["cellX"] + r["spanX"] > columns) or \
                    (rows and r["cellY"] + r["spanY"] > rows) or r["cellX"] < 0 or r["cellY"] < 0:
                outside.append(r["_id"])
            cells = occupied.setdefault(r["screen"], set())
            for x in range(r["cellX"], r["cellX"] + r["spanX"]):
                for y in range(r["cellY"], r["cellY"] + r["spanY"]):
                    if (x, y) in cells:
                        overlap.append(r["_id"])
                    cells.add((x, y))
    check(f"{tag}: all inside the {columns}x{rows} grid", not outside, str(outside))
    check(f"{tag}: nothing overlaps", not overlap, str(overlap))
    dock_ranks = sorted(r["screen"] for r in after if r["container"] == HOTSEAT)
    check(f"{tag}: dock fits {dock} icons",
          not dock or all(0 <= s < dock for s in dock_ranks) and len(set(dock_ranks)) == len(dock_ranks),
          str(dock_ranks))
    for r in after:
        if r["itemType"] == APPWIDGET and r["_id"] in b:
            check(f"{tag}: widget keeps its id and isn't waiting to be set up",
                  r["appWidgetId"] == b[r["_id"]]["appWidgetId"] and not r["restored"],
                  f"id {r['appWidgetId']}, restored {r['restored']}, {r['spanX']}x{r['spanY']}")


# --- Changing the grid through Home settings › Grid ---

def value_in_row(nodes, label):
    """The number shown on the row labelled [label] (the first such row)."""
    row = find(nodes, label)
    if not row:
        return None
    y = center(row)[1]
    for n in nodes:
        if re.fullmatch(r"\d+", n["text"] or "") and abs(center(n)[1] - y) < 60:
            return int(n["text"])
    return None


def set_value(label, tag, target):
    for _ in range(12):
        nodes = snap(f"grid-{tag}")
        v = value_in_row(nodes, label)
        if v is None or v == target:
            return v
        button = find(nodes, f"^{'more' if v < target else 'fewer'} {tag}$")
        if not button:
            return v
        tap(button)
        time.sleep(0.5)
    return value_in_row(snap(f"grid-{tag}-end"), label)


def switch_on(nodes, label):
    n = find(nodes, label)
    if not n:
        return None
    y = center(n)[1]
    sw = [m for m in nodes if m["checkable"] and abs(center(m)[1] - y) < 80]
    return sw[0]["checked"] if sw else None


def change_grid(tag, columns, rows, dock):
    """Sets the home screen and dock size (None: back to the defaults) and applies it."""
    open_settings()
    tap_scrolled(r"^grid$", f"{tag}-grid", wait=4, up=True)
    nodes = snap(f"{tag}-grid-screen")
    default = None
    if columns is None:
        w, h = screen_size()
        for _ in range(3):  # the button is at the bottom of the screen
            if tap_text(r"^back to the defaults$", f"{tag}-defaults", wait=1):
                break
            sh(f"input swipe {w // 2} {h * 3 // 4} {w // 2} {h // 4} 400")
            time.sleep(1.5)
        nodes = snap(f"{tag}-grid-defaults")
        m = next((re.search(r"(\d+) × (\d+)", n["text"]) for n in nodes
                  if "default for this phone" in (n["text"] or "").lower()), None)
        default = (int(m.group(1)), int(m.group(2))) if m else None
    else:
        if not switch_on(nodes, r"^choose home screen size$"):
            tap_text(r"^choose home screen size$", f"{tag}-custom-home", wait=1)
        set_value(r"^columns$", "columns", columns)
        set_value(r"^rows$", "rows", rows)
        # The dock's controls are further down the screen.
        w, h = screen_size()
        sh(f"input swipe {w // 2} {h * 3 // 4} {w // 2} {h // 2} 400")
        time.sleep(1.5)
        if not switch_on(snap(f"{tag}-dock-switch"), r"^choose dock size$"):
            tap_text(r"^choose dock size$", f"{tag}-custom-dock", wait=1)
        set_value(r"^dock icons$", "dock icons", dock)
        snap(f"{tag}-grid-set")
    tap_text(r"^apply$", f"{tag}-apply", wait=12)
    go_home()
    time.sleep(5)
    snap(f"{tag}-home")
    return default


def add_clock_widget():
    """Adds Clock's widget through the picker: it needs no setup (a widget that opens a setup
    screen would be cancelled by the test and removed by Android, muddling the check)."""
    w, h = screen_size()
    go_home()
    sh(f"input swipe {w // 2} {h // 3} {w // 2} {h // 3} 1500")  # empty spot: home menu
    time.sleep(2)
    if not tap_text(r"^widgets$", "widget-menu", wait=6):
        return False
    nodes = snap("picker")
    search = next((n for n in nodes if "search" in (n["id"] + n["text"] + n["desc"]).lower()
                   and n["bounds"][1] < h // 3), None)
    if search:
        tap(search)
        time.sleep(1)
        sh("input text clock")
        time.sleep(3)
    for attempt in range(4):
        nodes = snap(f"picker-clock-{attempt}")
        # Search results list each widget by name ("Analog clock"), with its size apart.
        widget = next((n for n in nodes if re.search(r"clock.*wide by", n["desc"], re.I)), None) \
            or next((n for n in reversed(nodes)
                     if re.fullmatch(r"analog clock", (n["text"] or n["desc"]).strip(), re.I)), None)
        if widget:
            break
        header = next((n for n in nodes if re.fullmatch(r"clock", n["text"] or "", re.I)
                       and n["bounds"][1] > h // 5), None)
        if header:
            tap(header)
            time.sleep(2)
        else:
            sh(f"input swipe {w // 2} {h * 3 // 4} {w // 2} {h // 3} 400")
            time.sleep(1.5)
    else:
        log("  !! no Clock widget in the picker; it showed: " + "; ".join(
            f"{n['text'] or n['desc']}" for n in nodes if n["text"] or n["desc"])[:600])
        go_home()
        return False
    log(f"  widget: {widget['desc']!r}")
    tap(widget)
    time.sleep(3)
    nodes = snap("picker-selected")
    add = find(nodes, r"^add$|^add .*widget$|add to home")
    if add:
        tap(add)
        time.sleep(6)
    nodes = snap("widget-bind")
    allow = find(nodes, r"^create$|^allow$")
    if allow:
        always = find(nodes, r"always allow|allow .* to create widgets")
        if always:
            tap(always)
            time.sleep(1)
        tap(allow)
        time.sleep(6)
    go_home()
    bound = "deskclock" in sh("dumpsys appwidget | grep -i 'provider=' ")
    log(f"  Clock widget placed and bound: {bound}")
    return bound


def build_layout():
    w, h = screen_size()
    add_clock_widget()
    go_home()
    nodes = snap("start")
    dock = [n for n in nodes if n["desc"] and n["bounds"][1] > h * 0.8]
    # A folder in the dock, keeping all four dock spots full (so a smaller dock has to move
    # one to the home screen): drop an app from the drawer on the first dock icon.
    log(f"  dock: {[n['desc'] for n in dock]}")
    if dock:
        nodes = open_drawer("drawer-dock", r"search apps") or []
        icons = [n for n in nodes if n["id"].endswith("/icon") and n["desc"]
                 and h * 0.25 < n["bounds"][1] < h * 0.85]
        if icons:
            (x1, y1), (x2, y2) = center(icons[-1]), center(dock[0])
            sh(f"input draganddrop {x1} {y1} {x2} {y2} 2500")
            time.sleep(3)
        go_home()
    # Six apps from the drawer onto the home screen, spread over the top half.
    spots = [(0.2, 0.42), (0.4, 0.42), (0.6, 0.42), (0.8, 0.42), (0.2, 0.55), (0.2, 0.55)]
    placed = 0
    for i, (fx, fy) in enumerate(spots):
        nodes = open_drawer(f"drawer-{i}", r"search apps") or []
        icons = [n for n in nodes if n["id"].endswith("/icon") and n["desc"]
                 and h * 0.25 < n["bounds"][1] < h * 0.85]
        if len(icons) <= i + 2:
            go_home()
            continue
        x, y = center(icons[i + 2])
        # Hold, then move out of the drawer onto the home screen.
        sh(f"input draganddrop {x} {y} {int(w * fx)} {int(h * fy)} 2500")
        time.sleep(3)
        go_home()
        placed += 1
    log(f"  apps dragged onto the home screen: {placed} (the last one onto another: a folder)")
    snap("built")


def main():
    setup_home()
    build_layout()
    before = backup("before")
    if not before:
        save_logs()
        return
    kinds = {t: sum(1 for r in before if r["itemType"] == t) for t in (0, 2, 4, 6)}
    log(f"  layout: {len(before)} items; apps {kinds[0]}, folders {kinds[2]}, widgets {kinds[4]}, "
        f"shortcuts {kinds[6]}; in the dock {sum(1 for r in before if r['container'] == HOTSEAT)}; "
        f"in folders {sum(1 for r in before if r['container'] not in (DESKTOP, HOTSEAT))}")
    check("test layout has a widget", kinds[4] >= 1)
    check("test layout has a folder", kinds[2] >= 1)
    check("test layout's dock is full (4)", sum(1 for r in before if r["container"] == HOTSEAT) >= 4)

    change_grid("small", 3, 3, 3)
    verify("small 3x3", before, backup("small"), 3, 3, 3)

    change_grid("large", 8, 10, 8)
    verify("large 8x10", before, backup("large"), 8, 10, 8)

    default = change_grid("default", None, None, None)
    cols, rows = default if default else (None, None)
    verify(f"default {cols}x{rows}", before, backup("default"), cols, rows, None)

    log(f"  RESULT: {'all checks passed' if not FAILED else 'FAILED: ' + ', '.join(FAILED)}")
    save_logs()


if __name__ == "__main__":
    main()

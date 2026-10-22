#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator check that Loom's screens use the same colours as the system Settings app.

In light and dark mode: screenshots of Settings, Loom's app drawer, a long-press menu, Home
settings › Feeds (Manage feeds) and Grid; logs each one's background colour and saves a
side-by-side sheet (theme-light.png, theme-dark.png).

usage: devtest_theme.py OUT_DIR LOOM_APK"""

import glob
import time

from PIL import Image

from devtest import OUT, find, log, save_logs, screen_size, setup_home, sh, snap, tap
from devtest_grid import center, close_popup, go_home, long_press, open_drawer, open_settings, \
    tap_scrolled


def last_png():
    return sorted(glob.glob(str(OUT / "*.png")))[-1]


def color_at(x, y):
    img = Image.open(last_png()).convert("RGB")
    x = min(max(0, x), img.width - 1)
    y = min(max(0, y), img.height - 1)
    return img.getpixel((x, y))


def hexc(c):
    return "#%02X%02X%02X" % c


def shoot(mode):
    w, h = screen_size()
    shots = []

    sh("am start -a android.settings.SETTINGS")
    time.sleep(4)
    snap(f"{mode}-settings")
    shots.append(("Settings", last_png(), color_at(12, h // 2)))
    go_home()

    open_drawer(f"{mode}-drawer", r"search apps")
    shots.append(("Loom drawer", last_png(), color_at(12, h // 2)))
    go_home()

    nodes = snap(f"{mode}-home")
    gallery = find(nodes, r"^gallery$")
    if gallery:
        x, y = center(gallery)
        long_press(x, y)
        nodes = snap(f"{mode}-menu")
        info = find(nodes, r"^app info$")
        c = color_at(info["bounds"][2] + 20, (info["bounds"][1] + info["bounds"][3]) // 2) \
            if info else (0, 0, 0)
        shots.append(("Loom menu", last_png(), c))
        close_popup()

    open_settings()
    if tap_scrolled(r"^feeds$", f"{mode}-feeds", wait=4):
        snap(f"{mode}-manage-feeds")
        shots.append(("Manage feeds", last_png(), color_at(12, h // 2)))
        sh("input keyevent KEYCODE_BACK")
        time.sleep(2)
    if tap_scrolled(r"^grid$", f"{mode}-grid", wait=4, up=True):
        snap(f"{mode}-grid-screen")
        shots.append(("Grid", last_png(), color_at(10, h // 2)))
        sh("input keyevent KEYCODE_BACK")
        time.sleep(2)
    go_home()

    for name, _, c in shots:
        log(f"  {mode}: {name} background {hexc(c)}")
    tiles = [Image.open(p).convert("RGB") for _, p, _ in shots]
    for t in tiles:
        t.thumbnail((300, 670))
    sheet = Image.new("RGB", (sum(t.width for t in tiles) + 10 * len(tiles), 680), (128, 128, 128))
    x = 0
    for t in tiles:
        sheet.paste(t, (x, 0))
        x += t.width + 10
    sheet.save(OUT / f"theme-{mode}.png")


def main():
    setup_home()
    sh("cmd uimode night no")
    time.sleep(4)
    shoot("light")
    sh("cmd uimode night yes")
    time.sleep(10)
    go_home()
    time.sleep(3)
    snap("dark-settle")  # first screenshot after the switch can be partial
    shoot("dark")
    sh("cmd uimode night no")
    save_logs()


if __name__ == "__main__":
    main()

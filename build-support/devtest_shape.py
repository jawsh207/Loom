#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test that app icons follow the system's icon shape (Wallpaper & style › icon
shape, which is an overlay) everywhere: on the home screen and in the app drawer.

Switches the shape overlay, then measures how much of the same apps' icons is filled on the
home screen and in the drawer: a square shape fills the corners a circle leaves empty.

usage: devtest_shape.py OUT_DIR LOOM_APK"""

import glob
import re
import time

from PIL import Image

from devtest import OUT, find, log, save_logs, screen_size, setup_home, sh, snap
from devtest_grid import center, go_home, open_drawer

APPS = ["Settings", "Camera", "Clock", "Contacts", "Files", "Chrome", "Messaging", "Phone"]


def last_png():
    return sorted(glob.glob(str(OUT / "[0-9]*.png")))[-1]


def corner_fill(png, node):
    """Share of the icon's four corner patches that isn't the background around it."""
    x1, y1, x2, y2 = node["bounds"]
    w = x2 - x1
    side = int(w * 0.62) if (y2 - y1) > w * 1.15 else int(min(w, y2 - y1) * 0.8)
    cx = (x1 + x2) // 2
    top = y1 + int(w * 0.06) if (y2 - y1) > w * 1.15 else (y1 + y2) // 2 - side // 2
    img = Image.open(png).convert("RGB")
    bg = img.getpixel((max(0, cx - side // 2 - 6), top + side // 2))
    patch = max(2, side // 8)
    filled = total = 0
    for (px, py) in ((cx - side // 2, top), (cx + side // 2 - patch, top),
                     (cx - side // 2, top + side - patch), (cx + side // 2 - patch, top + side - patch)):
        for y in range(py, py + patch):
            for x in range(px, px + patch):
                p = img.getpixel((x, y))
                total += 1
                if sum(abs(p[i] - bg[i]) for i in range(3)) > 60:
                    filled += 1
    return filled / max(1, total)


def measure(tag):
    w, h = screen_size()
    go_home()
    time.sleep(2)
    nodes = snap(f"{tag}-home")
    home = {}
    for a in APPS:
        n = next((n for n in nodes if n["desc"] == a and n["bounds"][1] > 150), None)
        if n:
            home[a] = corner_fill(last_png(), n)
    open_drawer(f"{tag}-drawer", r"search apps")
    nodes = snap(f"{tag}-drawer-icons")
    drawer = {}
    for a in APPS:
        n = next((n for n in nodes if n["desc"] == a and n["id"].endswith("/icon")
                  and n["bounds"][3] < h - 200), None)
        if n:
            drawer[a] = corner_fill(last_png(), n)
    go_home()
    fmt = lambda d: ", ".join(f"{k} {v:.0%}" for k, v in d.items())
    log(f"  {tag}: home corners filled [{fmt(home)}]")
    log(f"  {tag}: drawer corners filled [{fmt(drawer)}]")
    avg = lambda d: sum(d.values()) / len(d) if d else 0
    return avg(home), avg(drawer)


def main():
    setup_home()
    overlays = sh("cmd overlay list --user 0").splitlines()
    shapes = [re.sub(r"^\s*\[.\]\s*", "", l).strip() for l in overlays if "theme.icon." in l]
    log(f"  icon shape overlays: {shapes}")
    square = next((s for s in shapes if s.endswith(".square")), None) \
        or next((s for s in shapes if "roundedrect" in s), None)
    circle = next((s for s in shapes if s.endswith(".circle")), None)
    if not square:
        log("!! no square icon shape overlay on this image")
        save_logs()
        return
    if circle:
        sh(f"cmd overlay enable-exclusive --user 0 --category {circle}")
        time.sleep(8)
    h0, d0 = measure("circle")
    sh(f"cmd overlay enable-exclusive --user 0 --category {square}")
    time.sleep(10)
    h1, d1 = measure("square")
    log(f"  home follows the shape: {h1 - h0 > 0.3} ({h0:.0%} -> {h1:.0%})")
    log(f"  drawer follows the shape: {d1 - d0 > 0.3} ({d0:.0%} -> {d1:.0%})")
    # A crop of the same app on the home screen and in the drawer, each shape.
    save_logs()


if __name__ == "__main__":
    main()

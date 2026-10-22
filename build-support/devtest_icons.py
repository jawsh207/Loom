#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test of the colourful icons for GrapheneOS's apps and Brave.

Installs the real apps (official release APKs, downloaded by the workflow), then checks in
the app drawer that each icon shows its colour, that other apps are left alone, and that the
"Colorful GrapheneOS icons" switch turns it off again. Saves a contact sheet of the icons.

usage: devtest_icons.py OUT_DIR LOOM_APK APK_DIR"""

import glob
import os
import sys
import time

from PIL import Image

from devtest import OUT, adb, find, log, save_logs, screen_size, setup_home, sh, snap
from devtest_grid import go_home, open_drawer, open_settings, tap_scrolled

APK_DIR = sys.argv[3]

# Label in the drawer -> expected background colour.
EXPECTED = {
    "Camera": (0xD9, 0x30, 0x25),
    "PDF Viewer": (0xBF, 0x36, 0x0C),
    "Auditor": (0x6D, 0x4C, 0x41),
    "App Store": (0x2E, 0x7D, 0x32),  # GrapheneOS's Apps (its name in the release APK)
    "Brave": (0xFB, 0x54, 0x2B),
}


def last_png():
    return sorted(glob.glob(str(OUT / "*.png")))[-1]


def icon_box(node):
    """The icon part of a drawer cell (the label is underneath)."""
    x1, y1, x2, y2 = node["bounds"]
    w = x2 - x1
    side = int(w * 0.62)
    cx = (x1 + x2) // 2
    top = y1 + int(w * 0.06)
    return (cx - side // 2, top, cx + side // 2, top + side)


def share_of_color(img, box, rgb, tol=48):
    crop = img.crop(box).convert("RGB")
    px = list(crop.get_flattened_data()) if hasattr(crop, "get_flattened_data") else list(crop.getdata())
    near = sum(1 for p in px if sum(abs(p[i] - rgb[i]) for i in range(3)) < tol)
    return near / max(1, len(px))


def find_icons(nodes, label):
    return [n for n in nodes if n["desc"].lower() == label.lower() and n["id"].endswith("/icon")]


def find_icon(nodes, label):
    found = find_icons(nodes, label)
    return found[0] if found else None


def scan(name):
    """Checks every expected icon on the drawer's pages; returns {label: (share, box, png)}."""
    w, h = screen_size()
    found = {}
    for page in range(4):
        nodes = snap(f"{name}-{page}")
        img = Image.open(last_png())
        for label, rgb in EXPECTED.items():
            if label in found:
                continue
            # Several apps can share a name (the emulator has its own Camera): best match wins.
            best = None
            for n in find_icons(nodes, label):
                if n["bounds"][3] < h - 250:
                    box = icon_box(n)
                    share = share_of_color(img, box, rgb)
                    if best is None or share > best[0]:
                        best = (share, box, last_png())
            if best:
                found[label] = best
        oc = find_icon(nodes, "Open Camera")
        if oc and oc["bounds"][3] < h - 250 and name == "colored":
            from collections import Counter
            px = list(img.convert("RGB").crop(icon_box(oc)).getdata())
            sat = [((r // 8) * 8, (g // 8) * 8, (b // 8) * 8) for r, g, b in px
                   if max(r, g, b) - min(r, g, b) > 40]
            top = Counter(sat).most_common(3)
            log("  Open Camera icon colours: " + ", ".join(
                f"#{r:02X}{g:02X}{b:02X} ({n})" for (r, g, b), n in top))
            img.crop(icon_box(oc)).save(OUT / "opencamera-icon.png")
        if len(found) == len(EXPECTED) and page > 0:
            break
        sh(f"input swipe {w // 2} {h * 3 // 4} {w // 2} {h // 3} 400")
        time.sleep(1.5)
    return found


def main():
    setup_home()
    for apk in sorted(glob.glob(os.path.join(APK_DIR, "*.apk"))):
        out = adb("install", "-r", "-g", apk).strip().splitlines()
        log(f"  install {os.path.basename(apk)}: {out[-1] if out else '?'}")
    time.sleep(6)
    go_home()

    if not open_drawer("drawer-icons", r"search apps"):
        log("!! drawer didn't open")
    on = scan("colored")
    for label in EXPECTED:
        share = on.get(label, (None,))[0]
        log(f"  {label}: colored {share is not None and share > 0.2}"
            f" ({'not found' if share is None else f'{share:.0%} of the icon in its colour'})")

    # Other apps keep their own icons: Settings on this (non-GrapheneOS) emulator isn't white.
    nodes = snap("drawer-others")
    s = find_icon(nodes, "Settings")
    if s:
        img = Image.open(last_png())
        log(f"  Settings untouched: {share_of_color(img, icon_box(s), (0x54, 0x6E, 0x7A)) < 0.1}")

    # Contact sheet of the coloured icons.
    tiles = []
    for label, (share, box, png) in on.items():
        tiles.append(Image.open(png).crop(box).resize((180, 180)))
    if tiles:
        sheet = Image.new("RGB", (190 * len(tiles) + 10, 200), (236, 239, 241))
        for i, t in enumerate(tiles):
            sheet.paste(t, (10 + 190 * i, 10))
        sheet.save(OUT / "icons-sheet.png")
        log(f"  contact sheet: {', '.join(on.keys())}")

    # Switch it off: the icons go back to the apps' own.
    go_home()
    open_settings()
    tap_scrolled(r"^icons$", "settings-icons", wait=3)
    tap_scrolled(r"^colorful grapheneos icons$", "colored-off", wait=4)
    go_home()
    time.sleep(3)
    open_drawer("drawer-off", r"search apps")
    off = scan("plain")
    for label in EXPECTED:
        share = off.get(label, (None,))[0]
        if share is not None:
            log(f"  {label} back to its own icon when switched off: {share < 0.1} ({share:.0%})")
    save_logs()


if __name__ == "__main__":
    main()

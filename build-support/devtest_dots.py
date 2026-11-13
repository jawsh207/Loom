#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Emulator test of notification dots on folders and cover folders.

Makes a folder in the dock from the Phone and Messaging icons, turns it into a cover folder,
restarts the launcher (a cover that hasn't drawn its normal folder preview since start is
where the dot used to end up in the middle of the icon), then has the Phone app post a
missed-call notification and Messaging a text. Saves the folder icon and reports where the dot
is: it belongs at the icon's top right corner.

usage: devtest_dots.py OUT_DIR LOOM_APK"""

import glob
import time

from PIL import Image

from devtest import OUT, PKG, adb, find, log, save_logs, screen_size, setup_home, sh, snap
from devtest_grid import center, go_home


def last_png():
    return sorted(p for p in glob.glob(str(OUT / "[0-9]*.png")))[-1]


def dot_position(png, box):
    """Where the dot is in the icon box: the bright, saturated blob that isn't the icon."""
    img = Image.open(png).convert("RGB").crop(box)
    w, h = img.size
    before = Image.open(OUT / "cover-no-dot.png").convert("RGB").resize((w, h)) \
        if (OUT / "cover-no-dot.png").exists() else None
    xs, ys = [], []
    for y in range(h):
        for x in range(w):
            p = img.getpixel((x, y))
            if before is not None:
                q = before.getpixel((x, y))
                if sum(abs(p[i] - q[i]) for i in range(3)) > 90:
                    xs.append(x)
                    ys.append(y)
    if not xs:
        return None
    return (sum(xs) / len(xs) / w, sum(ys) / len(ys) / h, len(xs))


def icon_box(n):
    x1, y1, x2, y2 = n["bounds"]
    side = min(x2 - x1, y2 - y1)
    cx = (x1 + x2) // 2
    # Dock items have no label: the icon fills the cell's middle.
    cy = (y1 + y2) // 2
    pad = side // 10
    return (cx - side // 2 - pad, cy - side // 2 - pad, cx + side // 2 + pad, cy + side // 2 + pad)


def folder_menu(fx, fy, item, name, opened_by_tap):
    """Opens the folder, then its ⋮ Folder options menu, and picks [item]."""
    if opened_by_tap:
        sh(f"input tap {fx} {fy}")
    else:
        sh(f"input swipe {fx} {fy} {fx} {fy - 300} 250")
    time.sleep(3)
    nodes = snap(f"{name}-open")
    button = find(nodes, r"^folder options$")
    if not button:
        log(f"!! no Folder options button ({name})")
        go_home()
        return False
    x, y = center(button)
    sh(f"input tap {x} {y}")
    time.sleep(2)
    nodes = snap(f"{name}-menu")
    picked = find(nodes, item)
    if picked:
        x, y = center(picked)
        sh(f"input tap {x} {y}")
        time.sleep(2)
    else:
        log(f"!! no /{item}/")
    go_home()
    time.sleep(2)
    return bool(picked)


def main():
    setup_home()
    w, h = screen_size()
    nodes = snap("home")
    names = [n["desc"] for n in nodes if n["desc"] and n["bounds"][1] > h * 0.8]
    log(f"  dock: {names}")
    phone = find(nodes, r"^phone$")
    messages = find(nodes, r"^messag(es|ing)$")
    if not (phone and messages):
        log("!! Phone or Messages isn't in the dock")
        save_logs()
        return
    # Drag Messages onto Phone: a folder.
    (x1, y1), (x2, y2) = center(messages), center(phone)
    sh(f"input draganddrop {x1} {y1} {x2} {y2} 1500")
    time.sleep(3)
    go_home()
    time.sleep(2)
    nodes = snap("folder-made")
    folder = next((n for n in nodes if n["bounds"][1] > h * 0.8
                   and "folder" in (n["desc"] + n["cls"]).lower()), None)
    if not folder:
        log("!! no folder in the dock")
        save_logs()
        return
    log(f"  folder made: {folder['desc']!r}")
    fx, fy = center(folder)
    folder_menu(fx, fy, r"^make cover folder$", "cover-on", opened_by_tap=True)

    # Restart the launcher so the cover hasn't drawn a normal preview since it started.
    sh(f"am force-stop {PKG}")
    time.sleep(2)
    go_home()
    time.sleep(6)
    nodes = snap("cover-no-dot")
    folder = next((n for n in nodes if n["bounds"][1] > h * 0.8
                   and (fx - 20 < center(n)[0] < fx + 20)), None)
    box = icon_box(folder) if folder else None
    if box:
        Image.open(last_png()).crop(box).save(OUT / "cover-no-dot.png")

    # Notifications for apps in the folder: a missed call, and a text.
    for p in ("com.google.android.dialer", "com.android.dialer", "com.android.messaging",
              "com.google.android.apps.messaging"):
        sh(f"pm grant {p} android.permission.POST_NOTIFICATIONS")
    sh("cmd role add-role-holder --user 0 android.app.role.SMS com.android.messaging 0")
    adb("emu", "gsm", "call", "5551234")
    time.sleep(6)
    adb("emu", "gsm", "cancel", "5551234")
    time.sleep(3)
    adb("emu", "sms", "send", "5551234", "Hello from the dot test")
    time.sleep(10)
    go_home()
    sh("cmd statusbar collapse")
    time.sleep(4)
    log(f"  notifications: {sh('dumpsys notification --noredact | grep -E \"pkg=com.(google.)?android.(dialer|messaging|apps.messaging)\" | head -5').strip()[:300]}")
    snap("cover-dot")
    if box:
        Image.open(last_png()).crop(box).save(OUT / "cover-dot.png")
        pos = dot_position(OUT / "cover-dot.png", (0, 0, box[2] - box[0], box[3] - box[1]))
        if pos:
            x, y, n = pos
            where = "top right" if x > 0.6 and y < 0.4 else (
                "middle" if 0.35 < x < 0.65 and 0.35 < y < 0.65 else "elsewhere")
            log(f"  cover dot at x={x:.2f} y={y:.2f} ({where}), {n} px changed")
        else:
            log("  cover dot: none found")

    # The same folder as a regular folder (a cover opens with a swipe up).
    folder_menu(fx, fy, r"^make regular folder$", "cover-off", opened_by_tap=False)
    time.sleep(2)
    snap("folder-dot")
    if box:
        Image.open(last_png()).crop(box).save(OUT / "folder-dot.png")
    sheet = [Image.open(OUT / f).resize((240, 240)) for f in
             ("cover-no-dot.png", "cover-dot.png", "folder-dot.png") if (OUT / f).exists()]
    if sheet:
        s = Image.new("RGB", (250 * len(sheet) + 10, 260), (128, 128, 128))
        for i, t in enumerate(sheet):
            s.paste(t, (10 + 250 * i, 10))
        s.save(OUT / "dots-sheet.png")
    save_logs()


if __name__ == "__main__":
    main()

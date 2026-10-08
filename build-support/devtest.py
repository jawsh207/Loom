#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Drives Folio on an emulator to add a home-screen widget, saving screenshots, UI dumps
and logs to an output directory at every step. Used by .github/workflows/device-test.yml."""

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
PKG = "app.folio.launcher"
step_no = 0


def adb(*args, check=False, capture=True):
    r = subprocess.run(["adb", *args], capture_output=capture, text=True, timeout=120)
    if check and r.returncode:
        raise RuntimeError(f"adb {args}: {r.stderr}")
    return (r.stdout + r.stderr) if capture else ""


def sh(cmd):
    return adb("shell", cmd)


def log(msg):
    print(msg, flush=True)
    with open(OUT / "steps.txt", "a") as f:
        f.write(msg + "\n")


def snap(name):
    """Screenshot plus UI hierarchy for this step."""
    global step_no
    step_no += 1
    base = f"{step_no:02d}-{name}"
    with open(OUT / f"{base}.png", "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"],
                               capture_output=True, timeout=60).stdout)
    sh("uiautomator dump /sdcard/ui.xml > /dev/null 2>&1")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    (OUT / f"{base}.xml").write_text(xml)
    nodes = parse(xml)
    with open(OUT / f"{base}.txt", "w") as f:
        for n in nodes:
            if n["text"] or n["desc"] or n["id"]:
                f.write(f'{n["bounds"]} text={n["text"]!r} desc={n["desc"]!r} '
                        f'id={n["id"]} click={n["click"]}\n')
    log(f"[{base}] activity: {top_activity()}")
    return nodes


def parse(xml):
    nodes = []
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return nodes
    for e in root.iter("node"):
        b = [int(x) for x in re.findall(r"\d+", e.get("bounds", "[0,0][0,0]"))]
        nodes.append({"text": e.get("text", ""), "desc": e.get("content-desc", ""),
                      "id": e.get("resource-id", ""), "click": e.get("clickable"),
                      "bounds": b})
    return nodes


def find(nodes, pattern):
    rx = re.compile(pattern, re.I)
    for n in nodes:
        if rx.search(n["text"]) or rx.search(n["desc"]):
            return n
    return None


def tap(node, long_ms=0):
    x1, y1, x2, y2 = node["bounds"]
    x, y = (x1 + x2) // 2, (y1 + y2) // 2
    if long_ms:
        sh(f"input swipe {x} {y} {x} {y} {long_ms}")
    else:
        sh(f"input tap {x} {y}")


def tap_text(pattern, name, wait=2.0):
    nodes = snap(f"before-{name}")
    n = find(nodes, pattern)
    if not n:
        log(f"  !! no node matching /{pattern}/")
        return False
    log(f"  tapping {n['text'] or n['desc']!r} at {n['bounds']}")
    tap(n)
    time.sleep(wait)
    return True


def top_activity():
    out = sh("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -2")
    return " | ".join(l.strip() for l in out.splitlines())


def screen_size():
    m = re.search(r"(\d+)x(\d+)", sh("wm size"))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def go_home():
    sh("input keyevent KEYCODE_HOME")
    time.sleep(2)


def add_widget_attempt(label):
    log(f"=== attempt: {label} ===")
    go_home()
    w, h = screen_size()
    snap(f"{label}-home")
    # Long-press an empty spot of the home screen to get the options menu.
    sh(f"input swipe {w // 2} {h // 3} {w // 2} {h // 3} 1500")
    time.sleep(2)
    if not tap_text(r"^widgets$", f"{label}-options", wait=4):
        return
    nodes = snap(f"{label}-picker")
    # Expand the first app that offers widgets: prefer Clock, else any header-like row.
    app = find(nodes, r"^clock$") or find(nodes, r"widgets?$")
    if app:
        log(f"  opening app {app['text'] or app['desc']!r}")
        tap(app)
        time.sleep(3)
    nodes = snap(f"{label}-app-expanded")
    # Tap the first widget preview so its Add button appears.
    preview = find(nodes, r"(analog|digital|clock|widget).*\d+\s*[x×]\s*\d+|\d+\s*[x×]\s*\d+")
    if preview:
        log(f"  selecting preview {preview['text'] or preview['desc']!r}")
        tap(preview)
        time.sleep(2)
    if not tap_text(r"^add$|add widget|^add to home", f"{label}-add", wait=4):
        return
    nodes = snap(f"{label}-after-add")
    allow = find(nodes, r"^create$|^allow$")
    if allow:
        always = find(nodes, r"always allow")
        if always:
            tap(always)
            time.sleep(1)
        log(f"  bind dialog: tapping {allow['text']!r}")
        tap(allow)
        time.sleep(4)
    snap(f"{label}-result")
    time.sleep(3)
    snap(f"{label}-result-later")
    (OUT / f"appwidget-{label}.txt").write_text(sh("dumpsys appwidget"))


def main():
    sh("settings put global window_animation_scale 0; "
       "settings put global transition_animation_scale 0; "
       "settings put global animator_duration_scale 0")
    log(adb("install", "-r", "-g", sys.argv[2]))
    log(sh(f"cmd role add-role-holder --user 0 android.app.role.HOME {PKG} 0"))
    log(sh(f"cmd package set-home-activity --user 0 {PKG}/com.android.launcher3.Launcher"))
    log("home role: " + sh("cmd role get-role-holders --user 0 android.app.role.HOME"))
    sh("logcat -c")
    go_home()
    time.sleep(5)
    snap("first-home")
    add_widget_attempt("nobind")
    log(sh(f"cmd appwidget grantbind --package {PKG} --user 0"))
    add_widget_attempt("granted")
    (OUT / "logcat.txt").write_text(adb("logcat", "-d", "-v", "threadtime"))
    (OUT / "logcat-folio.txt").write_text(adb(
        "logcat", "-d", "-v", "threadtime", "-s",
        "Launcher:*", "AndroidRuntime:*", "AppWidgetHostView:*", "ActivityTaskManager:*",
        "WidgetPickerDragItemListener:*", "BaseItemDragListener:*", "WidgetManagerHelper:*",
        "LauncherWidgetHolder:*", "AppWidgetServiceImpl:*", "ContextTracker:*"))


if __name__ == "__main__":
    main()

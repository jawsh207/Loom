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
    # Screenshot from the emulator host side: screencap inside the guest crashes
    # SurfaceFlinger with the emulator's software GPU.
    adb("emu", "screenrecord", "screenshot", str((OUT / f"{base}.png").resolve()))
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


def find_id(nodes, suffix):
    for n in nodes:
        if n["id"].endswith(suffix):
            return n
    return None


def add_widget_attempt(label, use_drag=False):
    log(f"=== attempt: {label} ===")
    for _ in range(4):
        go_home()
        if PKG in top_activity():
            break
        log("  Folio isn't on top; waiting for the system")
        wait_for_system()
        sh(f"cmd role add-role-holder --user 0 android.app.role.HOME {PKG} 0")
    w, h = screen_size()
    snap(f"{label}-home")
    # Long-press an empty spot of the home screen to get the options menu.
    sh(f"input swipe {w // 2} {h // 3} {w // 2} {h // 3} 1500")
    time.sleep(2)
    if not tap_text(r"^widgets$", f"{label}-options", wait=10):
        return
    nodes = snap(f"{label}-picker")
    details = find_id(nodes, ":id/widget_details")
    if not details:
        log("  !! no widget in the picker")
        return
    name = next((n["desc"] for n in nodes if "wide by" in n["desc"]), "?")
    log(f"  first featured widget: {name!r}")
    if use_drag:
        preview = find_id(nodes, ":id/widget_preview")
        x1, y1, x2, y2 = preview["bounds"]
        x, y = (x1 + x2) // 2, (y1 + y2) // 2
        log(f"  dragging preview from {x},{y} to {w // 2},{h // 2}")
        sh(f"input draganddrop {x} {y} {w // 2} {h // 2} 3000")
        time.sleep(6)
    else:
        tap(details)
        time.sleep(3)
        nodes = snap(f"{label}-selected")
        add = find(nodes, r"^add$|^add .*widget$|add to home")
        if not add:
            log("  !! no Add button")
            return
        log(f"  tapping {add['text'] or add['desc']!r}")
        tap(add)
        time.sleep(6)
    nodes = snap(f"{label}-after-add")
    allow = find(nodes, r"^create$|^allow$")
    if allow:
        always = find(nodes, r"always allow|allow .* to create widgets")
        if always and always.get("click") == "true":
            tap(always)
            time.sleep(1)
        log(f"  bind dialog: tapping {allow['text']!r}")
        tap(allow)
        time.sleep(6)
    else:
        log("  no bind dialog")
    snap(f"{label}-result")
    time.sleep(5)
    nodes = snap(f"{label}-result-later")
    widgets = [n for n in nodes if "appwidget" in n["id"].lower() or "hostview" in n["id"].lower()]
    log(f"  widget-like views on home: {len(widgets)}")
    (OUT / f"appwidget-{label}.txt").write_text(sh("dumpsys appwidget"))


def wait_for_system():
    """sys.boot_completed can be set before system_server has settled (it may restart once
    on a first boot), so wait until package and activity services answer steadily."""
    ok = 0
    for _ in range(60):
        pm = sh("pm path android")
        am = sh("dumpsys activity activities | grep -c topResumedActivity")
        ok = ok + 1 if ("package:" in pm and am.strip() not in ("", "0")) else 0
        if ok >= 3:
            log("system ready")
            return
        time.sleep(10)
    log("!! system never settled")


def calm_surfaceflinger():
    """The emulator's software GPU aborts SurfaceFlinger when it samples screen regions (for
    navigation-bar tinting). Make sampling effectively never happen, then restart the UI."""
    log(adb("root"))
    time.sleep(5)
    adb("wait-for-device")
    sh("setprop debug.sf.region_sampling_period_ns 3600000000000; "
       "setprop debug.sf.region_sampling_timer_timeout_ns 3600000000000; "
       "setprop debug.sf.region_sampling_duration_ns 1")
    sh("stop; sleep 2; start")
    time.sleep(30)
    wait_for_system()


def main():
    wait_for_system()
    # Nexus launcher's region sampling is what trips the emulator's GPU bug.
    time.sleep(10)
    wait_for_system()
    sh("settings put global window_animation_scale 0; "
       "settings put global transition_animation_scale 0; "
       "settings put global animator_duration_scale 0")
    for attempt in range(6):
        wait_for_system()
        if "package:" not in sh(f"pm path {PKG}"):
            log(adb("install", "-r", "-g", sys.argv[2]))
        sh(f"cmd role add-role-holder --user 0 android.app.role.HOME {PKG} 0")
        sh("pm disable-user --user 0 com.google.android.apps.nexuslauncher")
        go_home()
        time.sleep(8)
        top = top_activity()
        log(f"setup {attempt}: home role={sh('cmd role get-role-holders --user 0 android.app.role.HOME').strip()} top={top}")
        if PKG in top:
            break
        time.sleep(20)
    sh("logcat -G 16M; logcat -c")
    go_home()
    time.sleep(5)
    snap("first-home")
    add_widget_attempt("add-button")
    add_widget_attempt("again")
    add_widget_attempt("drag", use_drag=True)
    (OUT / "logcat.txt").write_text(adb("logcat", "-d", "-v", "threadtime"))
    (OUT / "logcat-folio.txt").write_text(adb(
        "logcat", "-d", "-v", "threadtime", "-s",
        "Launcher:*", "AndroidRuntime:*", "AppWidgetHostView:*", "ActivityTaskManager:*",
        "WidgetPickerDragItemListener:*", "BaseItemDragListener:*", "WidgetManagerHelper:*",
        "LauncherWidgetHolder:*", "AppWidgetServiceImpl:*", "ContextTracker:*"))


if __name__ == "__main__":
    main()

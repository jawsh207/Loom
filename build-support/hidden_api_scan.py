#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Lists references from Folio's compiled classes to Android framework members that are not
in the public SDK. Android blocks most of those for apps that aren't part of the OS, so each
one is a potential crash (NoSuchMethodError / NoSuchFieldError / NoClassDefFoundError).

usage: hidden_api_scan.py PUBLIC_ANDROID_JAR HIDDEN_ANDROID_JAR CLASS_DIR_OR_JAR...

Only direct references are found (calls, field accesses, and javac/kotlinc bridge methods);
reflection isn't."""

import struct
import sys
import zipfile
from collections import defaultdict
from pathlib import Path


class ClassInfo:
    __slots__ = ("name", "super", "interfaces", "members", "refs", "class_refs")


def parse_class(data, want_refs):
    def u2(o):
        return struct.unpack_from(">H", data, o)[0]

    count = u2(8)
    cp = [None] * count
    o, i = 10, 1
    while i < count:
        tag = data[o]
        if tag == 1:
            ln = u2(o + 1)
            cp[i] = (1, data[o + 3:o + 3 + ln].decode("utf-8", "replace"))
            o += 3 + ln
        elif tag in (3, 4):
            o += 5
        elif tag in (5, 6):
            o += 9
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = (tag, u2(o + 1))
            o += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            cp[i] = (tag, u2(o + 1), u2(o + 3))
            o += 5
        elif tag == 15:
            o += 4
        else:
            raise ValueError(f"bad constant tag {tag}")
        i += 1

    def utf(idx):
        return cp[idx][1]

    def cls(idx):
        return utf(cp[idx][1]) if idx else None

    c = ClassInfo()
    c.name = cls(u2(o + 2))
    c.super = cls(u2(o + 4))
    n = u2(o + 6)
    c.interfaces = [cls(u2(o + 8 + 2 * k)) for k in range(n)]
    o += 8 + 2 * n
    c.members = set()
    for kind in ("f", "m"):
        n = u2(o)
        o += 2
        for _ in range(n):
            name, desc = utf(u2(o + 2)), utf(u2(o + 4))
            c.members.add((kind, name, desc if kind == "m" else ""))
            attrs = u2(o + 6)
            o += 8
            for _ in range(attrs):
                o += 6 + struct.unpack_from(">I", data, o + 2)[0]
    c.refs, c.class_refs = set(), set()
    if want_refs:
        for e in cp:
            if e and e[0] in (9, 10, 11):
                owner = cls(e[1])
                nt = cp[e[2]]
                kind = "f" if e[0] == 9 else "m"
                desc = utf(nt[2]) if kind == "m" else ""
                c.refs.add((owner, kind, utf(nt[1]), desc))
            elif e and e[0] == 7:
                c.class_refs.add(utf(e[1]))
    return c


def load_jar(path):
    out = {}
    with zipfile.ZipFile(path) as z:
        for n in z.namelist():
            if n.endswith(".class"):
                c = parse_class(z.read(n), False)
                out[c.name] = c
    return out


def load_app(paths):
    out = {}
    for p in map(Path, paths):
        if p.is_dir():
            for f in p.rglob("*.class"):
                c = parse_class(f.read_bytes(), True)
                out[c.name] = c
        elif p.suffix == ".jar":
            with zipfile.ZipFile(p) as z:
                for n in z.namelist():
                    if n.endswith(".class"):
                        c = parse_class(z.read(n), True)
                        out[c.name] = c
    return out


def main():
    public, hidden, app = load_jar(sys.argv[1]), load_jar(sys.argv[2]), load_app(sys.argv[3:])

    def lookup(name):
        return app.get(name) or hidden.get(name) or public.get(name)

    def resolve(owner, kind, name, desc, seen=None):
        """Returns 'ok', 'hidden' or 'unknown' for the member as the VM would resolve it."""
        seen = seen if seen is not None else set()
        if owner is None or owner in seen:
            return "unknown"
        seen.add(owner)
        c = lookup(owner)
        if c is None:
            return "unknown"
        key = (kind, name, desc)
        if key in c.members:
            if owner in app:
                return "ok"
            pub = public.get(owner)
            return "ok" if pub is not None and key in pub.members else "hidden"
        results = [resolve(c.super, kind, name, desc, seen)]
        results += [resolve(i, kind, name, desc, seen) for i in c.interfaces]
        if "ok" in results:
            return "ok"
        return "hidden" if "hidden" in results else "unknown"

    findings = defaultdict(set)
    for c in app.values():
        for owner, kind, name, desc in c.refs:
            if owner.startswith("[") or owner.startswith("java/lang/invoke/"):
                continue  # invokedynamic helpers; D8 desugars them
            if resolve(owner, kind, name, desc) == "hidden":
                findings[f"{owner}.{name}{desc if kind == 'm' else ''}"].add(c.name)
        for ref in c.class_refs:
            if ref.startswith(("[", "java/lang/invoke/")) or ref in app or ref in public:
                continue
            if ref in hidden:
                findings[f"class {ref}"].add(c.name)

    for member in sorted(findings):
        users = sorted(findings[member])
        print(f"{member}\n    used by: {', '.join(users[:6])}{' …' if len(users) > 6 else ''}")
    print(f"\n{len(findings)} hidden API references in {len(app)} classes", file=sys.stderr)


if __name__ == "__main__":
    main()

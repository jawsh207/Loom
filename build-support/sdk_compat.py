#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Lists framework resources (@android:color/..., ?android:attr/..., android.R.x.y) that
Folio's sources use but an older Android version doesn't have. Such references crash on that
version (for example "Can't convert value at index 0 to color").

usage: sdk_compat.py OLD_ANDROID_JAR NEW_ANDROID_JAR SOURCE_DIR..."""

import re
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from hidden_api_scan import parse_class  # noqa: E402

RES_REF = re.compile(r"[@?]android:(?:(\w+)/)?([\w.]+)")
CODE_REF = re.compile(r"\bandroid\.R\.(\w+)\.(\w+)")


def framework_resources(jar):
    out = defaultdict(set)
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            m = re.fullmatch(r"android/R\$(\w+)\.class", n)
            if m:
                c = parse_class(z.read(n), False)
                out[m.group(1)] = {name for kind, name, _ in c.members if kind == "f"}
    return out


def main():
    old, new = framework_resources(sys.argv[1]), framework_resources(sys.argv[2])
    used = defaultdict(set)
    for root in sys.argv[3:]:
        for f in Path(root).rglob("*"):
            if f.suffix not in (".xml", ".java", ".kt") or "/build/" in str(f):
                continue
            text = f.read_text(errors="replace")
            if f.suffix == ".xml":
                for t, name in RES_REF.findall(text):
                    t = t or "attr"  # ?android:foo is an attribute
                    used[(t, name.replace(".", "_"))].add(str(f))
            for t, name in CODE_REF.findall(text):
                used[(t, name)].add(str(f))
    missing = sorted(k for k in used if k[1] in new.get(k[0], ()) and k[1] not in old.get(k[0], ()))
    for t, name in missing:
        files = sorted(used[(t, name)])
        print(f"{t}/{name}  ({len(files)} files) e.g. {files[0]}")
    print(f"\n{len(missing)} resources are newer than the old SDK", file=sys.stderr)


if __name__ == "__main__":
    main()

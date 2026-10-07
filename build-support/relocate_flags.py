#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Points vendored sources at Folio's copies of framework feature-flag classes.

Framework flag classes (android.security.Flags, com.android.window.flags.Flags, ...)
are hidden parts of the OS. A same-named copy inside the APK would be ignored at
runtime in favour of the framework's, so gen_flags.py generates them under
com.android.launcher3.folio.platformflags and this script rewrites the references.
Run it after updating the vendored sources; it is idempotent.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_flags import RELOCATED, RELOCATE_PREFIX, ROOT, _source_files  # noqa: E402


def relocate(text):
    changed = text
    for pkg, cls in RELOCATED.items():
        old = pkg + ".Flags"
        new = RELOCATE_PREFIX + "." + cls
        imported = re.search(r"^import\s+%s;?\s*$" % re.escape(old), changed, re.M)
        changed = re.sub(r"(?<![\w.])%s\b" % re.escape(old), new, changed)
        if imported:
            # The file imported the framework class as "Flags"; rename those uses.
            changed = re.sub(r"(?<![\w.])Flags\.", cls + ".", changed)
    return changed


def main():
    n = 0
    for path in _source_files():
        text = open(path, errors="ignore").read()
        new = relocate(text)
        if new != text:
            open(path, "w").write(new)
            print("relocated framework flags in", os.path.relpath(path, ROOT))
            n += 1
    print("%d files changed" % n)


if __name__ == "__main__":
    main()

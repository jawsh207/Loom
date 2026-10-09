#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Release notes from CHANGELOG.md.

  release_notes.py notes CHANGELOG.md PREVIOUS_TAG   print the recap for the next release
  release_notes.py file CHANGELOG.md VERSION DATE    move "Unreleased" under VERSION

The recap is the changelog's "## Unreleased" section. If that's empty, it falls back to the
subjects of the commits since PREVIOUS_TAG, leaving out test, CI and README-only commits."""

import re
import subprocess
import sys

HEADING = "## Unreleased"


def split(text):
    """(before, unreleased body, after) of the changelog."""
    start = text.find(HEADING)
    if start < 0:
        return text, "", ""
    body_start = text.find("\n", start) + 1
    m = re.compile(r"^## ", re.M).search(text, body_start)
    end = m.start() if m else len(text)
    return text[:start], text[body_start:end], text[end:]


def commit_recap(previous_tag):
    rng = f"{previous_tag}..HEAD" if previous_tag else "HEAD"
    subjects = subprocess.run(["git", "log", "--no-merges", "--format=%s", rng],
                              capture_output=True, text=True).stdout.splitlines()
    skip = re.compile(r"^(devtest\w*|ci\b|readme\b|tests?\b|workflows?\b|changelog\b)", re.I)
    return "\n".join(f"- {s}" for s in subjects if s.strip() and not skip.match(s))


def main():
    mode, path = sys.argv[1], sys.argv[2]
    text = open(path, encoding="utf-8").read()
    before, body, after = split(text)
    if mode == "notes":
        recap = body.strip() or commit_recap(sys.argv[3] if len(sys.argv) > 3 else "")
        print(recap or "Maintenance release.")
    elif mode == "file":
        version, date = sys.argv[3], sys.argv[4]
        if not body.strip():
            return
        new = f"{before}{HEADING}\n\n## {version} ({date})\n\n{body.strip()}\n\n{after}"
        open(path, "w", encoding="utf-8").write(new)
    else:
        sys.exit(f"unknown mode {mode}")


if __name__ == "__main__":
    main()

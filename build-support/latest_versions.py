#!/usr/bin/env python3
# Copyright (C) 2026 The Folio Authors
# SPDX-License-Identifier: Apache-2.0
"""Prints the newest stable versions of Folio's build tools as Gradle -P arguments.

Used by CI (`-Platest=true` builds) to find versions to pin in gradle.properties.
"""
import re
import sys
import urllib.request

GOOGLE = "https://dl.google.com/dl/android/maven2"
CENTRAL = "https://repo1.maven.org/maven2"
PORTAL = "https://plugins.gradle.org/m2"


def versions(repo, group, artifact):
    url = "%s/%s/%s/maven-metadata.xml" % (repo, group.replace(".", "/"), artifact)
    xml = urllib.request.urlopen(url, timeout=60).read().decode()
    return re.findall(r"<version>([^<]+)</version>", xml)


def key(v):
    return [int(p) if p.isdigit() else p for p in re.split(r"[.\-]", v)]


def stable(vs):
    return [v for v in vs if re.fullmatch(r"[0-9]+(\.[0-9]+)*", v)]


def newest(vs):
    return max(vs, key=key)


def main():
    agp = newest(stable(versions(GOOGLE, "com.android.tools.build", "gradle")))
    kotlin = newest(stable(versions(CENTRAL, "org.jetbrains.kotlin", "kotlin-gradle-plugin")))
    ksp_all = versions(CENTRAL, "com.google.devtools.ksp", "com.google.devtools.ksp.gradle.plugin")
    # KSP 2.3+ is released independently of Kotlin; older releases are "<kotlin>-<ksp>".
    ksp_standalone = stable(ksp_all)
    ksp_coupled = [v for v in ksp_all if v.startswith(kotlin + "-") and re.fullmatch(r"[0-9.]+-[0-9.]+", v)]
    ksp = newest(ksp_standalone) if ksp_standalone else newest(ksp_coupled)
    protobuf_plugin = newest(stable(versions(CENTRAL, "com.google.protobuf", "protobuf-gradle-plugin")))
    protobuf = newest(stable(versions(CENTRAL, "com.google.protobuf", "protoc")))
    args = {
        "folio.agp": agp,
        "folio.kotlin": kotlin,
        "folio.ksp": ksp,
        "folio.protobufPlugin": protobuf_plugin,
        "folio.protobuf": protobuf,
    }
    print(" ".join("-P%s=%s" % kv for kv in args.items()))
    for k, v in args.items():
        print("%s=%s" % (k, v), file=sys.stderr)


if __name__ == "__main__":
    main()

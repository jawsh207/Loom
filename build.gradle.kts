/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.android.application") apply false
    id("com.android.library") apply false
    id("com.android.legacy-kapt") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("org.jetbrains.kotlin.plugin.parcelize") apply false
    id("com.google.devtools.ksp") apply false
}

// Shared settings, read by the module build files.
// compileSdk is "major.minor" (Android 17 SDKs are 37.0, 37.1, 37.2).
val compileSdkParts = providers.gradleProperty("folio.compileSdk").get().split(".")
extra["folioCompileSdk"] = compileSdkParts[0].toInt()
extra["folioCompileSdkMinor"] = compileSdkParts.getOrElse(1) { "0" }.toInt()
extra["folioMinSdk"] = 31
extra["folioProtobuf"] = providers.gradleProperty("folio.protobuf").get()

// aconfig flag values (GrapheneOS release config), see build-support/gen_flags.py.
val flagValues: Map<String, Boolean> = rootProject.file("build-support/flag_values.txt")
    .readLines()
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .associate { it.substringBefore("=") to (it.substringAfter("=") == "true") }

/**
 * Vendored AOSP manifests need a few changes for AGP:
 * - no package attribute or <uses-sdk> (AGP 9 uses namespace and minSdk/targetSdk instead);
 * - elements gated with android:featureFlag are kept or dropped by the flag's release value,
 *   which Soong does through aapt2 --feature-flags.
 * Returns the adjusted copy, kept in the module's build directory.
 */
val vendoredManifest: (Project, String) -> File = { project, path ->
    val text = rootProject.file(path).readText()
        .replace(Regex("""\s+package="[^"]*""""), "")
        .replace(Regex("""<uses-sdk[^>]*/>"""), "")
        .replace(Regex("""<[a-z\-]+\b[^<>]*?\s+android:featureFlag="(!?)([\w.]+)"[^<>]*?/>""")) { m ->
            val enabled = flagValues[m.groupValues[2]] ?: false
            val keep = if (m.groupValues[1] == "!") !enabled else enabled
            if (keep) m.value.replace(Regex("""\s+android:featureFlag="[^"]*""""), "") else ""
        }
    val out = project.layout.buildDirectory.file("vendored/AndroidManifest.xml").get().asFile
    out.parentFile.mkdirs()
    if (!out.exists() || out.readText() != text) out.writeText(text)
    out
}
extra["vendoredManifest"] = vendoredManifest


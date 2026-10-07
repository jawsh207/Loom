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

/**
 * Vendored AOSP manifests carry a package attribute and <uses-sdk>, which AGP 9 rejects in
 * favour of `namespace` and minSdk/targetSdk. This returns a copy without them, kept in the
 * module's build directory.
 */
val vendoredManifest: (Project, String) -> File = { project, path ->
    val text = rootProject.file(path).readText()
        .replace(Regex("""\s+package="[^"]*""""), "")
        .replace(Regex("""<uses-sdk[^>]*/>"""), "")
    val out = project.layout.buildDirectory.file("vendored/AndroidManifest.xml").get().asFile
    out.parentFile.mkdirs()
    if (!out.exists() || out.readText() != text) out.writeText(text)
    out
}
extra["vendoredManifest"] = vendoredManifest


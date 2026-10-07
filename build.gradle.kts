/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.android.application") apply false
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.kapt") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("org.jetbrains.kotlin.plugin.parcelize") apply false
    id("com.google.devtools.ksp") apply false
    id("com.google.protobuf") apply false
}

// Shared settings, read by the module build files.
extra["folioCompileSdk"] = providers.gradleProperty("folio.compileSdk").get().toInt()
extra["folioMinSdk"] = 31
extra["folioProtobuf"] = providers.gradleProperty("folio.protobuf").get()

/**
 * Vendored AOSP manifests still carry a package attribute, which AGP rejects in favour of
 * `namespace`. This returns a copy without it, kept in the module's build directory.
 */
val vendoredManifest: (Project, String) -> File = { project, path ->
    val text = rootProject.file(path).readText()
        .replace(Regex("""\s+package="[^"]*""""), "")
    val out = project.layout.buildDirectory.file("vendored/AndroidManifest.xml").get().asFile
    out.parentFile.mkdirs()
    if (!out.exists() || out.readText() != text) out.writeText(text)
    out
}
extra["vendoredManifest"] = vendoredManifest

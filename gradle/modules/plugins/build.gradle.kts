/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// SystemUI plugin interfaces (PluginCoreLib, SystemUILogCoreLib) and Launcher3's plugins.

plugins {
    id("com.android.library")
}

val folioCompileSdk = rootProject.extra["folioCompileSdk"] as Int
val folioMinSdk = rootProject.extra["folioMinSdk"] as Int
@Suppress("UNCHECKED_CAST")
val vendoredManifest = rootProject.extra["vendoredManifest"] as (Project, String) -> File

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "com.android.systemui.plugins"
    compileSdk = folioCompileSdk
    defaultConfig { minSdk = folioMinSdk }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint { abortOnError = false }
}

// Sources come from the vendored trees (through the new DSL type; AGP 9's
// `android.sourceSets` accessor still exposes the removed legacy type).
extensions.getByType<com.android.build.api.dsl.LibraryExtension>().sourceSets.getByName("main") {
    java.setSrcDirs(listOf<Any>(rootProject.file("frameworks/plugin_core/src"), rootProject.file("frameworks/plugin_core/annotations/src"), rootProject.file("frameworks/log_core/src"), rootProject.file("launcher3/src_plugins")))
    kotlin.setSrcDirs(listOf<Any>(rootProject.file("frameworks/plugin_core/src"), rootProject.file("frameworks/plugin_core/annotations/src"), rootProject.file("frameworks/log_core/src"), rootProject.file("launcher3/src_plugins")))
    res.setSrcDirs(listOf<Any>())
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    api("com.google.errorprone:error_prone_annotations:+")
    api("androidx.annotation:annotation:+")
}

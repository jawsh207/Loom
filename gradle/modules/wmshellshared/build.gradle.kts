/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// The two WM Shell classes Launcher3 uses (from WindowManager-Shell-shared).

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

val folioCompileSdk = rootProject.extra["folioCompileSdk"] as Int
val folioMinSdk = rootProject.extra["folioMinSdk"] as Int
@Suppress("UNCHECKED_CAST")
val vendoredManifest = rootProject.extra["vendoredManifest"] as (Project, String) -> File

android {
    namespace = "com.android.wm.shell.shared"
    compileSdk = folioCompileSdk
    defaultConfig { minSdk = folioMinSdk }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint { abortOnError = false }
    sourceSets.getByName("main") {
        java.setSrcDirs(listOf<Any>(rootProject.file("frameworks/wm_shell_shared/src")))
        kotlin.setSrcDirs(listOf<Any>(rootProject.file("frameworks/wm_shell_shared/src")))
        res.setSrcDirs(listOf<Any>())
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    api(project(":flags"))
}

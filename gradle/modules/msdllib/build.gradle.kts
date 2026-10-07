/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val folioCompileSdk: Int by rootProject.extra
val folioMinSdk: Int by rootProject.extra
@Suppress("UNCHECKED_CAST")
val vendoredManifest = rootProject.extra["vendoredManifest"] as (Project, String) -> File

android {
    namespace = "com.google.android.msdl"
    compileSdk = folioCompileSdk
    defaultConfig { minSdk = folioMinSdk }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint { abortOnError = false }
    sourceSets.getByName("main") {
        java.setSrcDirs(listOf(rootProject.file("systemui/msdllib/src")))
        res.setSrcDirs(listOf<Any>())
        manifest.srcFile(vendoredManifest(project, "systemui/msdllib/AndroidManifest.xml"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:+")
    api("androidx.annotation:annotation:+")
}

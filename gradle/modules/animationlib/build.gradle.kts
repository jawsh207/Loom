/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.android.library")
}

val folioCompileSdk = rootProject.extra["folioCompileSdk"] as Int
val folioCompileSdkMinor = rootProject.extra["folioCompileSdkMinor"] as Int
val folioMinSdk = rootProject.extra["folioMinSdk"] as Int
@Suppress("UNCHECKED_CAST")
val vendoredManifest = rootProject.extra["vendoredManifest"] as (Project, String) -> File

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "com.android.app.animation"
    compileSdk {
        version = release(folioCompileSdk) { minorApiLevel = folioCompileSdkMinor }
    }
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
    java.setSrcDirs(listOf<Any>(rootProject.file("systemui/animationlib/src")))
    kotlin.setSrcDirs(listOf<Any>(rootProject.file("systemui/animationlib/src")))
    res.setSrcDirs(listOf<Any>(rootProject.file("systemui/animationlib/res")))
    manifest.srcFile(vendoredManifest(project, "systemui/animationlib/AndroidManifest.xml"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    api("androidx.core:core-animation:+")
    api("androidx.core:core-ktx:+")
    api("androidx.annotation:annotation:+")
}

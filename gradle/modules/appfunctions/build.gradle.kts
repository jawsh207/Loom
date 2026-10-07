/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
}

val folioCompileSdk = rootProject.extra["folioCompileSdk"] as Int
val folioCompileSdkMinor = rootProject.extra["folioCompileSdkMinor"] as Int
val folioMinSdk = rootProject.extra["folioMinSdk"] as Int
@Suppress("UNCHECKED_CAST")
val vendoredManifest = rootProject.extra["vendoredManifest"] as (Project, String) -> File

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "com.android.launcher3.appfunctions"
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
    java.setSrcDirs(listOf<Any>(rootProject.file("launcher3/modules/appfunctions/src")))
    kotlin.setSrcDirs(listOf<Any>(rootProject.file("launcher3/modules/appfunctions/src")))
    res.setSrcDirs(listOf<Any>())
    manifest.srcFile(vendoredManifest(project, "launcher3/modules/appfunctions/AndroidManifest.xml"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    api("androidx.annotation:annotation:+")
    api("androidx.appfunctions:appfunctions:+")
    api("androidx.appfunctions:appfunctions-service:+")
    api("androidx.appcompat:appcompat:+")
    ksp("androidx.appfunctions:appfunctions-compiler:+")
}

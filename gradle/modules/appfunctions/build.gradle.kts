/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val folioCompileSdk: Int by rootProject.extra
val folioMinSdk: Int by rootProject.extra
@Suppress("UNCHECKED_CAST")
val vendoredManifest = rootProject.extra["vendoredManifest"] as (Project, String) -> File

android {
    namespace = "com.android.launcher3.appfunctions"
    compileSdk = folioCompileSdk
    defaultConfig { minSdk = folioMinSdk }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint { abortOnError = false }
    sourceSets.getByName("main") {
        java.setSrcDirs(listOf(rootProject.file("launcher3/modules/appfunctions/src")))
        res.setSrcDirs(listOf<Any>())
        manifest.srcFile(vendoredManifest(project, "launcher3/modules/appfunctions/AndroidManifest.xml"))
    }
}

kotlin {
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

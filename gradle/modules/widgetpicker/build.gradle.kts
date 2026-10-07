/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

val folioCompileSdk: Int by rootProject.extra
val folioMinSdk: Int by rootProject.extra
@Suppress("UNCHECKED_CAST")
val vendoredManifest = rootProject.extra["vendoredManifest"] as (Project, String) -> File

android {
    namespace = "com.android.launcher3.widgetpicker"
    compileSdk = folioCompileSdk
    defaultConfig { minSdk = folioMinSdk }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint { abortOnError = false }
    buildFeatures { compose = true }
    sourceSets.getByName("main") {
        java.setSrcDirs(listOf(rootProject.file("launcher3/modules/widgetpicker/src")))
        res.setSrcDirs(listOf<Any>(rootProject.file("launcher3/modules/widgetpicker/res")))
        manifest.srcFile(vendoredManifest(project, "launcher3/modules/widgetpicker/AndroidManifest.xml"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    api("com.google.dagger:dagger:+")
    kapt("com.google.dagger:dagger-compiler:+")
    api("javax.inject:javax.inject:1")
    api("androidx.core:core-ktx:+")
    api("androidx.annotation:annotation:+")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:+")
    api("androidx.compose.runtime:runtime:+")
    api("androidx.compose.ui:ui:+")
    api("androidx.compose.ui:ui-tooling-preview:+")
    api("androidx.compose.ui:ui-tooling:+")
    api("androidx.compose.foundation:foundation:+")
    api("androidx.compose.foundation:foundation-layout:+")
    api("androidx.compose.material3:material3:+")
    api("androidx.compose.material3:material3-window-size-class:+")
    api("androidx.compose.material:material-icons-extended:+")
    api("androidx.activity:activity-compose:+")
    api("androidx.window:window:+")
}

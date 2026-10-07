/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Launcher3's resources and shared manifest, plus the libraries they need (Soong: Launcher3ResLib).
// Kept apart from :app so the resources get Launcher3's R package, com.android.launcher3.
plugins {
    id("com.android.library")
}

val folioCompileSdk = rootProject.extra["folioCompileSdk"] as Int
val folioCompileSdkMinor = rootProject.extra["folioCompileSdkMinor"] as Int
val folioMinSdk = rootProject.extra["folioMinSdk"] as Int
@Suppress("UNCHECKED_CAST")
val vendoredManifest = rootProject.extra["vendoredManifest"] as (Project, String) -> File

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "com.android.launcher3"
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
    java.setSrcDirs(listOf<Any>())
    kotlin.setSrcDirs(listOf<Any>())
    res.setSrcDirs(listOf(rootProject.file("launcher3/res")))
    manifest.srcFile(vendoredManifest(project, "launcher3/AndroidManifest-common.xml"))
}

dependencies {
    api(project(":dynamiccolors"))
    api(project(":iconloaderlib"))
    api(project(":animationlib"))
    api(project(":msdllib"))
    api(project(":plugins"))
    api(project(":wmshellshared"))
    api(project(":widgetpicker"))
    api(project(":appfunctions"))
    api(project(":flags"))

    api("androidx.annotation:annotation:+")
    api("androidx.core:core-ktx:+")
    api("androidx.collection:collection:+")
    api("androidx.constraintlayout:constraintlayout:+")
    api("androidx.recyclerview:recyclerview:+")
    api("androidx.dynamicanimation:dynamicanimation:+")
    api("androidx.fragment:fragment-ktx:+")
    api("androidx.preference:preference:+")
    api("androidx.slice:slice-core:+")
    api("androidx.slice:slice-view:+")
    api("androidx.cardview:cardview:+")
    api("androidx.window:window:+")
    api("androidx.graphics:graphics-shapes:+")
    api("androidx.savedstate:savedstate:+")
    api("androidx.activity:activity-compose:+")
    api("androidx.navigation:navigation-compose:+")
    api("androidx.lifecycle:lifecycle-common-java8:+")
    api("androidx.lifecycle:lifecycle-extensions:+")
    api("androidx.lifecycle:lifecycle-runtime-ktx:+")
    api("androidx.lifecycle:lifecycle-runtime-compose:+")
    api("androidx.lifecycle:lifecycle-viewmodel-compose:+")
    api("com.google.android.material:material:+")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:+")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:+")
    api("androidx.compose.runtime:runtime:+")
    api("androidx.compose.material3:material3:+")
    api("androidx.compose.ui:ui-tooling-preview:+")
    api("androidx.compose.ui:ui-tooling:+")
}

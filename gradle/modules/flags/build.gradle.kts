/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.android.library")
}

val folioCompileSdk = rootProject.extra["folioCompileSdk"] as Int
val folioMinSdk = rootProject.extra["folioMinSdk"] as Int
@Suppress("UNCHECKED_CAST")
val vendoredManifest = rootProject.extra["vendoredManifest"] as (Project, String) -> File

android {
    namespace = "com.android.folio.flags"
    compileSdk = folioCompileSdk
    defaultConfig { minSdk = folioMinSdk }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint { abortOnError = false }
    sourceSets.getByName("main") {
        java.setSrcDirs(listOf<Any>(rootProject.file("flags/src")))
        kotlin.setSrcDirs(listOf<Any>(rootProject.file("flags/src")))
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
    compileOnly("androidx.annotation:annotation:+")
}

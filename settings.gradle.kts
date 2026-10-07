/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

pluginManagement {
    // Tool versions live in gradle.properties; CI can override them with -P.
    fun v(name: String) = providers.gradleProperty(name).get()
    plugins {
        id("com.android.application") version v("folio.agp")
        id("com.android.library") version v("folio.agp")
        id("com.android.legacy-kapt") version v("folio.agp")
        // Not applied to modules (AGP 9 compiles Kotlin itself); declared so AGP uses this Kotlin.
        id("org.jetbrains.kotlin.android") version v("folio.kotlin")
        id("org.jetbrains.kotlin.plugin.compose") version v("folio.kotlin")
        id("org.jetbrains.kotlin.plugin.parcelize") version v("folio.kotlin")
        id("com.google.devtools.ksp") version v("folio.ksp")
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Folio"

// Each module's build file lives in gradle/modules/<name>; its sources stay in the vendored
// directories (launcher3/, systemui/, frameworks/, flags/) so upstream syncs stay simple.
listOf(
    "app",
    "launcher3-res",
    "flags",
    "usertypelib",
    "iconloaderlib",
    "animationlib",
    "msdllib",
    "mechanics",
    "dynamiccolors",
    "plugins",
    "wmshellshared",
    "widgetpicker",
    "appfunctions",
).forEach { name ->
    include(":$name")
    project(":$name").projectDir = file("gradle/modules/$name")
}

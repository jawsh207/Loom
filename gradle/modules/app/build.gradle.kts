/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Folio app: the Launcher3 sources (Soong module "Folio" in launcher3's Android.bp).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.protobuf")
}

val folioCompileSdk: Int by rootProject.extra
val folioMinSdk: Int by rootProject.extra
val folioProtobuf: String by rootProject.extra
@Suppress("UNCHECKED_CAST")
val vendoredManifest = rootProject.extra["vendoredManifest"] as (Project, String) -> File

android {
    namespace = "app.folio.launcher"
    compileSdk = folioCompileSdk

    defaultConfig {
        applicationId = "app.folio.launcher"
        minSdk = folioMinSdk
        targetSdk = folioCompileSdk
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = false // Launcher3 has its own BuildConfig (src/main/java).
    }

    buildTypes {
        getByName("release") {
            // Like the AOSP Launcher3 build: no shrinking. Signed with the debug key until a
            // release key is configured (see README).
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets.getByName("main") {
        java.setSrcDirs(
            listOf(
                "src/main/java",
                rootProject.file("launcher3/src"),
                rootProject.file("launcher3/src_no_quickstep"),
                rootProject.file("launcher3/shared/src"),
                rootProject.file("launcher3/dagger/src"),
                rootProject.file("launcher3/modules/concurrent/src"),
            )
        )
        res.setSrcDirs(listOf<Any>())
        manifest.srcFile(vendoredManifest(project, "launcher3/folio/AndroidManifest.xml"))
        (this as ExtensionAware).extensions.getByName<SourceDirectorySet>("proto").apply {
            srcDir(rootProject.file("launcher3/protos"))
            srcDir(rootProject.file("launcher3/protos_quickstep"))
        }
    }

    packaging {
        resources.excludes += listOf("META-INF/*.version", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    lint { abortOnError = false }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

kapt {
    correctErrorTypes = true
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:$folioProtobuf" }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") { option("lite") }
            }
        }
    }
}

dependencies {
    implementation(project(":launcher3-res"))

    implementation("com.google.protobuf:protobuf-javalite:$folioProtobuf")
    implementation("com.google.dagger:dagger:+")
    kapt("com.google.dagger:dagger-compiler:+")
    implementation("javax.inject:javax.inject:1")
    implementation("com.google.guava:guava:33.4.8-android")
}

/*
 * Copyright (C) 2026 The Folio Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import javax.inject.Inject
import org.gradle.process.ExecOperations

// Folio app: the Launcher3 sources (Soong module "Folio" in launcher3's Android.bp).
plugins {
    id("com.android.application")
    id("com.android.legacy-kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

val folioCompileSdk = rootProject.extra["folioCompileSdk"] as Int
val folioCompileSdkMinor = rootProject.extra["folioCompileSdkMinor"] as Int
val folioMinSdk = rootProject.extra["folioMinSdk"] as Int
val folioProtobuf = rootProject.extra["folioProtobuf"] as String
@Suppress("UNCHECKED_CAST")
val vendoredManifest = rootProject.extra["vendoredManifest"] as (Project, String) -> File

val launcherSources = listOf<Any>(
    "src/main/java",
    rootProject.file("launcher3/src"),
    rootProject.file("launcher3/src_no_quickstep"),
    rootProject.file("launcher3/shared/src"),
    rootProject.file("launcher3/dagger/src"),
    rootProject.file("launcher3/modules/concurrent/src"),
)

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    namespace = "app.folio.launcher"
    compileSdk {
        version = release(folioCompileSdk) { minorApiLevel = folioCompileSdkMinor }
    }

    defaultConfig {
        applicationId = "app.folio.launcher"
        minSdk = folioMinSdk
        targetSdk = folioCompileSdk
        // CI passes its run number so every build can install over the previous one.
        versionCode = System.getenv("FOLIO_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("FOLIO_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "0.2-dev"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = false // Launcher3 has its own BuildConfig (src/main/java).
    }

    // Release signing key from the environment (CI secrets, see README); without one the
    // release APK is signed with the debug key, which differs between machines.
    val keystore = System.getenv("FOLIO_KEYSTORE")?.takeIf { it.isNotBlank() }
    if (keystore != null) {
        signingConfigs.create("folio") {
            storeFile = file(keystore)
            storePassword = System.getenv("FOLIO_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("FOLIO_KEY_ALIAS")
            keyPassword = System.getenv("FOLIO_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            // Like the AOSP Launcher3 build: no shrinking.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystore != null) "folio" else "debug")
        }
    }

    packaging {
        resources.excludes += listOf("META-INF/*.version", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    lint { abortOnError = false }
}

// Sources come from the vendored trees (through the new DSL type; AGP 9's
// `android.sourceSets` accessor still exposes the removed legacy type).
extensions.getByType<com.android.build.api.dsl.ApplicationExtension>().sourceSets.getByName("main") {
    java.setSrcDirs(launcherSources)
    kotlin.setSrcDirs(launcherSources)
    res.setSrcDirs(listOf<Any>("res")) // Folio-only overrides; launcher res is in :launcher3-res.
    manifest.srcFile(vendoredManifest(project, "launcher3/folio/AndroidManifest.xml"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

// Launcher3's logging protos, compiled with protoc for protobuf-javalite (Soong:
// launcher_quickstep_log_protos_lite). Done with a plain task instead of the protobuf
// Gradle plugin, which doesn't support AGP 9's variant API.
val protoc: Configuration by configurations.creating
dependencies {
    val os = System.getProperty("os.name").lowercase()
    val arch = if (System.getProperty("os.arch").contains("aarch64")) "aarch_64" else "x86_64"
    val classifier = when {
        os.contains("mac") -> "osx-$arch"
        os.contains("win") -> "windows-$arch"
        else -> "linux-$arch"
    }
    protoc("com.google.protobuf:protoc:$folioProtobuf:$classifier@exe")
}

abstract class GenerateLiteProtos : DefaultTask() {
    @get:InputFiles abstract val protoc: ConfigurableFileCollection
    @get:InputFiles abstract val protoDirs: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val exe = protoc.singleFile.apply { setExecutable(true) }
        val protos = protoDirs.files.flatMap { dir -> dir.listFiles { f -> f.extension == "proto" }!!.toList() }
        exec.exec {
            commandLine(
                listOf(exe.absolutePath, "--java_out=lite:${out.absolutePath}") +
                    protoDirs.files.map { "--proto_path=${it.absolutePath}" } +
                    protos.map { it.absolutePath }
            )
        }
    }
}

val generateLauncherProtos = tasks.register<GenerateLiteProtos>("generateLauncherProtos") {
    protoc.from(configurations.getByName("protoc"))
    // protos_quickstep goes first so its launcher_atom_extension.proto replaces the stub one.
    protoDirs.from(rootProject.file("launcher3/protos_quickstep"), rootProject.file("launcher3/protos"))
    outputDir.set(layout.buildDirectory.dir("generated/source/launcherProtos"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(
            generateLauncherProtos, GenerateLiteProtos::outputDir
        )
    }
}

dependencies {
    implementation(project(":launcher3-res"))

    implementation("com.google.protobuf:protobuf-javalite:$folioProtobuf")
    implementation("com.google.dagger:dagger:+")
    kapt("com.google.dagger:dagger-compiler:+")
    implementation("javax.inject:javax.inject:1")
    implementation("com.google.guava:guava:33.4.8-android")
    // Feed panel: article extraction (a port of Firefox Reader View's Readability), which
    // also brings jsoup for parsing HTML. Apache-2.0.
    implementation("net.dankito.readability4j:readability4j:1.0.8")
    // @ThreadSafe and friends; normally supplied by the hidden-API android.jar.
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

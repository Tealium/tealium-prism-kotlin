package com.tealium.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

/**
 * Pins the Kotlin language/API version to 2.0 and the core Kotlin libraries (stdlib,
 * reflect, and test) to the `kotlinCoreLibs` catalog entry, so every module compiles
 * against the same Kotlin version regardless of which Kotlin Gradle Plugin version is
 * applied.
 *
 * The language/API version is intentionally a hardcoded literal, not derived from the
 * catalog: it's a consumer-facing compatibility floor, not a dependency version. Raising
 * it is a deliberate, separate decision, not a side effect of bumping `kotlinCoreLibs`.
 * KGP >= 2.2 is required (lint-api's transitive kotlin-compiler dependency is built at
 * metadata version 2.2). KGP is kept at 2.2.x by choice, since 2.3+ marks language/API
 * version 2.0 as deprecated in its accepted-values list, producing build warnings.
 */
fun Project.configureKotlinVersionDefaults() {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val kotlinVersion = libs.findVersion("kotlinCoreLibs").get().requiredVersion

    when (val extension = extensions.getByType<KotlinBaseExtension>()) {
        is KotlinAndroidExtension -> extension.applyVersionDefaults(kotlinVersion)
        is KotlinJvmExtension -> extension.applyVersionDefaults(kotlinVersion)
        else -> error("Unsupported Kotlin extension type: ${extension::class}")
    }
}

private fun <T> T.applyVersionDefaults(kotlinVersion: String)
        where T : KotlinBaseExtension,
              T : HasConfigurableKotlinCompilerOptions<KotlinJvmCompilerOptions> {
    coreLibrariesVersion = kotlinVersion
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

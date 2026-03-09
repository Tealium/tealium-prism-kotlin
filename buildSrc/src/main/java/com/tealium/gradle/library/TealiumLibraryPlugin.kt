package com.tealium.gradle.library

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.tealium.gradle.dokka.DokkaLibraryPlugin
import com.tealium.gradle.publish.TealiumPublishPlugin
import com.tealium.gradle.tests.JacocoCoverageType
import com.tealium.gradle.tests.JacocoVerifyType
import com.tealium.gradle.tests.TestType
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoReportBase
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import java.math.BigDecimal

class TealiumLibraryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            apply<LibraryPlugin>()
            apply<KotlinAndroidPluginWrapper>()
            apply<JacocoPlugin>()
            apply<DokkaLibraryPlugin>()
            apply<TealiumPublishPlugin>()

            configureJacocoPlugin()
            configureAndroidExtensions()
            configureDefaultDependencies()
        }
    }

    /**
     * Configures the default `android` block for the project.
     *
     * Defaults set are:
     *  - Jvm Toolchain: 1.8
     *  - buildTypes: default proguard files, and "release" build minify enabled
     *  - code coverage:
     */
    private fun Project.configureAndroidExtensions() {
        val android = extensions.getByType<LibraryExtension>()

        configureLanguageDefaults(android)
        configureDefaultBuildTypes(android)
        configureCodeCoverage(android)
    }

    /**
     * Sets JVM compatibility to 1.8, but sets tests to execute using Java 17 for Robolectric support.
     */
    private fun Project.configureLanguageDefaults(android: LibraryExtension) {
        with(android) {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
            tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java)
                .configureEach {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_1_8)
                    }
                }
            extensions.configure(KotlinAndroidProjectExtension::class.java) {
                jvmToolchain {
                    languageVersion.set(JavaLanguageVersion.of(17))
                }
            }
        }
    }

    private fun Project.configureDefaultBuildTypes(android: LibraryExtension) {
        with(android) {
            buildTypes {
                getByName("release") {
                    isMinifyEnabled = true
                    proguardFiles.addAll(
                        listOf(
                            getDefaultProguardFile("proguard-android.txt"),
                            file("proguard-rules.pro")
                        )
                    )
                }
                getByName("debug") {
                    isMinifyEnabled = false
                    enableUnitTestCoverage = true
                    enableAndroidTestCoverage = true
                }
            }
        }
    }

    /**
     * Sets default properties for the JacocoPlugin
     *  - Tool Version
     *  - class exclusions
     */
    private fun Project.configureJacocoPlugin() {
        val jacoco = extensions.getByType<JacocoPluginExtension>()
        jacoco.toolVersion = "0.8.7"

        tasks.withType(Test::class.java) {
            val jacocoTask = extensions.getByType(JacocoTaskExtension::class.java)
            jacocoTask.isIncludeNoLocationClasses = true
            jacocoTask.excludes = listOf("jdk.internal.*")
        }
    }

    /**
     * Sets up tasks for:
     *  - creating JaCoCo code coverage reports
     *  - verifying JaCoCo code coverage reports
     */
    private fun Project.configureCodeCoverage(android: LibraryExtension) {
        val jacocoConfig = getJacocoConfiguration()
        android.buildTypes.forEach { variant ->
            val variantName = variant.name.uppercaseFirstChar()

            configureTestCoverage(variantName, jacocoConfig)
            configureVerifyTestCoverage(variantName, jacocoConfig)
        }
    }

    private fun Project.configureTestCoverage(variant: String, config: JacocoConfig) {
        tasks.register(JacocoCoverageType.Default.taskName(variant), JacocoReport::class.java) {
            // Depend on unit tests and Android tests tasks
            dependsOn(TestType.values().map { it.taskName(variant) })

            group = "Reporting"
            description = "Run Unit Tests and compile test coverage reports."

            reports {
                xml.required.set(true)
                html.required.set(true)
            }

            setDirectoriesAndExecutionData(this, config, variant)
        }
    }

    private fun Project.configureVerifyTestCoverage(variant: String, config: JacocoConfig) {
        tasks.register(
            JacocoVerifyType.Default.taskName(variant),
            JacocoCoverageVerification::class.java
        ) {
            group = "Reporting"
            description = "Verify code coverage."

            dependsOn.add(JacocoCoverageType.Default.taskName(variant))

            setDirectoriesAndExecutionData(this, config, variant)

            violationRules {
                isFailOnViolation = true
                // 1
                rule {
                    enabled = true
                    element = "PACKAGE"
                    includes = listOf("com.tealium.*")
                    limit {
                        counter = "CLASS"
                        value = "MISSEDCOUNT"
                        maximum = BigDecimal.ONE
                    }
                }
                // 2
                rule {
                    element = "PACKAGE"
                    includes = listOf("com.tealium.*")
                    limit {
                        value = "COVEREDRATIO"
                        counter = "INSTRUCTION"
                        minimum = BigDecimal.valueOf(0.3)
                    }
                }
            }
        }
    }

    private fun Project.setDirectoriesAndExecutionData(
        jacoco: JacocoReportBase,
        config: JacocoConfig,
        variant: String
    ) {
        with(jacoco) {
            sourceDirectories.setFrom(config.sourceDirectory)
            classDirectories.setFrom(
                files(
                    fileTree(layout.buildDirectory.dir("intermediates/javac/${variant}")) {
                        exclude(config.exclusions)
                    },
                    fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/${variant}")) {
                        exclude(config.exclusions)
                    }
                ))
            executionData.setFrom(config.executionData)
        }
    }

    private fun Project.getJacocoConfiguration(): JacocoConfig {
        val exclusions = listOf(
            "**/R.class", "**/R$*.class", "**/BuildConfig.*",
            "**/Manifest*.*", "**/*Test*.*", "android/**/*.*"
        )

        val jacocoSourceDirectories = layout.projectDirectory.dir("src/main")
        val jacocoExecutionData = files(
            fileTree(layout.buildDirectory) {
                include(
                    listOf(
                        "**/*.exec",
                        "**/*.ec"
                    )
                )
            }
        )

        return JacocoConfig(exclusions, jacocoSourceDirectories, jacocoExecutionData)
    }

    private fun Project.configureDefaultDependencies() {
        dependencies.add("lintChecks", project(":lint"))
    }

    private class JacocoConfig(
        val exclusions: List<String>,
        val sourceDirectory: Directory,
        val executionData: FileCollection
    )
}

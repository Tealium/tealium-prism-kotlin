package com.tealium.gradle

import com.android.build.gradle.LibraryExtension
import com.tealium.gradle.git.GitHelper
import com.tealium.gradle.library.TealiumLibraryPlugin
import com.tealium.gradle.tests.JacocoCoverageType
import com.tealium.gradle.tests.JacocoVerifyType
import com.tealium.gradle.tests.TestType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.uppercaseFirstChar

class TealiumGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.configureCompressReportsTask()
        project.configureUpdatedModulesTask()

        project.gradle.projectsEvaluated {
            // Flag to enable/disable CI tasks to avoid constant git checks
            // note. will also require "GITHUB_BASE_REF" and "GITHUB_HEAD_REF" as environment vars or
            // project properties. which can be passed as cli params like so:
            // `./gradlew -PGITHUB_BASE_REF=main <task-name>` etc
            val ciEnabled = project.getPropertyOrEnvironmentVariable("CI_TASKS_ENABLED", "false")
                .toBoolean()

            if (ciEnabled) {
                project.configureCiTasks()
            }
        }
    }

    private fun Project.configureCompressReportsTask() {
        tasks.register<Zip>("compressReports") {
            group = "build"
            destinationDirectory.set(project.layout.projectDirectory.dir("reports"))
            from(project.rootDir) {
                include("*/build/reports/**")
                includeEmptyDirs = false
            }
            archiveFileName.set("reports.zip")
        }
        tasks.register<Zip>("compressOutputs") {
            group = "build"
            destinationDirectory.set(project.layout.projectDirectory.dir("reports"))
            from(project.rootDir) {
                include("*/build/outputs/**")
                includeEmptyDirs = false
            }
            archiveFileName.set("outputs.zip")
        }
    }

    private fun Project.configureUpdatedModulesTask() {
        tasks.register("updatedUnitTestModules", UpdatedModules::class.java) {
            testType.set(TestType.UnitTest)
        }
        tasks.register("updatedInstrumentedTestModules", UpdatedModules::class.java) {
            testType.set(TestType.InstrumentedTest)
        }
    }

    private fun Project.configureCiTasks() {
        val tealiumLibraryProjects =
            project.subprojects.filter { project -> project.plugins.hasPlugin(TealiumLibraryPlugin::class.java) }

        val modifiedProjects = tealiumLibraryProjects.filter { GitHelper.isModified(it) }

        logger.lifecycle("Configuring CI Tasks for ${modifiedProjects.joinToString(", ") { it.name }}")

        modifiedProjects.mapNotNull { project ->
            project.extensions.findByType(LibraryExtension::class.java)
        }.forEach { android ->
            android.libraryVariants
                .map { variant -> variant.name.uppercaseFirstChar() }
                .forEach { variant ->
                    project.configureAssembleModifiedTasks(variant, modifiedProjects)
                    project.configureAggregateTestTasks(variant, modifiedProjects)
                    project.configureAggregateCoverageTasks(variant, modifiedProjects)
                }
        }
    }

    /**
     * Configures additional aggregate tasks to
     *  - run Unit Tests for all modified projects
     *  - run Instrumented Tests for all modified projects
     */
    private fun Project.configureAssembleModifiedTasks(
        variant: String,
        modifiedProjects: List<Project>
    ) {
        tasks.register("assembleModified$variant") {
            group = "Build"
            description =
                "Build modified projects"
            dependsOn.addAll(modifiedProjects.taskNames("assemble$variant"))
            dependsOn.addAll(modifiedProjects.taskNames("assemble${variant}AndroidTest"))
        }
    }

    /**
     * Configures additional aggregate tasks to
     *  - run Unit Tests for all modified projects
     *  - run Instrumented Tests for all modified projects
     */
    private fun Project.configureAggregateTestTasks(
        variant: String,
        modifiedProjects: List<Project>
    ) {
        listOf(TestType.UnitTest, TestType.InstrumentedTest).forEach { testType ->
            tasks.register(testType.modifiedTaskName(variant)) {
                group = "Verification"
                description =
                    "Runs tests for all modules that are modified according to version control"
                dependsOn.addAll(modifiedProjects.taskNames(testType.taskName(variant)))
            }
        }
    }

    /**
     * Configures additional aggregate tasks to
     *  - create JaCoCo reports for all modified projects
     *  - verify JaCoCo reports for all modified projects
     */
    private fun Project.configureAggregateCoverageTasks(
        variant: String,
        modifiedProjects: List<Project>
    ) {
        // tasks to run coverage and verification for only projects which have a "modified" task
        val modifiedTaskName = JacocoCoverageType.ModifiedOnly.taskName(variant)
        project.tasks.register(modifiedTaskName) {
            dependsOn.addAll(modifiedProjects.taskNames(JacocoCoverageType.Default.taskName(variant)))
        }
        project.tasks.register(JacocoVerifyType.ModifiedOnly.taskName(variant)) {
            dependsOn.add(modifiedTaskName)
            dependsOn.addAll(modifiedProjects.taskNames(JacocoVerifyType.Default.taskName(variant)))
        }
    }

    private fun List<Project>.taskNames(task: String) =
        map { project -> project.taskName(task) }

    private fun Project.taskName(task: String): String =
        "${name}:$task"
}
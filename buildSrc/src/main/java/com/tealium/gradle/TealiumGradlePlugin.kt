package com.tealium.gradle

import com.android.build.gradle.LibraryExtension
import com.tealium.gradle.library.TealiumLibraryPlugin
import com.tealium.gradle.tests.JacocoCoverageType
import com.tealium.gradle.tests.JacocoVerifyType
import com.tealium.gradle.tests.TestType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.dokka.gradle.DokkaPlugin

class TealiumGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(DokkaPlugin::class)
        project.configureCompressReportsTask()
        project.configureUpdatedModulesTask()

        project.gradle.projectsEvaluated {
            val modifiedProjects = project.getPropertyOrEnvironmentVariable("MODIFIED_PROJECTS", "")
                .split(",")

            project.configureCiTasks(modifiedProjects)
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
        val incoming = project.getPropertyOrEnvironmentVariable("GITHUB_HEAD_REF", "")
        val base = project.getPropertyOrEnvironmentVariable("GITHUB_BASE_REF", "")

        tasks.register("updatedUnitTestModules", UpdatedModules::class.java) {
            testType.set(TestType.UnitTest)
            incomingBranch.set(incoming)
            baseBranch.set(base)
        }
        tasks.register("updatedInstrumentedTestModules", UpdatedModules::class.java) {
            testType.set(TestType.InstrumentedTest)
            incomingBranch.set(incoming)
            baseBranch.set(base)
        }
    }

    private fun Project.configureCiTasks(modifiedProjectNames: List<String>) {
        val tealiumLibraryProjects = project.subprojects.filter { project ->
            project.plugins.hasPlugin(TealiumLibraryPlugin::class.java)
        }

        getAllModifiedVariants(tealiumLibraryProjects)
            .mapKeys { (variant, _) -> variant.uppercaseFirstChar() }
            .forEach { (variant, projects) ->
                val modifiedProjects =
                    projects.filter { project -> modifiedProjectNames.contains(project.name) }
                project.configureAssembleModifiedTasks(variant, modifiedProjects)
                project.configureAggregateTestTasks(variant, modifiedProjects)
                project.configureAggregateCoverageTasks(variant, modifiedProjects)
            }
    }

    /**
     * Gets all the unique Android Library Variant names, and associates them with the list of projects
     * that have that variant. In most cases this will simply be a map similar to the following:
     * ```json
     * {
     *   "debug": [Project1, Project2 ...],
     *   "release": [Project1, Project2 ...]
     * }
     * ```
     *
     * Projects that do not have the Android [LibraryExtension] are ignored.
     */
    private fun getAllModifiedVariants(tealiumLibraryProjects: List<Project>): Map<String, List<Project>> =
        tealiumLibraryProjects.mapNotNull { project ->
            project.extensions.findByType(LibraryExtension::class.java)
                ?.libraryVariants?.map { it.name to project }
        }.flatten()
            .group()

    /**
     * Groups a list of [Pair]s using their natural grouping - i.e. by taking the [Pair.first] as
     * the key, and [Pair.second] as the value.
     */
    private fun <K, V> Iterable<Pair<K, V>>.group(): Map<K, List<V>> =
        groupBy({ (k, _) -> k }, { (_, v) -> v })

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
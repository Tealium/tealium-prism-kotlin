package com.tealium.gradle.dokka

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.engine.plugins.DokkaHtmlPluginParameters

/**
 * Plugin to be applied to the main documentation project
 */
abstract class DokkaRootPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        apply<DokkaPlugin>()
        configureDokkaPlugin()
    }

    private fun Project.configureDokkaPlugin() {
        extensions.configure(DokkaExtension::class.java) {
            moduleName.set("TealiumPrism")
            dokkaPublications.named("html") {
                // todo - consider versioning the docs
                // https://github.com/Kotlin/dokka/tree/master/examples/gradle-v2/versioning-multimodule-example
                // outputDirectory.set(project.rootDir.resolve("docs/api/0.x"))
                includes.from(file("Module.md"))
            }
            pluginsConfiguration.named("html", DokkaHtmlPluginParameters::class.java) {
                footerMessage.set("(c) Tealium 2025")
            }
        }

        // auto add the library projects
        gradle.projectsEvaluated {
            val dokkaLibraryProjects = rootProject.subprojects.filter { project ->
                project.plugins.hasPlugin(DokkaLibraryPlugin::class.java)
            }

            dependencies {
                dokkaLibraryProjects.forEach { project ->
                    add("dokka", project)
                }
            }
        }
    }
}
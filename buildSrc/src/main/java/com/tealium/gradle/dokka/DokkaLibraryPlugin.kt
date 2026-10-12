package com.tealium.gradle.dokka

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.engine.plugins.DokkaHtmlPluginParameters

/**
 * Convention plugin to be applied to any project that should have their classes documented
 */
abstract class DokkaLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        apply<DokkaPlugin>()
        configureDokkaPlugin()
    }

    private fun Project.configureDokkaPlugin() {
        val project = this
        extensions.configure(DokkaExtension::class.java) {
            pluginsConfiguration.named("html", DokkaHtmlPluginParameters::class.java) {
                configureCommonHtmlProperties()
                configureCustomTemplates(project)
            }
            
            dokkaSourceSets.named("main") {
                includes.from("Module.md", "Packages.md")
                // Feature guides are any other *.md next to Module.md; each must start with "# Module <name>"
                includes.from(fileTree(projectDir) {
                    include("*.md")
                    exclude("Module.md", "Packages.md", "README.md", "CHANGELOG.md")
                })
                // source link currently not functioning as expected for Android projects
                sourceLink {
                    localDirectory.set(file("src/main/java"))
                    remoteUrl("https://github.com/Tealium/tealium-kotlin-v2/tree/main/core/src/main/java")
                    remoteLineSuffix.set("#L")
                }
                // filter out all `internal` classes
                perPackageOption {
                    matchingRegex.set(".*internal.*")
                    suppress.set(true)
                }
            }
        }
    }
}
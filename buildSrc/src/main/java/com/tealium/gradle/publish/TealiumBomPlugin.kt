package com.tealium.gradle.publish

import com.tealium.gradle.getTealiumLibraryProjects
import com.tealium.gradle.library.TealiumLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlatformExtension
import org.gradle.api.plugins.JavaPlatformPlugin
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class TealiumBomPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            apply<JavaPlatformPlugin>()
            apply<TealiumPublishPlugin>()

            configureJavaPlatform()
            configureDependencies()
        }
    }

    private fun Project.configureJavaPlatform() {
        extensions.configure<JavaPlatformExtension> {
            allowDependencies()
        }
    }

    private fun Project.configureDependencies() {
        dependencies {
            constraints {
                val libraries = getTealiumLibraryProjects()
                libraries.forEach { project ->
                    val ext = project.extensions.getByType<TealiumLibraryExtension>()
                    val groupId = ext.groupId.get()
                    val artifactId = ext.artifactId.get()
                    val version = ext.resolvedVersion.get()

                    add("api", "$groupId:$artifactId:$version")
                }
            }
        }
    }
}
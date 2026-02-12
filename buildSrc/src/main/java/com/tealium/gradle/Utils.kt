package com.tealium.gradle

import com.tealium.gradle.library.TealiumLibraryPlugin
import org.gradle.api.Project

fun Project.getPropertyOrEnvironmentVariable(
    name: String,
    default: String = ""
): String {
    val property = findProperty(name)
    if (property != null) {
        return property.toString()
    }

    val env = System.getenv(name)
    if (env != null) return env

    return default
}

/**
 * Returns a list of [Project]s that have the [TealiumLibraryPlugin] applied.
 */
fun Project.getTealiumLibraryProjects(): List<Project> {
    return rootProject.subprojects.filter { project ->
        project.plugins.hasPlugin(TealiumLibraryPlugin::class.java)
    }
}
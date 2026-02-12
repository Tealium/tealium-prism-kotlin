package com.tealium.gradle.library

import com.tealium.gradle.getPropertyOrEnvironmentVariable
import org.gradle.api.Project
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

open class TealiumLibraryExtension(
    project: Project
) {
    val groupId: Property<String> = project.objects.property(String::class.java)
    val artifactId: Property<String> = project.objects.property(String::class.java)
    val version: Property<String> = project.objects.property(String::class.java)

    /**
     * Returns a resolved version string.
     *
     * The returned value will be suffixed with `-SNAPSHOT` if the `SNAPSHOT` property is set to `true`
     */
    val resolvedVersion: Provider<String> = project.provider {
        val base = version.get()

        val isSnapshot = project.getPropertyOrEnvironmentVariable("SNAPSHOT")
            .toBoolean()

        if (isSnapshot && !base.endsWith("-SNAPSHOT")) {
            "$base-SNAPSHOT"
        } else {
            base
        }
    }

    init {
        groupId.set(DefaultProvider { project.group.toString() })
        artifactId.set(DefaultProvider { project.name })
        version.set(DefaultProvider { project.version.toString() })
    }
}
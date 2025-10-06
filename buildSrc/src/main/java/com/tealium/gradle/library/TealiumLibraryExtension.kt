package com.tealium.gradle.library

import org.gradle.api.Project
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.provider.Property

open class TealiumLibraryExtension(
    project: Project
) {
    val groupId: Property<String> = project.objects.property(String::class.java)
    val artifactId: Property<String> = project.objects.property(String::class.java)
    val version: Property<String> = project.objects.property(String::class.java)
    init {
        groupId.set(DefaultProvider { project.group.toString() })
        artifactId.set(DefaultProvider { project.name })
        version.set(DefaultProvider { project.version.toString() })
    }
}
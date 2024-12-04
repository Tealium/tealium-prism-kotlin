package com.tealium.gradle

import com.tealium.gradle.git.GitHelper
import com.tealium.gradle.library.TealiumLibraryPlugin
import com.tealium.gradle.tests.TestType
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction


abstract class UpdatedModules : DefaultTask() {

    @Input
    val testType: Property<TestType> = project.objects.property(TestType::class.java)

    @TaskAction
    fun run() {
        val updatedModules = project.subprojects
            .filter { it.plugins.hasPlugin(TealiumLibraryPlugin::class.java) }
            .filter { it.tasks.findByPath(testType.get().taskName("Debug")) != null }
            .filter { GitHelper.isModified(it) }

        println(updatedModules.joinToString(",") { it.name })
    }
}
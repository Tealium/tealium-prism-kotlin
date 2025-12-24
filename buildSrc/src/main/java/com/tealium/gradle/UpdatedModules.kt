package com.tealium.gradle

import com.tealium.gradle.git.GitHelper
import com.tealium.gradle.library.TealiumLibraryPlugin
import com.tealium.gradle.tests.TestType
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction


abstract class UpdatedModules : DefaultTask() {

    @InputDirectory
    val rootDir: DirectoryProperty = project.objects.directoryProperty()

    @Input
    val modules: ListProperty<String> = project.objects.listProperty(String::class.java)

    @Input
    val testType: Property<TestType> = project.objects.property(TestType::class.java)

    @Input
    val baseBranch: Property<String> = project.objects.property(String::class.java)

    @Input
    val incomingBranch: Property<String> = project.objects.property(String::class.java)

    init {
        rootDir.convention(
            project.provider {
                project.layout.projectDirectory
            }
        )
        modules.convention(
            project.provider {
                project.subprojects
                    .filter { it.plugins.hasPlugin(TealiumLibraryPlugin::class.java) }
                    .filter { it.tasks.findByPath(testType.get().taskName("Debug")) != null }
                    .map(Project::getName)
            }
        )
    }

    @TaskAction
    fun run() {
        val root = rootDir.get().asFile
        val base = baseBranch.get()
        val incoming = incomingBranch.get()

        val updatedModules = modules.get().filter { moduleName ->
            GitHelper.isModified(
                root,
                moduleName,
                base,
                incoming,
                logger
            )
        }

        println(updatedModules.joinToString(","))
    }
}
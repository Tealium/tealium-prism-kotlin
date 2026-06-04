package com.tealium.gradle

import com.tealium.gradle.git.GitHelper
import com.tealium.gradle.library.TealiumLibraryPlugin
import com.tealium.gradle.tests.TestType
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction


abstract class UpdatedModules : DefaultTask() {

    @InputDirectory
    val rootDir: DirectoryProperty = project.objects.directoryProperty()

    @Input
    val modules: ListProperty<String> = project.objects.listProperty(String::class.java)

    @Input
    val modulePaths: MapProperty<String, String> =
        project.objects.mapProperty(String::class.java, String::class.java)

    @Input
    @Suppress("UNCHECKED_CAST")
    val moduleChildPaths: MapProperty<String, List<String>> =
        project.objects.mapProperty(String::class.java, List::class.java as Class<List<String>>)

    @Input
    @Optional
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

        val librarySubprojects = project.provider {
            project.subprojects.filter {
                it.plugins.hasPlugin(TealiumLibraryPlugin::class.java)
            }
        }

        modules.convention(
            librarySubprojects.map { projects ->
                val maybeTestType = testType.orNull
                projects
                    .filter { sub ->
                        if (maybeTestType != null)
                            sub.tasks.findByPath(maybeTestType.taskName("Debug")) != null
                        else true
                    }
                    .map(Project::getName)
            }
        )

        modulePaths.convention(
            librarySubprojects.map { projects ->
                projects.associate { sub ->
                    sub.name to sub.projectDir.relativeTo(sub.rootDir).path
                }
            }
        )

        moduleChildPaths.convention(
            librarySubprojects.map { projects ->
                projects.associate { sub ->
                    val subDir = sub.projectDir
                    val children = projects
                        .filter { other ->
                            other != sub && other.projectDir.startsWith(subDir)
                        }
                        .map { it.projectDir.relativeTo(sub.rootDir).path }
                    sub.name to children
                }
            }
        )
    }

    @TaskAction
    fun run() {
        val root = rootDir.get().asFile
        val base = baseBranch.get()
        val incoming = incomingBranch.get()
        val paths = modulePaths.get()
        val childPaths = moduleChildPaths.get()

        val updatedModules = modules.get().filter { moduleName ->
            val projectPath = paths[moduleName] ?: moduleName
            val children = childPaths[moduleName] ?: emptyList()
            GitHelper.isModified(root, projectPath, children, base, incoming, logger)
        }

        println(updatedModules.joinToString(","))
    }
}

package com.tealium.gradle.dokka

import com.tealium.gradle.getPropertyOrEnvironmentVariable
import org.gradle.api.Project
import org.jetbrains.dokka.gradle.engine.plugins.DokkaHtmlPluginParameters

fun Project.getDokkaTemplatesDirectory() =
    project.getPropertyOrEnvironmentVariable("DOKKA_TEMPLATES_DIR")

fun DokkaHtmlPluginParameters.configureCommonHtmlProperties() {
    footerMessage.set("(c) Tealium 2026")
}

fun DokkaHtmlPluginParameters.configureCustomTemplates(project: Project) {
    val templatesPath = project.getDokkaTemplatesDirectory()

    if (templatesPath.isBlank()) {
        project.logger.info("No custom Dokka templates provided; using defaults")
        return
    }

    val docs = project.project(":docs")
    val templates = docs.file(templatesPath)
    if (templates.exists()) {
        templatesDir.set(templates)
        customStyleSheets.from( docs.fileTree(templates) {
            include("**/*.css")
        })
    }
}
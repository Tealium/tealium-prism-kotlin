package com.tealium.gradle

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
package com.tealium.gradle.tests

enum class JacocoCoverageType {
    Default, ModifiedOnly;

    fun taskName(variant: String) = when (this) {
        Default -> "jacoco${variant}TestCoverage"
        ModifiedOnly -> "jacocoModified${variant}TestCoverage"
    }
}

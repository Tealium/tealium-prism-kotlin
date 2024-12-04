package com.tealium.gradle.tests

enum class JacocoVerifyType {
    Default, ModifiedOnly;

    fun taskName(variant: String) = when (this) {
        Default -> "verify${variant}TestCoverage"
        ModifiedOnly -> "verifyModified${variant}TestCoverage"
    }
}
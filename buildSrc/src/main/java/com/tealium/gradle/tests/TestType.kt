package com.tealium.gradle.tests

enum class TestType {
    UnitTest, InstrumentedTest;

    /**
     * Returns the standard Android test task name for the given variant
     */
    fun taskName(variant: String) = when (this) {
        UnitTest -> "test${variant}UnitTest"
        InstrumentedTest -> "connected${variant}AndroidTest"
    }

    /**
     * Returns the Tealium test task name for the given variant, for modified projects only.
     */
    fun modifiedTaskName(variant: String) = when (this) {
        UnitTest -> "testModified${variant}UnitTest"
        InstrumentedTest -> "testModified${variant}InstrumentedTest"
    }
}
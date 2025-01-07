plugins {
    alias(libs.plugins.java)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.lint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.jar {
    manifest {
        attributes(mapOf("Lint-Registry-v2" to "com.tealium.lint.TealiumLintRegistry"))
    }
}

dependencies {
    compileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
}
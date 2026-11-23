plugins {
    alias(libs.plugins.tealium.library)
}

version = "0.8.0"

tealiumLibrary {
    groupId = "com.tealium.prism"
    artifactId = "prism-extensions"
}

android {
    namespace = "com.tealium.prism.extensions"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "TEALIUM_LIBRARY_VERSION", "\"$version\"")
    }
}

dependencies {
    implementation(project(":core"))

    testImplementation(libs.test.junit)
    testImplementation(libs.test.mockk.jvm)
    testImplementation(libs.test.robolectric)

    androidTestImplementation(libs.test.androidx.junit)
}
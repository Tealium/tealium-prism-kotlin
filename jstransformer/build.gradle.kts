plugins {
    alias(libs.plugins.tealium.library)
}

version = "0.1.1"

tealiumLibrary {
    groupId = "com.tealium.prism"
    artifactId = "prism-js-transformer"
}

android {
    namespace = "com.tealium.prism.jstransformer"

    defaultConfig {
        buildConfigField("String", "TEALIUM_LIBRARY_VERSION", "\"$version\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

    }
}

dependencies {
    implementation(project(":core"))

    testImplementation(project(":tests-common"))
    testImplementation(libs.test.junit)
    testImplementation(libs.test.mockk.jvm)
    testImplementation(libs.test.robolectric)
    
    androidTestImplementation(libs.test.androidx.junit)
}
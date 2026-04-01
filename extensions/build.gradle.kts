plugins {
    alias(libs.plugins.tealium.library)
}

version = "0.1.0"

tealiumLibrary {
    artifactId = "prism-extensions"
}

android {
    namespace = "com.tealium.prism.extensions"
    compileSdk = 35

    defaultConfig {
        minSdk = 23

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
    testImplementation(project(":tests-common"))

    androidTestImplementation(libs.test.androidx.junit)
}
plugins {
    alias(libs.plugins.tealium.library)
}

version = "0.1.0"

tealiumLibrary {
    groupId = "com.tealium.prism"
    artifactId = "prism-js-transformer-rhino"
}

android {
    namespace = "com.tealium.prism.jstransformer.rhino"
    compileSdk = 35

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":jstransformer"))

    implementation(libs.rhino)

    testImplementation(project(":tests-common"))
    testImplementation(libs.test.junit)
    testImplementation(libs.test.mockk.jvm)
    testImplementation(libs.test.robolectric)
    androidTestImplementation(libs.test.androidx.junit)
}
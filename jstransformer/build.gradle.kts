plugins {
    alias(libs.plugins.tealium.library)
}

tealiumLibrary {
    artifactId = "prism-js-transformer"
}

android {
    namespace = "com.tealium.prism.jstransformer"
    compileSdk = 35

    defaultConfig {
        minSdk = 23

        buildConfigField("String", "TEALIUM_LIBRARY_VERSION", "\"$version\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

    }
}

dependencies {
    implementation(project(":core"))

    testImplementation(libs.test.junit)
    testImplementation(libs.test.mockk.jvm)
    testImplementation(libs.test.robolectric)
    
    androidTestImplementation(libs.test.androidx.junit)
}
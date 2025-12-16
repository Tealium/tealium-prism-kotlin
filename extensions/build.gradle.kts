plugins {
    alias(libs.plugins.tealium.library)
}

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
    androidTestImplementation(libs.test.androidx.junit)
}
plugins {
    alias(libs.plugins.tealium.library)
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

    testImplementation(libs.test.junit)
    androidTestImplementation(libs.test.androidx.junit)
}
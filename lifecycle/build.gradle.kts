plugins {
    alias(libs.plugins.tealium.library)
}

version = "0.1.0"

tealiumLibrary {
    groupId = "com.tealium.prism"
    artifactId = "prism-lifecycle"
}

android {
    namespace = "com.tealium.prism.lifecycle"
    compileSdk = 35

    defaultConfig {
        minSdk = 23

        buildConfigField ("String", "TEALIUM_LIBRARY_VERSION", "\"$version\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles.add(file("consumer-rules.pro"))
    }

    kotlin {

        kotlinOptions {
            freeCompilerArgs += "-Xjvm-default=all"
        }
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            merges += "META-INF/LICENSE.md"
            merges += "META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {

    implementation(project(":core"))

    testImplementation(libs.test.androidx.core)
    testImplementation(libs.test.junit)
    testImplementation(libs.test.robolectric)
    testImplementation(libs.test.mockk.jvm)
    testImplementation(project(":tests-common"))

    androidTestImplementation(libs.test.androidx.junit)
    androidTestImplementation(libs.test.androidx.runner)
    androidTestImplementation(project(":core"))
    androidTestImplementation(project(":tests-common"))
}
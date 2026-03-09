plugins {
    alias(libs.plugins.tealium.library)
}

version = "0.4.0"

tealiumLibrary {
    groupId = "com.tealium.prism"
    artifactId = "prism-core"
}

android {
    namespace = "com.tealium.prism.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 23

        buildConfigField("String", "TAG", "\"TealiumPrism\"")
        buildConfigField("String", "TEALIUM_LIBRARY_NAME", "\"prism-kotlin\"")
        buildConfigField("String", "TEALIUM_LIBRARY_VERSION", "\"$version\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles.add(file("consumer-rules.pro"))
    }

    kotlin {
        kotlinOptions {
            freeCompilerArgs += "-Xjvm-default=all"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
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

    implementation(libs.androidx.annotations)

    testImplementation(project(":tests-common"))
    testImplementation(libs.test.androidx.core)
    testImplementation(libs.test.junit)
    testImplementation(libs.test.kotlinx.coroutines)
    testImplementation(libs.test.mockk.jvm)
    testImplementation(libs.test.okhttp.webserver)
    testImplementation(libs.test.robolectric)

    androidTestImplementation(project(":tests-common"))
    androidTestImplementation(libs.test.androidx.junit)
    androidTestImplementation(libs.test.androidx.runner)
    androidTestImplementation(libs.test.kotlinx.coroutines)
    androidTestImplementation(libs.test.mockk.android)
}
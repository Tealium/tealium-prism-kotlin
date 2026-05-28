plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tealium.prism.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tealium.prism.mobile"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.app.androidx.core)
    implementation(libs.app.androidx.appcompat)
    implementation(libs.app.androidx.constraintlayout)
    implementation(libs.app.androidx.fragment)
    implementation(libs.app.androidx.lifecycle.viewmodel)
    implementation(libs.app.material)
    implementation(project (":core"))
    implementation(project (":lifecycle"))
    implementation(project (":momentsapi"))
    implementation(project (":extensions"))
    implementation(project (":jstransformer"))
    implementation(project (":jstransformer:jstransformer-rhino"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
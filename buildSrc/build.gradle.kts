plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    maven(url = "https://maven.google.com/")
    mavenCentral()
    maven(url = "https://plugins.gradle.org/m2/")
}

dependencies {
    implementation(libs.android.gradle)
    implementation(libs.kotlin.gradle.jvm)
    implementation(libs.eclipse.jgit)
    implementation(libs.dokka.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("tealiumPlugin") {
            id = "tealium-plugin"
            implementationClass = "com.tealium.gradle.TealiumGradlePlugin"
        }
        register("tealiumLibraryPlugin") {
            id = "tealium-library"
            implementationClass = "com.tealium.gradle.library.TealiumLibraryPlugin"
        }
        register("dokkaRoot") {
            id = "dokka-root"
            implementationClass = "com.tealium.gradle.dokka.DokkaRootPlugin"
        }
        register("dokkaLibrary") {
            id = "dokka-library"
            implementationClass = "com.tealium.gradle.dokka.DokkaLibraryPlugin"
        }
    }
}
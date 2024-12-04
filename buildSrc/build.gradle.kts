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
    implementation("com.android.tools.build:gradle:8.1.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.22")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
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
    }
}
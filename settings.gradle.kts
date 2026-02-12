pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "com.tealium.prism"
include(":app")
include(":core")
include(":lifecycle")
include(":momentsapi")
include(":tests-common")
include(":lint")
include(":docs")
include(":platform")

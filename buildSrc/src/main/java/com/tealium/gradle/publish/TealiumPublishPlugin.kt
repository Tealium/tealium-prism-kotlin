package com.tealium.gradle.publish

import com.tealium.gradle.getPropertyOrEnvironmentVariable
import com.tealium.gradle.library.TealiumLibraryExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.plugins.JavaPlatformPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.hasPlugin
import java.net.URI

class TealiumPublishPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            apply<MavenPublishPlugin>()

            val tealiumLibrary =
                extensions.create<TealiumLibraryExtension>("tealiumLibrary", this)

            configurePublishing(tealiumLibrary)
        }
    }


    private fun Project.configurePublishing(tealiumLibraryExtension: TealiumLibraryExtension) {
        val credentials = Action<AwsCredentials> {
            accessKey = getPropertyOrEnvironmentVariable("AWS_ACCESS_KEY")
            secretKey = getPropertyOrEnvironmentVariable("AWS_SECRET_KEY")
            sessionToken = getPropertyOrEnvironmentVariable("AWS_SESSION_TOKEN")
        }

        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    url = URI("s3://maven.tealiumiq.com/android/releases/")
                    name = "Release"
                    credentials(AwsCredentials::class.java, credentials)
                }
                maven {
                    url = URI("s3://maven.tealiumiq.com/android/snapshots/")
                    name = "Snapshot"
                    credentials(AwsCredentials::class.java, credentials)
                }
            }
            publications.create<MavenPublication>("Release") {
                afterEvaluate {
                    groupId = tealiumLibraryExtension.groupId.get()
                    artifactId = tealiumLibraryExtension.artifactId.get()
                    version = tealiumLibraryExtension.resolvedVersion.get()

                    val componentName = if (plugins.hasPlugin(JavaPlatformPlugin::class)) {
                        "javaPlatform"
                    } else {
                        "release"
                    }
                    val component = requireNotNull(components.findByName(componentName)) {
                        "Publishing component '$componentName' not found. Ensure publishing is configured for this variant."
                    }
                    from(component)
                }
            }
        }
    }
}
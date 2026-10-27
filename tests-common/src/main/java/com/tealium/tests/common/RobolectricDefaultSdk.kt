package com.tealium.tests.common

import org.robolectric.annotation.Config
import org.robolectric.pluginapi.config.GlobalConfigProvider

/**
 * Robolectric's lowest config layer for every module's unit tests: runs tests without an explicit
 * `sdk` on the catalog `minSdk`, instead of the level Robolectric derives from the merged manifest's
 * targetSdk (or its lowest level when the module has no test manifest).
 *
 * The level is compiled into this module's `BuildConfig`, so it applies under any runner (Gradle,
 * IDE, other). A package `robolectric.properties` and class or method `@Config` still override it.
 *
 * Registered in `META-INF/services/org.robolectric.pluginapi.config.GlobalConfigProvider`. The
 * services file must contain exactly this class name, with no comments, blank lines or trailing
 * whitespace: Robolectric 4.13 reads it raw (`PluginFinder`), and a bad line fails every Robolectric
 * test.
 */
class RobolectricDefaultSdk : GlobalConfigProvider {

    override fun get(): Config = Config.Builder().setSdk(BuildConfig.ROBOLECTRIC_DEFAULT_SDK).build()
}

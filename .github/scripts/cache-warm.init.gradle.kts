// CI-only init script for the cache writer; used only by .github/workflows/ci-cache-warm.yml:
//   ./gradlew -I .github/scripts/cache-warm.init.gradle.kts assembleDebug assembleDebugAndroidTest testDebugUnitTest
// Runs every test task's prerequisites (compilation, artifact transforms, dexing, JaCoCo instrumentation) so their
// outputs land in the CI cache. The filter selects no tests, so no test JVM starts (Gradle filters by class name
// before forking). The test tasks' results are not cached, and nothing is reported as tested.
// Registers no task or extension: one registered here would change every project's Kotlin DSL accessors, and the
// PR builds could no longer reuse the build scripts this run compiles.
// Guard: a test task that still runs a test (the empty filter stopped working) fails the build. The doLast reads the
// task's JUnit XML report and captures only the task's own providers, so it's configuration-cache compatible.
// Gradle 9: with a filter set, failOnNoMatchingTests still decides, not the new failOnNoDiscoveredTests.
allprojects {
    tasks.withType<Test>().configureEach {
        filter {
            setIncludePatterns("ci.cachewarm.NoTestMatchesThis")
            isFailOnNoMatchingTests = false
        }
        outputs.doNotCacheIf("CI cache warm-up runs no tests") { true }
        reports.junitXml.required.set(true) // the guard reads it
        val xmlDir = reports.junitXml.outputLocation
        val label = path
        doLast {
            val reports = xmlDir.get().asFile.listFiles { f -> f.name.startsWith("TEST-") && f.name.endsWith(".xml") }
            val ran = reports.orEmpty().sumOf { xml ->
                Regex("""<testsuite\b[^>]*?\btests="(\d+)"""").find(xml.readText())?.groupValues?.get(1)?.toInt() ?: 1
            }
            if (ran > 0) {
                throw GradleException("cache-warm: $label ran $ran tests; the empty filter no longer works " +
                    "(Gradle change?), fix .github/scripts/cache-warm.init.gradle.kts")
            }
        }
    }
}

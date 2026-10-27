// CI-only: timing records for phases_summary.py and the "Check that the connected tasks ran" step, plus a
// "Gradle configuration" GitHub log group around configuration.
// Used by .github/workflows/kotlin-unit-tests.yml (Build, Unit tests and instrumented tests steps) and
// ci-cache-warm.yml; phases_summary.py turns the records into the job summary tables.
// usage: ./gradlew <tasks> -I .github/scripts/phases.init.gradle.kts
//   (not on the `-q` steps whose stdout goes to $GITHUB_OUTPUT)
// File: $RUNNER_TEMP/gradle-phases.tsv (else <java.io.tmpdir>/gradle-phases.tsv), appended to by every build.
// Record format (canonical; phases_summary.py and the workflow check refer here): one record per line,
// 7 tab-separated columns, `-` = empty:
//   kind id name start end outcome detail
//   phase rows: id = per-configuration id, name = start|settings|projectsLoaded|configureStart|projectsEvaluated|
//               taskGraph, start = epoch ms, detail = requested tasks (on `start`)
//   task rows:  id = per-build id, name = task path, start/end = epoch ms,
//               outcome = executed|from-cache|up-to-date|skipped|failed, detail = skip reason
//   build rows: id = per-build id, name = requested tasks, start = epoch ms (written before the build's first task
//               row, on a hit too)
//   end rows:   end = epoch ms, outcome = SUCCESS|FAILED; belongs to the latest build (builds run one after another)
// Phase rows come from configuration hooks, so a configuration-cache hit writes none; task rows come from a
// BuildService listener, which runs (with a fresh id) on a hit too; build and end rows are written on a hit too.
// Timing must never fail the build.
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Input
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import java.util.UUID
import javax.inject.Inject

// An object, not script state: the listener below must not capture the script instance.
object PhasesTsv {
    private val path: String =
        (System.getenv("RUNNER_TEMP") ?: System.getProperty("java.io.tmpdir")) + "/gradle-phases.tsv"

    // Synchronized: the listener's delivery thread vs the build thread.
    fun append(vararg cols: Any?) = synchronized(this) {
        java.io.File(path).appendText(
            cols.joinToString("\t") { it?.toString()?.replace(Regex("[\t\n]"), " ")?.ifEmpty { "-" } ?: "-" } + "\n"
        )
    }
}

// Init scripts also run for buildSrc and other nested builds; only the root build writes phase rows.
if (gradle.parent == null) {
    val inv = UUID.randomUUID().toString().take(8)
    fun phase(name: String, detail: String? = null) =
        PhasesTsv.append("phase", inv, name, System.currentTimeMillis(), null, null, detail)

    phase("start", gradle.startParameter.taskNames.joinToString(" "))
    // One collapsed GitHub log group around configuration only, so task output and failures stay visible.
    // Lifecycle level: `-q` prints neither marker. A hit runs no configuration, so it prints none.
    logger.lifecycle("::group::Gradle configuration")
    beforeSettings { pluginManager.apply(TaskTimesPlugin::class.java) }
    settingsEvaluated { phase("settings") }
    projectsLoaded { phase("projectsLoaded") }
    var configuring = false
    beforeProject { if (!configuring) { configuring = true; phase("configureStart") } }
    projectsEvaluated { phase("projectsEvaluated") }
    gradle.taskGraph.whenReady { phase("taskGraph"); logger.lifecycle("::endgroup::") }
}

// A settings plugin: the public way to get an injected BuildEventsListenerRegistry, FlowScope and FlowProviders.
abstract class TaskTimesPlugin @Inject constructor(
    private val listeners: BuildEventsListenerRegistry,
    private val flowScope: FlowScope,
    private val flowProviders: FlowProviders,
) : Plugin<Settings> {
    override fun apply(settings: Settings) {
        listeners.onTaskCompletion(settings.gradle.sharedServices.registerIfAbsent("phasesTaskRows", TaskTimes::class.java) {
            // Stored in the cache entry; right on a hit because the requested task names are part of the cache key.
            parameters.tasks.set(settings.gradle.startParameter.taskNames.joinToString(" "))
        })
        // The build outcome: a flow action is the only configuration-cache-compatible source of it. It must not
        // reference TaskTimes (a typed service parameter fails to reload when the daemon runs another miss).
        flowScope.always(BuildEnd::class.java) {
            parameters.outcome.set(flowProviders.buildWorkResult.map { if (it.failure.isPresent) "FAILED" else "SUCCESS" })
        }
    }
}

// Created anew in every build, also on a configuration-cache hit, so `run` identifies one build's tasks.
abstract class TaskTimes : BuildService<TaskTimes.Params>, OperationCompletionListener {
    interface Params : BuildServiceParameters {
        val tasks: Property<String>
    }

    private val run = UUID.randomUUID().toString().take(8)
    private val first = java.util.concurrent.atomic.AtomicBoolean(true)

    override fun onFinish(event: FinishEvent) {
        runCatching {
            if (event !is TaskFinishEvent) return
            if (first.getAndSet(false)) PhasesTsv.append("build", run, parameters.tasks.orNull, System.currentTimeMillis(), null, null, null)
            val r = event.result
            val (outcome, reason) = when (r) {
                is TaskSuccessResult -> (if (r.isFromCache) "from-cache" else if (r.isUpToDate) "up-to-date" else "executed") to null
                is TaskSkippedResult -> "skipped" to r.skipMessage
                is TaskFailureResult -> "failed" to null
                else -> "unknown" to null
            }
            PhasesTsv.append("task", run, event.descriptor.taskPath, r.startTime, r.endTime, outcome, reason)
        }
    }
}

abstract class BuildEnd : FlowAction<BuildEnd.Params> {
    interface Params : FlowParameters {
        @get:Input val outcome: Property<String>
    }

    // Records Gradle's build work result (flowProviders.buildWorkResult), not the process exit status; failures
    // during service shutdown are not included.
    override fun execute(parameters: Params) {
        val outcome = parameters.outcome.get()
        // A configuration failure leaves the configuration group open: close it so the failure report shows.
        // (A stray ::endgroup:: after a failed miss or hit is expected to be ignored by GitHub (not verified).)
        if (outcome == "FAILED") org.gradle.api.logging.Logging.getLogger(BuildEnd::class.java).lifecycle("::endgroup::")
        runCatching { PhasesTsv.append("end", null, null, null, System.currentTimeMillis(), outcome, null) }
    }
}

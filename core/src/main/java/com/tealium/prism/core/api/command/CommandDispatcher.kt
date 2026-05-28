package com.tealium.prism.core.api.command

import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfDebugEnabled
import com.tealium.prism.core.api.logger.logIfWarnEnabled
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.command.CommandRegistry
import com.tealium.prism.core.internal.command.getCommands
import com.tealium.prism.core.internal.dispatch.CompletableTask
import com.tealium.prism.core.internal.dispatch.Tasks
import com.tealium.prism.core.internal.pubsub.DisposableContainer

/**
 * Abstract [Dispatcher] that routes each [Dispatch] through a [CommandRegistry].
 *
 * Subclasses provide the list of [Command]s at construction time.
 * Command names are extracted from the dispatch payload via the [Dispatch.getCommands] extension.
 *
 * The [scheduler] controls which thread the per-dispatch completion callback is invoked on
 * once all of the dispatch's commands have finished. Runtime subclasses must supply the
 * regular Tealium scheduler (i.e. `schedulers.tealium`) so that completion always lands on
 * that dedicated thread. Passing [Scheduler.SYNCHRONOUS] here is intended for tests only:
 * it makes completion run in whichever command-execution thread happens to finish last,
 * which is not safe for runtime use.
 */
abstract class CommandDispatcher(
    final override val id: String,
    final override val version: String,
    commands: List<Command>,
    protected val logger: Logger,
    protected val logCategory: String = id,
    private val scheduler: Scheduler,
) : Dispatcher {

    override val dispatchLimit: Int get() = 10

    private val commandRegistry = CommandRegistry(commands)

    /**
     * Locked routing bridge: for each dispatch, executes its commands in parallel.
     * Invokes [callback] once per [Dispatch] as it completes, consistent with the
     * [Dispatcher] contract for split-batch acknowledgement.
     *
     * This method is final to guarantee consistent routing behaviour. Extend via custom
     * [Command] implementations.
     */
    final override fun dispatch(
        dispatches: List<Dispatch>,
        callback: Callback<List<Dispatch>>
    ): Disposable {
        val container = DisposableContainer()
        for (dispatch in dispatches) {
            container.add(processDispatch(dispatch) {
                callback.onComplete(listOf(dispatch))
            })
        }
        return container
    }

    private fun processDispatch(dispatch: Dispatch, completion: () -> Unit): Disposable {
        val commands = dispatch.getCommands()

        if (commands.isEmpty()) {
            logger.logIfDebugEnabled(logCategory) { "No command in dispatch ${dispatch.logDescription()}." }
            completion()
            return Disposables.disposed()
        }

        logger.logIfDebugEnabled(logCategory) { "Processing dispatch ${dispatch.logDescription()} with commands: $commands." }

        return Tasks.execute(
            notifyOn = scheduler,
            completableTasks = commands.map { commandName ->
                CompletableTask<Unit> { onComplete ->
                    commandRegistry.execute(commandName, dispatch.payload()) { result ->
                        if (result.isFailure) {
                            logger.logIfWarnEnabled(logCategory) { "Command '$commandName' failed: ${result.exceptionOrNull()}" }
                        } else {
                            logger.logIfDebugEnabled(logCategory) { "Command '$commandName' completed." }
                        }
                        onComplete(Unit)
                    }
                }
            },
            notify = { completion() }
        )
    }
}

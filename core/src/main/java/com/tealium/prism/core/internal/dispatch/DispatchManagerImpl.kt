package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfDebugEnabled
import com.tealium.prism.core.api.logger.logIfErrorEnabled
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.tracking.TrackResultListener
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.internal.barriers.BarrierCoordinator
import com.tealium.prism.core.internal.consent.ConsentManager
import com.tealium.prism.core.internal.consent.matchesConfiguration
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.logger.logDescriptions
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.internal.rules.LoadRuleEngine

class DispatchManagerImpl(
    /**
     * The enabled [Dispatcher]s, emitting only once the modules of a settings pass are all created,
     * and re-emitting only when that list actually changes.
     *
     * The dequeue loops are rebuilt on every emission, so this relies on the source de-duplicating by
     * identity and order - which `ModuleManager.observeModules` does.
     */
    private val onDispatchers: Observable<List<Dispatcher>>,
    private val barrierCoordinator: BarrierCoordinator,
    private val transformerCoordinator: TransformerCoordinator,
    private val queueManager: QueueManager,
    private val loadRuleEngine: LoadRuleEngine,
    private val mappingsEngine: MappingsEngine,
    private val consentManager: ConsentManager?,
    private val logger: Logger,
    private val maxInFlightPerDispatcher: Int = MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER
) : DispatchManager {

    private var dispatchLoop: Disposable? = null

    override fun track(dispatch: Dispatch) {
        track(dispatch, null)
    }

    override val tealiumPurposeExplicitlyBlocked: Boolean
        get() = consentManager?.tealiumPurposeExplicitlyBlocked == true

    override fun track(dispatch: Dispatch, onComplete: TrackResultListener?) {
        if (tealiumPurposeExplicitlyBlocked) {
            logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                "Tealium consent purpose is explicitly blocked. Event ${dispatch.logDescription()} will be dropped."
            }

            onComplete?.onTrackResultReady(
                TrackResult.dropped(
                    dispatch,
                    "Tealium consent purpose is explicitly blocked."
                )
            )
            return
        }

        transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors) { transformed ->
            if (transformed == null) {
                logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                    "Event ${dispatch.logDescription()} dropped due to transformer"
                }

                onComplete?.onTrackResultReady(
                    TrackResult.dropped(
                        dispatch,
                        "Transformers decision."
                    )
                )
                return@transform
            }

            val consentManager = consentManager
            if (consentManager != null) {
                logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                    "Event ${transformed.logDescription()} consent applied"
                }

                val result = consentManager.applyConsent(transformed)
                onComplete?.onTrackResultReady(result)
            } else {
                // Enqueue for the dispatchers of the settings pass that is current when the dispatch
                // is ready, rather than for whichever list happened to be published beforehand.
                onDispatchers.subscribeOnce { dispatchers ->
                    val dispatcherIds = dispatchers.map(Dispatcher::id).toSet()
                    if (dispatcherIds.isEmpty()) {
                        // Storing for no processor at all would drop the dispatch silently.
                        val trackResult =
                            TrackResult.dropped(transformed, "No dispatcher is enabled.")
                        logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                            "Event ${transformed.logDescription()} dropped: ${trackResult.description}"
                        }

                        onComplete?.onTrackResultReady(trackResult)
                        return@subscribeOnce
                    }

                    try {
                        queueManager.storeDispatches(listOf(transformed), dispatcherIds)
                    } catch (ex: PersistenceException) {
                        logger.logIfErrorEnabled(LogCategory.DISPATCH_MANAGER) {
                            "Failed to persist dispatch ${transformed.logDescription()} for processors ($dispatcherIds): ${ex.message}"
                        }

                        onComplete?.onTrackResultReady(
                            TrackResult.dropped(transformed, STORE_FAILED_REASON)
                        )
                        return@subscribeOnce
                    }

                    logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                        "Event ${transformed.logDescription()} accepted for processing"
                    }

                    onComplete?.onTrackResultReady(
                        TrackResult.accepted(
                            transformed,
                            "Enqueued for processors: $dispatcherIds"
                        )
                    )
                }
            }
        }
    }

    internal fun stopDispatchLoop() {
        dispatchLoop?.dispose()
        dispatchLoop = null
    }

    /**
     * Subscribes the dispatch pipeline: for each active [Dispatcher], wait for its barriers to open,
     * dequeue its queued [Dispatch]es, transform them, and hand them over to be sent.
     *
     * Calling this while a loop is already running is a no-op; [stopDispatchLoop] must be called first
     * for a fresh subscription to be made. A [stopDispatchLoop] issued while the subscription is still
     * being set up, such as from within a [Dispatcher.dispatch] triggered by the initial dequeue, is
     * honored and leaves the loop stopped.
     *
     * Where each stage sits relative to the outer [Observable.flatMapLatest] decides what a change to
     * the dispatcher list cancels, so the placement is load bearing.
     *
     * Everything a [Dispatcher] needs in order to *acquire* work, meaning its barrier subscription and
     * its dequeue loop, is built inside the `flatMapLatest` and is therefore disposable. A new emission
     * from [onDispatchers] replaces that whole set of loops at once, which gives two guarantees:
     *   - a dispatcher that is no longer in the list has no loop, so it neither drains what is still queued
     *   for it nor stays alive through a subscription that outlives it;
     *   - and a dispatcher that remains in the list has exactly one loop, so at most
     *   [maxInFlightPerDispatcher] dispatches are ever in flight for it regardless of how often the list changes.
     *
     * Work that has already been acquired is deliberately kept out of that scope.
     * [transformAndDispatch] is applied downstream of the `flatMapLatest`, so dispatches already handed
     * to a [Dispatcher] are untouched by a list change and still get to report completion.
     *
     * The cost is that enabled dispatchers are rebuilt too, since the list is replaced wholesale rather
     * than diffed. Repeating a dequeue is harmless, because [QueueManager.dequeueDispatches] never
     * returns a dispatch that is already in-flight and so cannot send one twice. The source of
     * [onDispatchers] also drops emissions that leave the list untouched, keeping rebuilds to the
     * changes that genuinely alter the set of dispatchers.
     */
    internal fun startDispatchLoop() {
        if (dispatchLoop != null) {
            logger.debug(LogCategory.DISPATCH_MANAGER, "Dispatch loop already started; ignoring.")
            return
        }

        /*
         * Assigned before subscribing so that a stopDispatchLoop() re-entered from within the
         * synchronous subscribe (e.g. from a Dispatcher's dispatch) is honored: by the time the
         * subscription is added below the container is already disposed, and add() disposes it.
         */
        val loop = DisposableContainer()
        dispatchLoop = loop

        onDispatchers.flatMapLatest { dispatchers ->
            Observables.fromIterable(dispatchers)
                .flatMap { dispatcher ->
                    dequeueWhileBarriersOpen(dispatcher)
                        .map { dispatchSplit -> dispatcher to dispatchSplit }
                }
        }.flatMap { (dispatcher, dispatchSplit) ->
            if (dispatchSplit.unsuccessful.isNotEmpty()) {
                logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                    "Dispatches discarded due to consent: ${dispatchSplit.unsuccessful.logDescriptions()}"
                }
            }

            transformAndDispatch(dispatchSplit.successful, dispatcher)
                .startWith(dispatchSplit.unsuccessful)
                .filter { it.isNotEmpty() }
                .map { completedDispatches -> dispatcher to completedDispatches }
        }.subscribe { (dispatcher, completedDispatches) ->
            queueManager.deleteDispatches(
                completedDispatches,
                dispatcher.id
            )

            logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                "Dispatcher: ${dispatcher.id} processed events: ${completedDispatches.logDescriptions()}"
            }
        }.addTo(loop)
    }

    private fun dequeueWhileBarriersOpen(dispatcher: Dispatcher): Observable<DispatchSplit> =
        barrierCoordinator.onBarriersState(dispatcher.id)
            .flatMapLatest { active ->
                logger.debug(
                    LogCategory.DISPATCH_MANAGER,
                    "BarrierState changed for %s: %s", dispatcher.id, active
                )

                if (active == BarrierState.Open) {
                    startConsentedDequeueLoop(dispatcher)
                } else {
                    Observables.empty()
                }
            }

    private fun startConsentedDequeueLoop(dispatcher: Dispatcher): Observable<DispatchSplit> {
        if (consentManager == null) {
            return startDequeueLoop(dispatcher)
                .map { DispatchSplit(it, emptyList()) }
        }

        return consentManager.configuration.flatMapLatest { consentConfiguration ->
            if (consentConfiguration == null) {
                return@flatMapLatest Observables.empty()
            }

            startDequeueLoop(dispatcher)
                .map { dispatches ->
                    dispatches.partition {
                        it.matchesConfiguration(consentConfiguration, dispatcher.id)
                    }
                }
        }
    }

    private fun startDequeueLoop(dispatcher: Dispatcher): Observable<List<Dispatch>> {
        val onInflightLower = queueManager.inFlightCount(dispatcher.id)
            .map(::isLessThanMaxInFlight)
            .distinct()
        return queueManager.enqueuedDispatchesForProcessors
            .filter { processors -> processors.contains(dispatcher.id) }
            .startWith(setOf())
            .flatMapLatest { _ ->
                onInflightLower
                    .filter { it }
                    .map { _ ->
                        queueManager.dequeueDispatches(
                            dispatcher.dispatchLimit,
                            dispatcher.id
                        )
                    }
                    .resubscribingWhile { it.count() >= dispatcher.dispatchLimit } // Loops the `getQueuedEvents` as long as we pull `dispatchLimit` items from the queue
            }
    }

    /**
     * Transforms the provided [dispatches] according to the current set of transformers available
     * to the [transformerCoordinator], then sends the transformed dispatches to the [dispatcher].
     *
     * Any dispatches dropped by the [transformerCoordinator] or disallowed by the [loadRuleEngine]
     * for this [dispatcher] are emitted downstream rather than being sent to the [dispatcher], so
     * that the caller can remove them from the queue. Dropped dispatches will not be replaced in
     * the batch to make up the numbers.
     *
     * Completes once every dispatch has been accounted for, or immediately if [dispatches] is empty.
     */
    private fun transformAndDispatch(
        dispatches: List<Dispatch>,
        dispatcher: Dispatcher
    ): Observable<List<Dispatch>> {
        if (dispatches.isEmpty()) return Observables.empty()

        return Observables.create { observer ->
            val remainingIds = dispatches.map { it.id }.toMutableSet()
            val container = DisposableContainer()

            fun emitProcessed(processed: List<Dispatch>) {
                if (container.isDisposed) return
                processed.forEach { remainingIds.remove(it.id) }
                observer.onNext(processed)
                if (remainingIds.isEmpty()) {
                    observer.onComplete()
                }
            }

            transformerCoordinator.transform(
                dispatches,
                DispatchScope.Dispatcher(dispatcher.id)
            ) { transformedDispatches ->
                if (container.isDisposed) return@transform

                val (passed, _) = loadRuleEngine.evaluateLoadRules(dispatcher, transformedDispatches)

                val missingDispatches = dispatches.filter { original ->
                    passed.none { it.id == original.id }
                }
                if (missingDispatches.isNotEmpty()) {
                    logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                        "Dispatching disallowed for Dispatcher(${dispatcher.id}) and Dispatches (${missingDispatches.logDescriptions()})"
                    }
                    emitProcessed(missingDispatches)
                }

                if (passed.isEmpty()) return@transform

                val mapped = passed.map { dispatch -> mappingsEngine.map(dispatcher.id, dispatch) }

                logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                    "Sending events to dispatcher ${dispatcher.id}: ${mapped.logDescriptions()}"
                }

                dispatcher.dispatch(mapped) { completed ->
                    emitProcessed(completed)
                }.addTo(container)
            }

            container
        }
    }

    private fun isLessThanMaxInFlight(count: Int): Boolean =
        count < maxInFlightPerDispatcher

    companion object {
        const val MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER = 50
        private const val STORE_FAILED_REASON = "Failed to persist dispatch to the queue."
    }
}

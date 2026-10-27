package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfDebugEnabled
import com.tealium.prism.core.api.logger.logIfErrorEnabled
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.api.pubsub.mapToUnit
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.tracking.TrackResultListener
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.internal.barriers.BarrierCoordinator
import com.tealium.prism.core.internal.consent.ConsentManager
import com.tealium.prism.core.internal.consent.matchesConfiguration
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.logger.logDescriptions
import com.tealium.prism.core.internal.misc.async
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.internal.rules.LoadRuleEngine
import com.tealium.prism.core.internal.settings.consent.ConsentConfiguration
import kotlin.math.max
import kotlin.math.min

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
    /**
     * New batches are dequeued for a [Dispatcher] only while it has fewer in-flight dispatches than this.
     *
     * This is a threshold, not a hard limit: a batch of up to `dispatchLimit` is still dequeued as a whole,
     * so the in-flight count can exceed it by up to one batch.
     */
    private val maxInFlightPerDispatcher: Int = MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER,
    /**
     * The scheduler the dispatch loop runs on, used to request the dequeue that follows a full batch
     * on a later turn.
     */
    private val scheduler: Scheduler
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
     * request dequeues of its queued [Dispatch]es, dequeue them, transform them, and hand them over
     * to be sent.
     *
     * Calling this while a loop is already running is a no-op; [stopDispatchLoop] must be called first
     * for a fresh subscription to be made. A [stopDispatchLoop] issued while the subscription is still
     * being set up, such as from within a [Dispatcher.dispatch] triggered by the initial dequeue, is
     * honored and leaves the loop stopped.
     *
     * Where each stage sits relative to the outer [Observable.flatMapLatest] decides what a change to
     * the dispatcher list cancels, so the placement is load bearing.
     *
     * Everything that decides *when* a [Dispatcher] acquires work, meaning its barrier subscription, its
     * consent configuration and its loop of [DequeueRequest]s, is built inside the `flatMapLatest` and is
     * therefore disposable. A new emission from [onDispatchers] replaces that whole set of loops at once,
     * which gives two guarantees:
     *   - a dispatcher that is no longer in the list has no loop, so it neither drains what is still queued
     *   for it nor stays alive through a subscription that outlives it;
     *   - and a dispatcher that remains in the list has exactly one loop, so new batches are dequeued for it
     *   only while it has fewer than [maxInFlightPerDispatcher] in flight, regardless of how often the list
     *   changes.
     *
     * The dequeue itself, and the work it acquires, are deliberately kept out of that scope. A dequeue
     * can synchronously close a barrier, remove the consent configuration or change the list, which
     * disposes the loop that requested it; downstream of the `flatMapLatest` such a change only stops the
     * requests that follow, and the dequeued batch is still sent. [transformAndDispatch] is downstream
     * too, so dispatches already handed to a [Dispatcher] still get to report completion.
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

        // Assigned before subscribing so that a stopDispatchLoop() re-entered from within the
        // synchronous subscribe (e.g. from code reacting synchronously to the first dequeue) is honored:
        // by the time the subscription is added below the container is already disposed, and add() disposes it.
        val loop = DisposableContainer()
        dispatchLoop = loop

        onDispatchers.flatMapLatest { dispatchers ->
            Observables.fromIterable(dispatchers)
                .flatMap(::startDequeueRequestsWhileBarriersOpen)
        }.map { request ->
            // Dequeue outside of the flatMapLatests above, so a change the dequeue triggers can't drop the batch.
            request.dispatcher to dequeue(request)
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

    private fun startDequeueRequestsWhileBarriersOpen(dispatcher: Dispatcher): Observable<DequeueRequest> =
        barrierCoordinator.onBarriersState(dispatcher.id)
            .flatMapLatest { active ->
                logger.debug(
                    LogCategory.DISPATCH_MANAGER,
                    "BarrierState changed for %s: %s", dispatcher.id, active
                )

                if (active == BarrierState.Open) {
                    startConsentedDequeueRequests(dispatcher)
                } else {
                    Observables.empty()
                }
            }

    private fun startConsentedDequeueRequests(dispatcher: Dispatcher): Observable<DequeueRequest> {
        if (consentManager == null) {
            return startDequeueRequests(dispatcher, null)
        }

        return consentManager.configuration.flatMapLatest { consentConfiguration ->
            if (consentConfiguration == null) {
                return@flatMapLatest Observables.empty()
            }

            startDequeueRequests(dispatcher, consentConfiguration)
        }
    }

    /**
     * Emits a [DequeueRequest] when the loop starts, on every enqueue for the [dispatcher] and after
     * every full batch, each time only once fewer than [maxInFlightPerDispatcher] events are in flight.
     *
     * The request after a full batch is emitted in its own task on the [scheduler], so that draining a
     * backlog doesn't recurse, and any state change the previous dequeue triggered is handled before it.
     */
    private fun startDequeueRequests(
        dispatcher: Dispatcher,
        consentConfiguration: ConsentConfiguration?
    ): Observable<DequeueRequest> {
        val onInflightLower = queueManager.inFlightCount(dispatcher.id)
            .map(::isLessThanMaxInFlight)
            .distinct()
        val onFullBatch = Observables.publishSubject<Unit>()
        val request = DequeueRequest(dispatcher, consentConfiguration) {
            // On a real scheduler, `async` always queues the task, unlike `execute`. Once this loop is
            // disposed, `onFullBatch` has no observer left and the late request is dropped.
            scheduler.async { onFullBatch.onNext(Unit) }
        }

        // `merge` subscribes in order: `onFullBatch` is observed before `startWith` requests the first dequeue.
        return onFullBatch.merge(
            queueManager.enqueuedDispatchesForProcessors
                .filter { processors -> processors.contains(dispatcher.id) }
                .mapToUnit()
                .startWith(Unit)
        ).flatMapLatest {
            onInflightLower
                .filter { it }
                .take(1)
                .map { request }
        }
    }

    /**
     * Dequeues one batch for the [request], requests the next one if it is full, and splits it by
     * the [DequeueRequest.consentConfiguration], when there is one.
     */
    private fun dequeue(request: DequeueRequest): DispatchSplit {
        val dispatcher = request.dispatcher
        val limit = max(min(dispatcher.dispatchLimit, maxInFlightPerDispatcher), 1)
        val dispatches = queueManager.dequeueDispatches(limit, dispatcher.id)
        if (dispatches.size >= limit) {
            request.onFullBatch()
        }

        val consentConfiguration = request.consentConfiguration
            ?: return DispatchSplit(dispatches, emptyList())

        return dispatches.partition {
            it.matchesConfiguration(consentConfiguration, dispatcher.id)
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
        /**
         * New batches are dequeued for a [Dispatcher] only while it has fewer inflight events than this.
         *
         * This is a threshold, not a hard limit: a batch of up to `dispatchLimit` is still dequeued as a whole,
         * so the inflight count can exceed it by up to one batch.
         */
        const val MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER = 50
        private const val STORE_FAILED_REASON = "Failed to persist dispatch to the queue."
    }
}

/**
 * A request to dequeue one batch for [dispatcher], emitted by the dequeue loop while the barriers are open.
 */
internal class DequeueRequest(
    val dispatcher: Dispatcher,
    /**
     * The consent configuration to split the batch with; `null` when there is no consent integration.
     */
    val consentConfiguration: ConsentConfiguration?,
    /**
     * Requests the next dequeue, asynchronously: called when the batch filled the dequeue limit.
     */
    val onFullBatch: () -> Unit
)

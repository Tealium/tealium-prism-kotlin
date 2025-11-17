package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfDebugEnabled
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.tracking.TrackResultListener
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.internal.consent.ConsentManager
import com.tealium.prism.core.internal.consent.matchesConfiguration
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.logger.logDescriptions
import com.tealium.prism.core.internal.modules.InternalModuleManager
import com.tealium.prism.core.internal.pubsub.CompletedDisposable
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.internal.rules.LoadRuleEngine


class DispatchManagerImpl(
    private val moduleManager: InternalModuleManager,
    private val barrierCoordinator: BarrierCoordinator,
    private val transformerCoordinator: TransformerCoordinator,
    private val queueManager: QueueManager,
    private val loadRuleEngine: LoadRuleEngine,
    private val mappingsEngine: MappingsEngine,
    private val consentManager: ConsentManager?,
    private val logger: Logger,
    private val maxInFlightPerDispatcher: Int = MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER
) : DispatchManager {

    private val dispatchers: ObservableState<Set<Dispatcher>>
        get() = moduleManager.modules.map { it.filterIsInstance<Dispatcher>().toSet() }
            .withState { moduleManager.getModulesOfType(Dispatcher::class.java).toSet() }

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
                logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                    "Event ${transformed.logDescription()} accepted for processing"
                }

                val dispatcherIds = dispatchers.value.map(Dispatcher::id).toSet()
                queueManager.storeDispatches(listOf(transformed), dispatcherIds)

                onComplete?.onTrackResultReady(
                    TrackResult.accepted(
                        transformed,
                        "Enqueued for processors: $dispatcherIds"
                    )
                )
            }
        }
    }

    internal fun stopDispatchLoop() {
        dispatchLoop?.dispose()
    }

    internal fun startDispatchLoop() {
        dispatchLoop =
            dispatchers.flatMapLatest { dispatchers ->
                Observables.fromIterable(dispatchers)
            }.flatMap { dispatcher ->
                ensureBarriersOpen(dispatcher)
                    .async { dispatches, observer: Observer<Pair<Dispatcher, List<Dispatch>>> ->
                        logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                            "Sending events to dispatcher ${dispatcher.id}: ${dispatches.successful.logDescriptions()}"
                        }

                        transformAndDispatch(dispatches, dispatcher) { completedDispatches ->
                            observer.onNext(dispatcher to completedDispatches)
                        }
                    }
            }.subscribe { (dispatcher, completedDispatches) ->
                queueManager.deleteDispatches(
                    completedDispatches,
                    dispatcher.id
                )

                logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                    "Dispatcher: ${dispatcher.id} processed events: ${completedDispatches.logDescriptions()}"
                }
            }
    }

    private fun ensureBarriersOpen(dispatcher: Dispatcher): Observable<DispatchSplit> =
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
                    .filter { it.isNotEmpty() }
                    .resubscribingWhile { it.count() >= dispatcher.dispatchLimit } // Loops the `getQueuedEvents` as long as we pull `dispatchLimit` items from the queue
            }
    }

    /**
     * Transforms the provided [dispatches] according to the current set of transformers available
     * to the [transformerCoordinator].
     *
     * Any dispatches where the [transformerCoordinator] returns null, will be dropped from the batch.
     * Dropped dispatches will not be replaced in the batch to make up the numbers.
     */
    private fun transformAndDispatch(
        dispatches: DispatchSplit,
        dispatcher: Dispatcher,
        onProcessedDispatches: (List<Dispatch>) -> Unit
    ): Disposable {
        if (dispatches.unsuccessful.isNotEmpty()) {
            onProcessedDispatches.invoke(dispatches.unsuccessful)
        }

        if (dispatches.successful.isEmpty()) {
            return CompletedDisposable
        }

        val container = DisposableContainer()
        transformerCoordinator.transform(
            dispatches.successful,
            DispatchScope.Dispatcher(dispatcher.id)
        ) { transformedDispatches ->
            if (container.isDisposed) return@transform

            val (passed, _) = loadRuleEngine.evaluateLoadRules(dispatcher, transformedDispatches)
            deleteMissingDispatches(
                dispatches.successful,
                passed,
                dispatcher.id,
                onProcessedDispatches
            )

            val mapped = passed.map { dispatch -> mappingsEngine.map(dispatcher.id, dispatch) }

            dispatcher.dispatch(mapped) { completed ->
                if (container.isDisposed) return@dispatch
                onProcessedDispatches(completed)
            }.addTo(container)
        }

        return container
    }

    private fun deleteMissingDispatches(
        original: List<Dispatch>,
        transformedDispatches: List<Dispatch>,
        dispatcherId: String,
        onProcessedDispatches: (List<Dispatch>) -> Unit
    ) {
        val missingDispatches = original.filter { oldDispatch ->
            transformedDispatches.firstOrNull { transformedDispatch -> oldDispatch.id == transformedDispatch.id } == null
        }

        if (missingDispatches.isNotEmpty()) {
            logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                "Dispatching disallowed for Dispatcher(${dispatcherId}) and Dispatches (${missingDispatches.logDescriptions()})"
            }
            onProcessedDispatches(missingDispatches)
        }
    }

    private fun isLessThanMaxInFlight(count: Int): Boolean =
        count < maxInFlightPerDispatcher

    companion object {
        const val MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER = 50
    }
}

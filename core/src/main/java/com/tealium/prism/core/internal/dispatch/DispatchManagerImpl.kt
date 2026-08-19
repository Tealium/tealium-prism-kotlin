package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfDebugEnabled
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.ObservableState
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
import com.tealium.prism.core.internal.modules.InternalModuleManager
import com.tealium.prism.core.internal.pubsub.DisposableContainer
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
        get() = moduleManager.modules.mapState { it.filterIsInstance<Dispatcher>().toSet() }

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
                    .flatMap { dispatchSplit ->
                        if (dispatchSplit.unsuccessful.isNotEmpty()) {
                            logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                                "Dispatches discarded due to consent: ${dispatchSplit.unsuccessful.logDescriptions()}"
                            }
                        }

                        transformAndDispatch(dispatchSplit.successful, dispatcher)
                            .startWith(dispatchSplit.unsuccessful)
                            .filter { it.isNotEmpty() }
                            .map { completedDispatches -> dispatcher to completedDispatches }
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
    }
}

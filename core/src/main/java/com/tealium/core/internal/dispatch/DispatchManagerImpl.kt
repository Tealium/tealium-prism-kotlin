package com.tealium.core.internal.dispatch

import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.logger.logIfDebugEnabled
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.modules.consent.ConsentDecision
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.api.tracking.TrackResultListener
import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.internal.logger.LogCategory
import com.tealium.core.internal.logger.logDescriptions
import com.tealium.core.internal.modules.InternalModuleManager
import com.tealium.core.internal.modules.consent.ConsentManager
import com.tealium.core.internal.pubsub.DisposableContainer
import com.tealium.core.internal.pubsub.addTo
import com.tealium.core.internal.rules.LoadRuleEngine

class DispatchManagerImpl(
    private val moduleManager: InternalModuleManager,
    private val barrierCoordinator: BarrierCoordinator,
    private val transformerCoordinator: TransformerCoordinator,
    private val queueManager: QueueManager,
    private val dispatchers: Observable<Set<Dispatcher>>,
    private val loadRuleEngine: LoadRuleEngine,
    private val mappingsEngine: MappingsEngine,
    private val logger: Logger,
    private val maxInFlightPerDispatcher: Int = MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER
) : DispatchManager {

    private var dispatchLoop: Disposable? = null
    private val _dispatchers: Set<Dispatcher>
        get() = moduleManager.getModulesOfType(Dispatcher::class.java).toSet()

    private val consentManager: ConsentManager?
        get() = moduleManager.getModuleOfType(ConsentManager::class.java)

    private fun tealiumPurposeExplicitlyBlocked(): Boolean {
        val consentManager = consentManager ?: return false

        val decision = consentManager.getConsentDecision()
        if (decision == null || decision.decisionType == ConsentDecision.DecisionType.Implicit)
            return false

        return !consentManager.tealiumConsented(decision.purposes)
    }

    override fun track(dispatch: Dispatch) {
        track(dispatch, null)
    }

    override fun track(dispatch: Dispatch, onComplete: TrackResultListener?) {
        if (tealiumPurposeExplicitlyBlocked()) {
            logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                "Tealium consent purpose is explicitly blocked. Event ${dispatch.logDescription()} will be dropped."
            }

            onComplete?.onTrackResultReady(TrackResult.Dropped(dispatch))
            return
        }

        transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors) { transformed ->
            if (transformed == null) {
                logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                    "Event ${dispatch.logDescription()} dropped due to transformer"
                }

                onComplete?.onTrackResultReady(TrackResult.Dropped(dispatch))
                return@transform
            }

            val consentManager = consentManager
            if (consentManager != null) {
                logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                    "Event ${transformed.logDescription()} consent applied"
                }

                consentManager.applyConsent(transformed)
                // TODO - This call may need to be moved into the Consent Manager implementation once it's done.
                onComplete?.onTrackResultReady(TrackResult.Accepted(transformed))
            } else {
                logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                    "Event ${transformed.logDescription()} accepted for processing"
                }

                queueManager.storeDispatches(
                    listOf(transformed),
                    _dispatchers.map(Dispatcher::id).toSet()
                )
                onComplete?.onTrackResultReady(TrackResult.Accepted(transformed))
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
                            "Sending events to dispatcher ${dispatcher.id}: ${dispatches.logDescriptions()}"
                        }

                        transformAndDispatch(dispatches, dispatcher) { completedDispatches ->
                            observer.onNext(Pair(dispatcher, completedDispatches))
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

    private fun ensureBarriersOpen(dispatcher: Dispatcher): Observable<List<Dispatch>> =
        barrierCoordinator.onBarriersState(dispatcher.id)
            .flatMapLatest { active ->
                logger.debug(
                    LogCategory.DISPATCH_MANAGER,
                    "BarrierState changed for %s: %s", dispatcher.id, active
                )

                if (active == BarrierState.Open) {
                    startDequeueLoop(dispatcher)
                } else {
                    Observables.empty()
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
                        queueManager.getQueuedDispatches(
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
        dispatches: List<Dispatch>,
        dispatcher: Dispatcher,
        onProcessedDispatches: (List<Dispatch>) -> Unit
    ): Disposable {
        val container = DisposableContainer()
        transformerCoordinator.transform(
            dispatches,
            DispatchScope.Dispatcher(dispatcher.id)
        ) { transformedDispatches ->
            if (container.isDisposed) return@transform

            deleteMissingDispatches(dispatches, transformedDispatches, onProcessedDispatches)

            val (passed, failed) = loadRuleEngine.evaluateLoadRules(dispatcher, transformedDispatches)
            if (failed.isNotEmpty()) {
                onProcessedDispatches(failed)
                logger.logIfDebugEnabled(LogCategory.DISPATCH_MANAGER) {
                    "Dispatching disallowed for Dispatcher(${dispatcher.id}) and Dispatches (${failed.logDescriptions()})"
                }
            }

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
        onProcessedDispatches: (List<Dispatch>) -> Unit
    ) {
        val missingDispatches = original.filter { oldDispatch ->
            transformedDispatches.firstOrNull { transformedDispatch -> oldDispatch.id == transformedDispatch.id } == null
        }

        if (missingDispatches.isNotEmpty()) {
            onProcessedDispatches(missingDispatches)
        }
    }

    private fun isLessThanMaxInFlight(count: Int): Boolean =
        count < maxInFlightPerDispatcher

    companion object {
        const val MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER = 50
    }
}

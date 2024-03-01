package com.tealium.core.internal.dispatch

import com.tealium.core.api.BarrierState
import com.tealium.core.api.ConsentDecision
import com.tealium.core.api.Dispatch
import com.tealium.core.api.DispatchScope
import com.tealium.core.api.TrackResult
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.api.listeners.TrackResultListener
import com.tealium.core.api.logger.Logger
import com.tealium.core.internal.consent.ConsentManager
import com.tealium.core.internal.observables.DisposableContainer
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.StateSubject
import com.tealium.core.internal.observables.addTo

class DispatchManagerImpl(
    private val consentManager: ConsentManager,
    private val barrierCoordinator: BarrierCoordinator,
    private val transformerCoordinator: TransformerCoordinator,
    private val queueManager: QueueManager,
    private val dispatchers: StateSubject<Set<Dispatcher>>,
    private val logger: Logger,
    private val maxInFlightPerDispatcher: Int = MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER
) {

    private var dispatchLoop: Disposable? = null

    private fun tealiumPurposeExplicitlyBlocked(): Boolean {
        if (!consentManager.enabled)
            return false

        val decision = consentManager.getConsentDecision()
        if (decision == null || decision.decisionType == ConsentDecision.DecisionType.Implicit)
            return false

        return !consentManager.tealiumConsented(decision.purposes)
    }

    fun track(dispatch: Dispatch, onComplete: TrackResultListener? = null) {
        if (tealiumPurposeExplicitlyBlocked()) {
            logger.info?.log(
                "Dispatch",
                "Tealium Purpose is explicitly blocked. Dispatch will not be sent."
            )
            onComplete?.onTrackResultReady(dispatch, TrackResult.Dropped)
            return
        }

        transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors) { transformed ->
            if (transformed == null) {
                onComplete?.onTrackResultReady(dispatch, TrackResult.Dropped)
                return@transform
            }

            if (consentManager.enabled) {
                consentManager.applyConsent(transformed)
                // TODO - This call may need to be moved into the Consent Manager implementation once it's done.
                onComplete?.onTrackResultReady(transformed, TrackResult.Accepted)
            } else {
                queueManager.storeDispatch(transformed, dispatchers.value)
                onComplete?.onTrackResultReady(dispatch, TrackResult.Accepted)
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
                barrierCoordinator.onBarriersState(dispatcher.name)
                    .flatMapLatest { active ->
                        logger.debug?.log(
                            "Dispatch",
                            "BarrierState changed for ${dispatcher.name}: $active"
                        )
                        if (active == BarrierState.Open) {
                            startDequeueLoop(dispatcher)
                        } else {
                            Observables.empty()
                        }
                    }.map { dispatcher to it }
            }.async { (dispatcher, dispatches), observer: Observer<List<Dispatch>> ->
                transformAndDispatch(dispatches, dispatcher) { completed ->
                    observer.onNext(completed)
                }
            }.subscribe { dispatches ->
                logger.debug?.log(
                    "Dispatch",
                    "Complete - ${dispatches.map { it.tealiumEvent }}"
                )
            }
    }

    private fun startDequeueLoop(dispatcher: Dispatcher): Observable<List<Dispatch>> {
        val onInflightLower = queueManager.inFlightCount(dispatcher)
            .map(::isLessThanMaxInFlight)
            .distinct()
        return queueManager.onEnqueuedEvents.startWith(Unit)
            .flatMapLatest { _ ->
                onInflightLower
                    .map { _ -> queueManager.getQueuedEvents(dispatcher, dispatcher.dispatchLimit) }
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
        completion: (List<Dispatch>) -> Unit
    ): Disposable {
        val container = DisposableContainer()
        transformerCoordinator.transform(
            dispatches,
            DispatchScope.Dispatcher(dispatcher.name)
        ) { transformedDispatches ->
            if (container.isDisposed) return@transform

            deleteMissingDispatches(dispatches, transformedDispatches, dispatcher)

            dispatcher.dispatch(transformedDispatches).subscribe { completed ->
                if (container.isDisposed) return@subscribe

                queueManager.deleteDispatches(
                    completed,
                    dispatcher
                )
                logger.debug?.log(
                    "Dispatch",
                    "${dispatcher.name}: Deleted: ${completed.map { it.tealiumEvent }}"
                )
                completion(transformedDispatches)
            }.addTo(container)
        }

        return container
    }

    private fun deleteMissingDispatches(
        original: List<Dispatch>,
        transformedDispatches: List<Dispatch>,
        dispatcher: Dispatcher
    ) {
        val missingDispatches = original.filter { oldDispatch ->
            transformedDispatches.firstOrNull { transformedDispatch -> oldDispatch.id == transformedDispatch.id } == null
        }

        if (missingDispatches.isNotEmpty()) {
            queueManager.deleteDispatches(
                missingDispatches,
                dispatcher
            )
        }
    }

    private fun isLessThanMaxInFlight(count: Int): Boolean =
        count < maxInFlightPerDispatcher

    companion object {
        const val MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER = 50

        private fun extractCount(enqueuedEvents: Unit, count: Int) = count
    }
}

package com.tealium.core.internal.dispatch

import com.tealium.core.api.BarrierState
import com.tealium.core.api.ConsentDecision
import com.tealium.core.api.Dispatch
import com.tealium.core.api.DispatchScope
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.logger.Logger
import com.tealium.core.internal.consent.ConsentManager
import com.tealium.core.internal.flatMapLatest
import com.tealium.core.internal.flatMapMerge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class DispatchManagerImpl(
    private val consentManager: ConsentManager,
    private val barrierCoordinator: BarrierCoordinator,
    private val transformerCoordinator: TransformerCoordinator,
    private val queueManager: QueueManager,
    private val dispatchers: StateFlow<Set<Dispatcher>>,
    private val tealiumScope: CoroutineScope,
    private val logger: Logger,
    private val maxInFlightPerDispatcher: Int = MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER
) {

    private var dispatchLoop: Job? = null

    private fun tealiumPurposeExplicitlyBlocked(): Boolean {
        if (!consentManager.enabled)
            return false

        val decision = consentManager.getConsentDecision()
        if (decision == null || decision.decisionType == ConsentDecision.DecisionType.Implicit)
            return false

        return !consentManager.tealiumConsented(decision.purposes)
    }

    suspend fun track(dispatch: Dispatch) {
        if (tealiumPurposeExplicitlyBlocked()) {
            logger.info?.log(
                "Dispatch",
                "Tealium Purpose is explicitly blocked. Dispatch will not be sent."
            )
            return
        }

        val transformed =
            transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors) ?: return

        if (consentManager.enabled) {
            consentManager.applyConsent(transformed)
        } else {
            queueManager.storeDispatch(dispatch, dispatchers.value)
        }
    }

    internal fun stopDispatchLoop() {
        dispatchLoop?.cancel()
    }

    internal fun startDispatchLoop() {
        dispatchLoop = tealiumScope.launch {
            dispatchers.flatMapLatest { dispatchers ->
                dispatchers.asFlow()
            }.flatMapMerge { dispatcher ->
                barrierCoordinator.onBarriersState(dispatcher.name)
                    .flatMapLatest { active ->
                        logger.debug?.log(
                            "Dispatch",
                            "BarrierState changed for ${dispatcher.name}: $active"
                        )
                        if (active == BarrierState.Open) {
                            startTransformAndDispatchLoop(dispatcher)
                        } else {
                            emptyFlow()
                        }
                    }.map { dispatcher to it }
            }.onEach { (dispatcher, dispatches) ->
                // Launch as a separate coroutine to keep dispatch
                // in-flight even after the flow is cancelled
                launch(SupervisorJob()) {
                    dispatch(dispatcher, dispatches)
                }
            }.collect { dispatches ->
                logger.debug?.log(
                    "Dispatch",
                    "Dispatched - ${dispatches.second.map { it.tealiumEvent }} to ${dispatches.first.name}"
                )
            }
        }
    }

    /**
     * Creates the queued events Flow for the given [dispatcher].
     *
     * The returned flow will emit batches of [Dispatch]es under the following circumstances:
     *  - There are still some dispatches queued
     *  - The limit for the number of dispatches "in-flight" is not yet reached
     *
     * The Dispatches emitted are already transformed, will there will be at most, [Dispatcher.dispatchLimit]
     * dispatches in each batch. It could be fewer, due to the Transformers ability to drop dispatches.
     *
     * @param dispatcher The specific dispatcher for this Flow to be set up for
     */
    private fun startTransformAndDispatchLoop(dispatcher: Dispatcher): Flow<List<Dispatch>> {
        return queueManager.onEnqueuedEvents.onStart { emit(Unit) }
            .combine(queueManager.inFlightCount(dispatcher), ::extractCount)
            .filter(::isLessThanMaxInFlight)
            .mapNotNull { queueManager.getQueuedEvents(dispatcher, dispatcher.dispatchLimit) }
            .filter { it.isNotEmpty() }
            .map { transform(it, dispatcher) }
    }

    /**
     * Transforms the provided [dispatches] according to the current set of transformers available
     * to the [transformerCoordinator].
     *
     * Any dispatches where the [transformerCoordinator] returns null, will be dropped from the batch.
     * Dropped dispatches will not be replaced in the batch to make up the numbers.
     */
    private suspend fun transform(
        dispatches: List<Dispatch>,
        dispatcher: Dispatcher
    ): List<Dispatch> {
        val transformedDispatches =
            transformerCoordinator.transform(dispatches, DispatchScope.Dispatcher(dispatcher.name))

        val missingDispatches = dispatches.filter { oldDispatch ->
            transformedDispatches.firstOrNull { transformedDispatch -> oldDispatch.id == transformedDispatch.id } == null
        }

        if (missingDispatches.isNotEmpty()) {
            queueManager.deleteDispatches(
                missingDispatches,
                dispatcher
            )
        }

        return transformedDispatches
    }

    /**
     * Asynchronously dispatch the [dispatches] to the given [dispatcher].
     *
     * The coroutine will only continue once all Dispatches have been marked as completed by way of
     * the [dispatcher] executing its completion block for all given [dispatches]
     */
    private suspend fun dispatch(
        dispatcher: Dispatcher,
        dispatches: List<Dispatch>,
    ) {
        val incomplete = dispatches.map { it.id }.toMutableList()

        dispatcher.dispatch(dispatches)
            .onCompletion {
                if (incomplete.isNotEmpty()) {
                    // TODO - delete incomplete? or leave in queue for next time?
                    logger.info?.log(
                        "Dispatch",
                        "${dispatcher.name}: The following dispatches were not marked as completed: $incomplete"
                    )
                }
            }
            .collect { completed ->
                queueManager.deleteDispatches(
                    completed,
                    dispatcher
                )
                logger.debug?.log(
                    "Dispatch",
                    "${dispatcher.name}: Deleted: ${completed.map { it.tealiumEvent }}"
                )

                incomplete.removeAll(completed.map { it.id })
            }
    }
    private fun isLessThanMaxInFlight(count: Int): Boolean =
        count < maxInFlightPerDispatcher

    companion object {
        const val MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER = 50


        private fun extractCount(enqueuedEvents: Unit, count: Int) = count
    }
}
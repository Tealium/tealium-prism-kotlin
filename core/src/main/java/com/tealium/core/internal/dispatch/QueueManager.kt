package com.tealium.core.internal.dispatch

import com.tealium.core.api.Dispatch
import com.tealium.core.api.PersistenceException
import com.tealium.core.api.logger.Logger
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.StateSubject
import com.tealium.core.internal.observables.Subject
import com.tealium.core.internal.persistence.QueueRepository
import com.tealium.core.internal.persistence.days
import com.tealium.core.internal.settings.CoreSettings
import java.lang.Exception

interface QueueManager {

    /**
     * An observable stream to notify that there has been new [Dispatch]es added to the queue. The
     * emitted value is the set of processor names that have had new events stored.
     */
    val enqueuedDispatchesForProcessors: Observable<Set<String>>

    /**
     * Returns an observable stream of the number of dispatches currently in-flight for a given [processor]
     *
     * @param processor The processor whose event count is to be observed.
     */
    fun inFlightCount(processor: String): Observable<Int>

    /**
     * Returns the oldest [count] dispatches for the given [processor] and sets them as "in-flight"
     * such that they will not be returned again (unless they are not marked as processed before the
     * app closes)
     *
     * @param count The maximum number of queued [Dispatch]es to return. If value is negative, then all
     * entries will be returned.
     * @param processor The name of the processor whose dispatches are being retrieved
     */
    fun getQueuedDispatches(count: Int, processor: String): List<Dispatch>

    /**
     * Adds the [dispatches] to the queue, creating entries for all [processors]s provided.
     * Note. when executed with existing [dispatches], any entries currently in the queue will be
     * removed.
     *
     * @param dispatches The dispatches to persist in case we can't yet send them.
     * @param processors The list of processors to save the [dispatches] for
     */
    fun storeDispatches(dispatches: List<Dispatch>, processors: Set<String>)

    /**
     * Removes the given [dispatches] from the queue, only for the given [processor].
     *
     * @param dispatches The list of [Dispatch] to remove from the queue
     * @param processor The name of the processor whose dispatches are to be removed
     */
    fun deleteDispatches(dispatches: List<Dispatch>, processor: String)

    /**
     * Removes all [Dispatch] entries currently stored for the given [processor]
     *
     * @param processor The name of the processor to clear all stored dispatches.
     */
    fun deleteAllDispatches(processor: String)
}

class QueueManagerImpl(
    private val queueRepository: QueueRepository,
    settings: Observable<CoreSettings>,
    processors: Observable<Set<String>>,
    private val inFlightDispatches: StateSubject<Map<String, Set<Dispatch>>> = Observables.stateSubject(
        mapOf()
    ),
    private val _enqueuedDispatches: Subject<Set<String>> = Observables.publishSubject(),
    private val logger: Logger? = null
) : QueueManager {

    init {
        settings.subscribe(::handleSettingsUpdate)
        processors.subscribe(::deleteQueues)
    }

    override val enqueuedDispatchesForProcessors: Observable<Set<String>>
        get() = _enqueuedDispatches.asObservable()

    override fun inFlightCount(processor: String): Observable<Int> {
        return inFlightDispatches.map { inFlight ->
            inFlight[processor]?.size ?: 0
        }.distinct()
    }

    override fun getQueuedDispatches(count: Int, processor: String): List<Dispatch> {
        val dispatches = queueRepository.getQueuedDispatches(
            count,
            inFlightDispatches.value[processor] ?: emptySet(),
            processor
        )

        if (dispatches.isEmpty()) return emptyList()
        logger?.debug?.log(
            "QueueManager",
            "Dequeued dispatches for processor ($processor): (${dispatches.map(Dispatch::logDescription)})"
        )

        addToInflightDispatches(processor, dispatches)

        return dispatches
    }

    override fun storeDispatches(dispatches: List<Dispatch>, processors: Set<String>) {
        if (dispatches.isEmpty() || processors.isEmpty()) return

        try {
            queueRepository.storeDispatch(dispatches, processors)
            logger?.debug?.log(
                "QueueManager",
                "Enqueued dispatches for processors ($processors): ${dispatches.map(Dispatch::logDescription)})"
            )

            _enqueuedDispatches.onNext(processors)
        } catch (ex: PersistenceException) {
            logger?.error?.log(
                "QueueManager",
                "Failed to enqueue dispatch for processors ($processors): ${dispatches.map(Dispatch::logDescription)} \nError: ${ex.message})"
            )
        }
    }

    override fun deleteDispatches(dispatches: List<Dispatch>, processor: String) {
        try {
            queueRepository.deleteDispatches(dispatches, processor)
            logger?.debug?.log(
                "QueueManager",
                "Removed processed dispatches for processor ($processor): ${dispatches.map(Dispatch::id)}"
            )

            removeFromInflightDispatches(processor, dispatches)
        } catch (ex: PersistenceException) {
            logger?.error?.log(
                "QueueManager",
                "Failed to remove processed dispatches for processor ($processor): (${
                    dispatches.map(
                        Dispatch::id
                    )
                }) \nError: (${ex.message})"
            )
        }
    }

    override fun deleteAllDispatches(processor: String) {
        try {
            queueRepository.deleteAllDispatches(processor)
            logger?.debug?.log(
                "QueueManager",
                "Removed all processed dispatches for processor ($processor)"
            )

            removeAllInflightDispatches(processor)
        } catch (ex: PersistenceException) {
            logger?.error?.log(
                "QueueManager",
                "Failed to remove all processed dispatches for processor ($processor) \nError: (${ex.message})"
            )
        }
    }

    private fun deleteQueues(currentProcessors: Set<String>) {
        queueRepository.deleteQueues(forProcessorsNotIn = currentProcessors)

        inFlightDispatches.value.keys.filter { !currentProcessors.contains(it) }
            .forEach { missingProcessor ->
                removeAllInflightDispatches(missingProcessor)
            }
    }

    private fun handleSettingsUpdate(coreSettings: CoreSettings) {
        try {
            queueRepository.resize(coreSettings.maxQueueSize)
            logger?.debug?.log(
                "QueueManager",
                "Resized the queue to (${coreSettings.maxQueueSize}) and deleted eventual overflowing dispatches")
        } catch (ex: Exception) {
            logger?.error?.log("QueueManager","Failed to dispatches overflowing the maxQueueSize of (${coreSettings.maxQueueSize})\nError: (${ex.message})")
        }

        try {
            queueRepository.setExpiration(coreSettings.expiration.days)
            logger?.debug?.log("QueueManager", "Set Queue Expiration to (settings.queueExpiration) and deleted all expired dispatches")
        } catch (e: Exception) {
            logger?.error?.log("QueueManager", "Failed to delete expired dispatches for expiration (settings.queueExpiration)\nError: (error)")
        }
    }

    private fun addToInflightDispatches(processor: String, dispatches: List<Dispatch>) {
        val inFlight = inFlightDispatches.value.toMutableMap()

        inFlight[processor] = (inFlight[processor]?.toMutableSet() ?: mutableSetOf()).apply {
            addAll(dispatches.toMutableSet())
        }

        inFlightDispatches.onNext(inFlight)
    }

    private fun removeFromInflightDispatches(processor: String, dispatches: List<Dispatch>) {
        if (dispatches.isEmpty()) return
        val inFlight = inFlightDispatches.value.toMutableMap()

        inFlight[processor] = inFlight[processor]?.filter { dispatch ->
            dispatches.find { it.id == dispatch.id } == null
        }?.toMutableSet() ?: mutableSetOf()

        inFlightDispatches.onNext(inFlight)
    }

    private fun removeAllInflightDispatches(processor: String) {
        val inFlight = inFlightDispatches.value.toMutableMap()

        inFlight[processor] = mutableSetOf()

        inFlightDispatches.onNext(inFlight)
    }
}
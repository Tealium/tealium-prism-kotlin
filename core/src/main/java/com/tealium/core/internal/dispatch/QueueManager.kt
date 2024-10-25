package com.tealium.core.internal.dispatch

import com.tealium.core.api.logger.Logger
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.persistence.PersistenceException
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.api.settings.CoreSettings
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.internal.logger.LogCategory
import com.tealium.core.internal.logger.ids
import com.tealium.core.api.logger.logIfDebugEnabled
import com.tealium.core.api.logger.logIfErrorEnabled
import com.tealium.core.internal.logger.logDescriptions
import com.tealium.core.internal.logger.nonNullMessage
import com.tealium.core.internal.persistence.repositories.QueueRepository

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
    private val logger: Logger
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
        logger.logIfDebugEnabled(LogCategory.QUEUE_MANAGER) {
            "Dequeued dispatches for processor ($processor): (${dispatches.logDescriptions()})"
        }

        addToInflightDispatches(processor, dispatches)

        return dispatches
    }

    override fun storeDispatches(dispatches: List<Dispatch>, processors: Set<String>) {
        if (dispatches.isEmpty() || processors.isEmpty()) return

        try {
            queueRepository.storeDispatch(dispatches, processors)
            logger.logIfDebugEnabled(LogCategory.QUEUE_MANAGER) {
                "Enqueued dispatches for processors ($processors): (${dispatches.logDescriptions()})"
            }

            _enqueuedDispatches.onNext(processors)
        } catch (ex: PersistenceException) {
            logger.logIfErrorEnabled(LogCategory.QUEUE_MANAGER) {
                "Failed to enqueue dispatches for processors ($processors): ${dispatches.logDescriptions()}\nError: ${ex.message})"
            }
        }
    }

    override fun deleteDispatches(dispatches: List<Dispatch>, processor: String) {
        try {
            queueRepository.deleteDispatches(dispatches, processor)
            logger.logIfDebugEnabled(LogCategory.QUEUE_MANAGER) {
                "Removed processed dispatches for processor ($processor): (${dispatches.ids()})"
            }

            removeFromInflightDispatches(processor, dispatches)
        } catch (ex: PersistenceException) {
            logger.logIfErrorEnabled(LogCategory.QUEUE_MANAGER) {
                "Failed to remove processed dispatches for processor ($processor): (${dispatches.ids()})\nError: ${ex.message}"
            }
        }
    }

    override fun deleteAllDispatches(processor: String) {
        try {
            queueRepository.deleteAllDispatches(processor)
            logger.debug(
                LogCategory.QUEUE_MANAGER,
                "Removed all processed dispatches for processor (%s)", processor
            )

            removeAllInflightDispatches(processor)
        } catch (ex: PersistenceException) {
            logger.error(
                LogCategory.QUEUE_MANAGER,
                "Failed to remove all processed dispatches for processor (%s)\nError: %s",
                processor,
                ex.nonNullMessage()
            )
        }
    }

    private fun deleteQueues(currentProcessors: Set<String>) {
        try {
            queueRepository.deleteQueues(forProcessorsNotIn = currentProcessors)
            logger.debug(
                LogCategory.QUEUE_MANAGER,
                "Deleted queued events for disabled processors. Currently enabled processors are: %s",
                currentProcessors
            )

            inFlightDispatches.value.keys.filter { !currentProcessors.contains(it) }
                .forEach { missingProcessor ->
                    removeAllInflightDispatches(missingProcessor)
                }
        } catch (ex: PersistenceException) {
            logger.error(
                LogCategory.QUEUE_MANAGER,
                "Failed to delete queued events for disabled processors. Currently enabled processors are: %s\nError: %s",
                currentProcessors,
                ex.nonNullMessage()
            )
        }
    }

    private fun handleSettingsUpdate(coreSettings: CoreSettings) {
        try {
            queueRepository.resize(coreSettings.maxQueueSize)
            logger.debug(
                LogCategory.QUEUE_MANAGER,
                "Resized the queue to (%s) and deleted eventual overflowing dispatches",
                coreSettings.maxQueueSize
            )
        } catch (ex: PersistenceException) {
            logger.error(
                LogCategory.QUEUE_MANAGER,
                "Failed to delete dispatches exceeding the maxQueueSize of (%s)\nError: %s",
                coreSettings.maxQueueSize,
                ex.nonNullMessage()
            )
        }

        val expiration: TimeFrame = coreSettings.expiration
        try {
            queueRepository.setExpiration(expiration)
            logger.debug(
                LogCategory.QUEUE_MANAGER,
                "Set Queue Expiration to %s and deleted all expired dispatches", expiration
            )
        } catch (ex: PersistenceException) {
            logger.error(
                LogCategory.QUEUE_MANAGER,
                "Failed to delete expired dispatches for expiration %s\nError: %s",
                expiration,
                ex.nonNullMessage()
            )
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
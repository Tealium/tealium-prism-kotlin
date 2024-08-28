package com.tealium.core.internal.persistence.repositories

import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.persistence.PersistenceException
import com.tealium.core.api.settings.CoreSettings
import com.tealium.core.api.tracking.Dispatch

/**
 * A repository for managing the persistence of [Dispatch] items to ensure they are not dropped when
 * a processor is unable to process them immediately.
 *
 * This repository supports creating, reading, and deleting [Dispatch] items, associated to given
 * processors.
 */
interface QueueRepository {

    /**
     * Returns the current size of the queue considering all dispatches that have not been fully
     * processed.
     * If a dispatch has remained unprocessed by any of the processors it is registered for, then
     * this property will include it in the returned size.
     *
     * @returns The number of dispatches that are not completely processed
     */
    val size: Int

    /**
     * Adds the [dispatches] to the queue, creating entries for all [processors]s provided.
     * Note. when executed with existing [dispatches], any entries currently in the queue will be
     * removed.
     *
     * @param dispatches The dispatches to persist in case we can't yet send them.
     * @param processors The list of processors to save the [dispatches] for
     */
    @Throws(PersistenceException::class)
    fun storeDispatch(dispatches: List<Dispatch>, processors: Set<String>)

    /**
     * Returns the oldest [count] dispatches for the given [processor].
     *
     * @param count The maximum number of queued [Dispatch]es to return. If value is negative, then all
     * entries will be returned.
     * @param processor The name of the processor whose dispatches are being retrieved
     */
    fun getQueuedDispatches(count: Int, processor: String): List<Dispatch>

    /**
     * Returns the oldest [count] dispatches for the given [processor].
     *
     * @param count The maximum number of queued [Dispatch]es to return. If value is negative, then all
     * entries will be returned.
     * @param excluding The list of dispatches not to be included in the results
     * @param processor The name of the processor whose dispatches are being retrieved
     */
    fun getQueuedDispatches(count: Int, excluding: Set<Dispatch>, processor: String): List<Dispatch>

    /**
     * Removes the given [dispatch] from the queue, only for the given [processor].
     *
     * @param dispatch The [Dispatch] to remove from the queue
     * @param processor The name of the processor whose dispatches are to be removed
     */
    fun deleteDispatch(dispatch: Dispatch, processor: String)

    /**
     * Removes the given [dispatches] from the queue, only for the given [processor].
     *
     * @param dispatches The list of [Dispatch] to remove from the queue
     * @param processor The name of the processor whose dispatches are to be removed
     */
    @Throws(PersistenceException::class)
    fun deleteDispatches(dispatches: List<Dispatch>, processor: String)

    /**
     * Removes all [Dispatch] entries currently stored for the given [processor]
     *
     * @param processor The name of the processor to clear all stored dispatches.
     */
    @Throws(PersistenceException::class)
    fun deleteAllDispatches(processor: String)

    /**
     * Removes all queue entries for any processor **not** in the given list of [forProcessorsNotIn] processors.
     * That is, it should be called with the current set of processors, thus removing queue entries
     * for processors that are no longer in use.
     *
     * @param forProcessorsNotIn The current set of processors
     */
    @Throws(PersistenceException::class)
    fun deleteQueues(forProcessorsNotIn: Set<String>)

    /**
     * Updates the maximum queue size, deleting the oldest entries where necessary
     *
     * @param newSize The new maximum size that the queue can extend to.
     */
    @Throws(PersistenceException::class)
    fun resize(newSize: Int)

    /**
     * Updates the Dispatch expiration that determines how long a Dispatch can remain in the queue
     * for.
     *
     * @see [CoreSettings.expiration]
     */
    @Throws(PersistenceException::class)
    fun setExpiration(expiration: TimeFrame)
}


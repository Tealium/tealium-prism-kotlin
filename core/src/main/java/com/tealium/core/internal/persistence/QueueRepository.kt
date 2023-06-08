package com.tealium.core.internal.persistence

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher

interface QueueRepository {

    /**
     * Returns the current size of the queue considering all dispatches that have not been fully
     * processed.
     * If a dispatch has remained unprocessed by any of the Dispatchers it is registered for, then
     * this property will include it in the returned size.
     *
     * @returns The number of dispatches that are not completely processed
     */
    val size: Int

    /**
     * Sets the given [dispatchers] as active, marking any others as inactive.
     *
     * @param dispatchers The dispatchers to start receiving events.
     */
    fun updateDispatchers(dispatchers: List<Dispatcher>)

    /**
     * Adds the [dispatch]es to the queue, creating entries for all [Dispatcher]s that are currently
     * registered and active.
     *
     * @param dispatch The dispatch to persist in case we can't yet send it.
     */
    fun enqueue(dispatch: Dispatch)

    /**
     * Adds the [dispatches] to the queue, creating entries for all [Dispatcher]s that are currently
     * registered and active.
     *
     * @param dispatches The dispatches to persist in case we can't yet send them.
     */
    fun enqueue(dispatches: List<Dispatch>)

    /**
     * Returns the oldest [count] dispatches for the given [dispatcher].
     *
     * @param count The maximum number of queued [Dispatch]es to return. If value is negative, then all
     * entries will be returned.
     */
    fun getQueuedDispatches(count: Int, dispatcher: Dispatcher): List<Dispatch>

    /**
     * Removes the given [dispatches] from the queue. This will remove all entries that it has for
     * all [Dispatcher]s
     *
     * @param dispatches The [Dispatch] to remove from the queue
     */
    // TODO - not sure this is actually required yet.
    fun deleteDispatches(dispatches: List<Dispatch>)

    /**
     * Removes the given [dispatches] from the queue, only for the given [dispatcher].
     *
     *
     * @param dispatches The [Dispatch] to remove from the queue
     */
    fun deleteDispatch(dispatcher: Dispatcher, dispatch: Dispatch)

    /**
     * Removes the given [dispatches] from the queue, only for the given [dispatcher].
     *
     *
     * @param dispatches The [Dispatch] to remove from the queue
     */
    fun deleteDispatches(dispatcher: Dispatcher, dispatches: List<Dispatch>)

    /**
     * Updates the maximum queue size, deleting the oldest entries where necessary
     *
     * @param newSize The new maximum size that the queue can extend to.
     */
    fun resize(newSize: Int)
}


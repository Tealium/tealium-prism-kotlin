package com.tealium.core.internal.persistence

import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.internal.persistence.repositories.QueueRepository
import kotlin.math.min

class VolatileQueueRepository(
    private val queue: MutableMap<String, MutableSet<Dispatch>>
) : QueueRepository {
    override val size: Int
        get() = 0 // TODO("Not yet implemented")


    override fun storeDispatch(dispatches: List<Dispatch>, processors: Set<String>) {
        processors.forEach { processor ->
            queue[processor] = (queue[processor] ?: mutableSetOf()).apply {
                addAll(dispatches)
            }
        }
    }

    override fun getQueuedDispatches(count: Int, processor: String): List<Dispatch> =
        getQueuedDispatches(count, setOf(), processor)


    override fun getQueuedDispatches(
        count: Int,
        excluding: Set<Dispatch>,
        processor: String
    ): List<Dispatch> {
        val notExcluded = queue[processor]?.filter { !excluding.contains(it) }
        return notExcluded?.subList(0, min(notExcluded.size, count))
            ?: emptyList()
    }

    override fun deleteDispatch(dispatch: Dispatch, processor: String) {
        queue[processor]?.remove(dispatch)
    }

    override fun deleteDispatches(dispatches: List<Dispatch>, processor: String) {
        queue[processor]?.removeAll(dispatches)
    }

    override fun deleteAllDispatches(processor: String) {
        queue[processor]?.clear()
    }

    override fun deleteQueues(forProcessorsNotIn: Set<String>) {
        queue.forEach { (key, value) ->
            if (!forProcessorsNotIn.contains(key)) {
                value.clear()
            }
        }
    }

    override fun resize(newSize: Int) {
        // TODO("Not yet implemented")
    }

    override fun setExpiration(expiration: TimeFrame) {
        // TODO("Not yet implemented")
    }
}
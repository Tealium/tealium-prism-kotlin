package com.tealium.core.internal.persistence

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.TealiumDispatchType
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.getDefaultConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QueueRepositoryTests {

    lateinit var app: Application
    lateinit var queueRepository: QueueRepositoryImpl

    lateinit var dispatch1: Dispatch
    lateinit var dispatch2: Dispatch
    lateinit var dispatcher1: Dispatcher
    lateinit var dispatcher2: Dispatcher
    lateinit var dispatcher3: Dispatcher

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext() as Application
        val config = getDefaultConfig(app)

        // use-in-memory DB
        val inMemoryDbProvider = InMemoryDatabaseProvider(config)
        // Repositories
        queueRepository = QueueRepositoryImpl(
            inMemoryDbProvider
        )

        dispatch1 = Dispatch.create(
            eventName = "dispatch1",
            type = TealiumDispatchType.Event,
            bundle = TealiumBundle.create {
                put("key1", "string1")
            }
        )
        dispatch2 = Dispatch.create(
            eventName = "dispatch2",
            type = TealiumDispatchType.View,
            bundle = TealiumBundle.create {
                put("key2", "string2")
            }
        )
        dispatcher1 = TestDispatcher(name = "dispatcher_1")
        dispatcher2 = TestDispatcher(name = "dispatcher_2")
        dispatcher3 = TestDispatcher(name = "dispatcher_3")

        prePopulateDispatchers()
    }

    @Test
    fun enqueue_CreatesEntries_ForAllDispatchers() {
        queueRepository.enqueue(dispatch1)
        val dispatches1 = queueRepository.getQueuedDispatches(1, dispatcher1)
        val dispatches2 = queueRepository.getQueuedDispatches(1, dispatcher2)

        assertEquals(dispatches1.size, dispatches2.size)
        assertEquals(dispatches1.first().id, dispatches2.first().id)
    }

    @Test
    fun deleteForDispatcher_DeletesEntries_ForIndividualDispatcher() {
        queueRepository.enqueue(dispatch1)
        queueRepository.enqueue(dispatch2)
        var dispatches1 = queueRepository.getQueuedDispatches(2, dispatcher1)
        var dispatches2 = queueRepository.getQueuedDispatches(2, dispatcher2)

        assertEquals(2, dispatches1.size)
        assertEquals(2, dispatches2.size)
        assertEquals(dispatches1.first().id, dispatches2.first().id)

        queueRepository.deleteDispatch(dispatcher1, dispatch1)
        dispatches1 = queueRepository.getQueuedDispatches(1, dispatcher1)
        dispatches2 = queueRepository.getQueuedDispatches(1, dispatcher2)

        assertNotEquals(dispatches1.first().id, dispatches2.first().id)
    }

    @Test
    fun deleteForDispatcher_DeletesEntries_ButOnlyReducesQueueSizeWhenAllQueueEntriesProcessed() {
        queueRepository.enqueue(dispatch1)
        queueRepository.enqueue(dispatch2)
        assertEquals(2, queueRepository.size)
        assertEquals(6, queueRepository.dispatcherQueueSize())

        queueRepository.deleteDispatch(dispatcher1, dispatch1)
        assertEquals(2, queueRepository.size)
        assertEquals(5, queueRepository.dispatcherQueueSize())

        queueRepository.deleteDispatch(dispatcher2, dispatch1)
        queueRepository.deleteDispatch(dispatcher3, dispatch1)
        assertEquals(3, queueRepository.dispatcherQueueSize())
        assertEquals(1, queueRepository.size)
    }

    @Test
    fun updateDispatchers_DisablesMissingDispatchers() {
        queueRepository.updateDispatchers(
            listOf(dispatcher1, dispatcher2) // disabling dispatcher3
        )
        queueRepository.enqueue(dispatch1)

        val dispatches1 = queueRepository.getQueuedDispatches(1, dispatcher1)
        val dispatches2 = queueRepository.getQueuedDispatches(1, dispatcher2)
        val dispatches3 = queueRepository.getQueuedDispatches(1, dispatcher3)

        assertEquals(1, dispatches1.size)
        assertEquals(1, dispatches2.size)
        assertEquals(0, dispatches3.size)
    }

    @Test
    fun updateDispatchers_InsertsNewDispatchers() {
        val dispatcher4 = TestDispatcher("dispatcher4")
        queueRepository.updateDispatchers(
            listOf(dispatcher1, dispatcher2, dispatcher3, dispatcher4)
        )
        queueRepository.enqueue(dispatch1)

        val dispatches4 = queueRepository.getQueuedDispatches(1, dispatcher4)

        assertEquals(1, dispatches4.size)
    }

    @Test
    fun resize_RemovesOldestDispatchesFirst() {
        queueRepository.enqueue(listOf(dispatch1, dispatch2))
        assertEquals(2, queueRepository.size)

        queueRepository.resize(1) // deletes dispatch1
        val dispatches = queueRepository.getQueuedDispatches(1, dispatcher1)

        assertEquals(1, queueRepository.size)
        assertEquals(dispatches.first().id, dispatch2.id)
    }

    @Test
    fun enqueue_RemovesOldestDispatches_IfSpaceRequired() {
        queueRepository.enqueue(listOf(dispatch1, dispatch2))
        assertEquals(2, queueRepository.size)

        queueRepository.resize(2)
        val dispatch3 = Dispatch.create(
            eventName = "dispatch3",
            type = TealiumDispatchType.Event,
            bundle = TealiumBundle.create {
                put("key1", "string1")
            }
        )
        queueRepository.enqueue(listOf(dispatch3)) // removes dispatch1
        val dispatches = queueRepository.getQueuedDispatches(2, dispatcher1)

        assertEquals(2, queueRepository.size)
        assertEquals(dispatches.first().id, dispatch2.id)
        assertEquals(dispatches.last().id, dispatch3.id)
    }

    private fun prePopulateDispatchers() {
        queueRepository.updateDispatchers(
            listOf(dispatcher1, dispatcher2, dispatcher3)
        )
    }
}
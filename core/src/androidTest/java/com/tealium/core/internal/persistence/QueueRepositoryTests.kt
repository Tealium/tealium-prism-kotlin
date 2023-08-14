package com.tealium.core.internal.persistence

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.TealiumDispatchType
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.internal.persistence.DatabaseTestUtils.upgrade
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.getDefaultConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

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
    fun updateDispatchers_InsertDoesntAffectExistingDispatchers() {
        val dispatcher4 = TestDispatcher("dispatcher4")
        queueRepository.updateDispatchers(
            listOf(dispatcher1, dispatcher2, dispatcher3, dispatcher4)
        )
        queueRepository.enqueue(dispatch1)

        val dispatches1 = queueRepository.getQueuedDispatches(1, dispatcher1)
        val dispatches2 = queueRepository.getQueuedDispatches(1, dispatcher2)
        val dispatches3 = queueRepository.getQueuedDispatches(1, dispatcher3)
        val dispatches4 = queueRepository.getQueuedDispatches(1, dispatcher4)

        assertEquals(1, dispatches1.size)
        assertEquals(1, dispatches2.size)
        assertEquals(1, dispatches3.size)
        assertEquals(1, dispatches4.size)
    }

    @Test
    fun updateDispatchers_DeletesOnlyQueueEntriesForRemovedDispatchers() {
        queueRepository.enqueue(dispatch1)
        var dispatches1 = queueRepository.getQueuedDispatches(1, dispatcher1)
        var dispatches2 = queueRepository.getQueuedDispatches(1, dispatcher2)
        var dispatches3 = queueRepository.getQueuedDispatches(1, dispatcher3)
        assertEquals(1, dispatches1.size)
        assertEquals(1, dispatches2.size)
        assertEquals(1, dispatches3.size)

        queueRepository.updateDispatchers( // remove dispatcher3
            listOf(dispatcher1, dispatcher2)
        )

        dispatches1 = queueRepository.getQueuedDispatches(1, dispatcher1)
        dispatches2 = queueRepository.getQueuedDispatches(1, dispatcher2)
        dispatches3 = queueRepository.getQueuedDispatches(1, dispatcher3)

        assertEquals(1, dispatches1.size)
        assertEquals(1, dispatches2.size)
        assertEquals(0, dispatches3.size)
    }

    @Test
    fun updateDispatchers_DeletesQueueEntriesForRemovedDispatchers_AndAreNotAvailableIfReenabled() {
        queueRepository.enqueue(dispatch1)
        var dispatches3 = queueRepository.getQueuedDispatches(1, dispatcher3)
        assertEquals(1, dispatches3.size)

        queueRepository.updateDispatchers( // remove dispatcher3
            listOf(dispatcher1, dispatcher2)
        )

        dispatches3 = queueRepository.getQueuedDispatches(1, dispatcher3)
        assertEquals(0, dispatches3.size)

        queueRepository.updateDispatchers( // enable dispatcher3 again
            listOf(dispatcher1, dispatcher2, dispatcher3)
        )

        dispatches3 = queueRepository.getQueuedDispatches(1, dispatcher3)
        assertEquals(0, dispatches3.size)
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

    @Test
    fun size_DoesNotInclude_ExpiredEntries() {
        queueRepository.enqueue(listOf(dispatch1, dispatch2))
        assertEquals(2, queueRepository.size)

        val expiredDispatch = Dispatch.create(
            "expired",
            dispatch1.payload(),
            QueueRepositoryImpl.getExpiryTimestamp(queueRepository.expiration) - 1
        )
        queueRepository.enqueue(listOf(expiredDispatch!!))

        assertEquals(2, queueRepository.size)
    }

    @Test
    fun getQueuedDispatches_DoesNotInclude_ExpiredEntries() {
        queueRepository.enqueue(listOf(dispatch1, dispatch2))
        assertEquals(2, queueRepository.size)

        val expiredDispatch = Dispatch.create(
            "expired",
            dispatch1.payload(),
            QueueRepositoryImpl.getExpiryTimestamp(queueRepository.expiration) - 1
        )
        queueRepository.enqueue(listOf(expiredDispatch!!))

        val dispatches1 = queueRepository.getQueuedDispatches(3, dispatcher1)
        val dispatches2 = queueRepository.getQueuedDispatches(3, dispatcher2)

        assertEquals(2, dispatches1.size)
        assertEquals(2, dispatches2.size)
        assertNull(dispatches1.find { it.id == "expired" })
        assertNull(dispatches2.find { it.id == "expired" })
    }

    @Test
    fun getQueuedDispatches_DoesNot_ReturnMoreThanRequested() {
        queueRepository.enqueue(listOf(dispatch1, dispatch2))
        assertEquals(2, queueRepository.size)

        val dispatches1 = queueRepository.getQueuedDispatches(1, dispatcher1)
        val dispatches2 = queueRepository.getQueuedDispatches(1, dispatcher2)

        assertEquals(1, dispatches1.size)
        assertEquals(1, dispatches2.size)
    }

    @Test
    fun getQueuedDispatches_ReturnsAll_WhenMoreIsRequested() {
        queueRepository.enqueue(listOf(dispatch1, dispatch2))
        assertEquals(2, queueRepository.size)

        val dispatches1 = queueRepository.getQueuedDispatches(3, dispatcher1)
        val dispatches2 = queueRepository.getQueuedDispatches(3, dispatcher2)

        assertEquals(2, dispatches1.size)
        assertEquals(2, dispatches2.size)
    }

    @Test
    fun getQueuedDispatches_ReturnsAll_WhenNegativeCountProvided() {
        queueRepository.enqueue(listOf(dispatch1, dispatch2))
        assertEquals(2, queueRepository.size)

        val dispatches1 = queueRepository.getQueuedDispatches(-1, dispatcher1)
        val dispatches2 = queueRepository.getQueuedDispatches(-100, dispatcher2)

        assertEquals(2, dispatches1.size)
        assertEquals(2, dispatches2.size)
    }

    @Test
    fun updateDispatches_OnUpgrade_MigratesExistingQueuedDispatches() {
        val mockProvider = mockk<DatabaseProvider>()
        val inMemoryDb =
            DatabaseTestUtils.createV1Database(getDefaultConfig(app).application.applicationContext)
        every { mockProvider.database } returns inMemoryDb


        DatabaseTestUtils.insertLegacyDispatch(
            inMemoryDb, "expired", timestamp = QueueRepositoryImpl.getExpiryTimestamp(
                TimeFrame(10, TimeUnit.DAYS)
            )
        )
        DatabaseTestUtils.insertLegacyDispatch(
            inMemoryDb, "not-expired", timestamp = QueueRepositoryImpl.getExpiryTimestamp(
                TimeFrame(1, TimeUnit.DAYS)
            )
        )
        inMemoryDb.upgrade(1, 3)

        // migrate
        val queueRepository =
            QueueRepositoryImpl(mockProvider, expiration = TimeFrame(5, TimeUnit.DAYS))
        queueRepository.updateDispatchers(listOf(dispatcher1, dispatcher2))
        val dispatches1 = queueRepository.getQueuedDispatches(2, dispatcher1)
        val dispatches2 = queueRepository.getQueuedDispatches(2, dispatcher2)
        val dispatches3 = queueRepository.getQueuedDispatches(2, dispatcher3)


        assertEquals(1, queueRepository.size)
        assertEquals(2, queueRepository.dispatcherQueueSize()) // one for dispatcher 1 + 2

        assertEquals(1, dispatches1.size)
        assertEquals(1, dispatches2.size)
        assertEquals(0, dispatches3.size)

        assertEquals("not-expired", dispatches1.first().id)
        assertEquals("not-expired", dispatches2.first().id)
    }

    @Test
    fun updateDispatches_OnUpgrade_EmptyQueueDoesNotThrow() {
        val mockProvider = mockk<DatabaseProvider>()
        val inMemoryDb =
            DatabaseTestUtils.createV1Database(getDefaultConfig(app).application.applicationContext)
        inMemoryDb.upgrade(1, 3)
        every { mockProvider.database } returns inMemoryDb


        // migrate
        val queueRepository =
            QueueRepositoryImpl(mockProvider, expiration = TimeFrame(5, TimeUnit.DAYS))
        queueRepository.updateDispatchers(listOf(dispatcher1, dispatcher2))
        val dispatches1 = queueRepository.getQueuedDispatches(2, dispatcher1)
        val dispatches2 = queueRepository.getQueuedDispatches(2, dispatcher2)
        val dispatches3 = queueRepository.getQueuedDispatches(2, dispatcher3)


        assertEquals(0, queueRepository.size)
        assertEquals(0, queueRepository.dispatcherQueueSize())

        assertEquals(0, dispatches1.size)
        assertEquals(0, dispatches2.size)
        assertEquals(0, dispatches3.size)
    }

    private fun prePopulateDispatchers() {
        queueRepository.updateDispatchers(
            listOf(dispatcher1, dispatcher2, dispatcher3)
        )
    }
}
package com.tealium.core.internal.persistence.repositories

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.misc.TimeFrameUtils.days
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TealiumDispatchType
import com.tealium.core.internal.persistence.database.DatabaseProvider
import com.tealium.core.internal.persistence.database.DatabaseTestUtils
import com.tealium.core.internal.persistence.database.DatabaseTestUtils.upgrade
import com.tealium.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.core.internal.persistence.database.getTimestampMilliseconds
import com.tealium.tests.common.getDefaultConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SQLQueueRepositoryTests {

    lateinit var app: Application
    lateinit var queueRepository: SQLQueueRepository
    lateinit var dbProvider: DatabaseProvider

    lateinit var dispatch1: Dispatch
    lateinit var dispatch2: Dispatch
    lateinit var processor1: String
    lateinit var processor2: String
    lateinit var processor3: String
    lateinit var defaultProcessors: Set<String>

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext() as Application
        val config = getDefaultConfig(app)

        // use-in-memory DB
        dbProvider = InMemoryDatabaseProvider(config)
        // Repositories
        queueRepository = SQLQueueRepository(
            dbProvider,
            10,
            1.days
        )

        dispatch1 = Dispatch.create(
            eventName = "dispatch1",
            type = TealiumDispatchType.Event,
            dataObject = DataObject.create {
                put("key1", "string1")
            }
        )
        dispatch2 = Dispatch.create(
            eventName = "dispatch2",
            type = TealiumDispatchType.View,
            dataObject = DataObject.create {
                put("key2", "string2")
            }
        )
        processor1 = "processor_1"
        processor2 = "processor_2"
        processor3 = "processor_3"
        defaultProcessors = setOf(processor1, processor2, processor3)

        prePopulateProcessors()
    }

    @After
    fun tearDown() {
        dbProvider.database.close()
    }

    @Test
    fun storeDispatch_CreatesEntries_ForGivenProcessors() {
        queueRepository.storeDispatch(listOf(dispatch1), setOf(processor1, processor2))
        val dispatches1 = queueRepository.getQueuedDispatches(1, processor1)
        val dispatches2 = queueRepository.getQueuedDispatches(1, processor2)

        assertEquals(dispatches1.size, dispatches2.size)
        assertEquals(dispatches1.first().id, dispatches2.first().id)
    }

    @Test
    fun storeDispatch_OverwritesEntries_WhenIdAlreadyExists() {
        queueRepository.storeDispatch(listOf(dispatch1), setOf(processor1, processor2))
        queueRepository.storeDispatch(listOf(dispatch1), setOf(processor1, processor2))
        val dispatches1 = queueRepository.getQueuedDispatches(2, processor1)
        val dispatches2 = queueRepository.getQueuedDispatches(2, processor2)

        assertEquals(1, queueRepository.size)
        assertEquals(dispatches1.size, dispatches2.size)
        assertEquals(dispatches1.first().id, dispatches2.first().id)
    }

    @Test
    fun storeDispatch_DoesNot_CreateDuplicateEntries_WhenIdAlreadyExists() {
        queueRepository.storeDispatch(
            listOf(dispatch1, dispatch1, dispatch2),
            setOf(processor1, processor2)
        )
        val dispatches1 = queueRepository.getQueuedDispatches(2, processor1)
        val dispatches2 = queueRepository.getQueuedDispatches(2, processor2)

        assertEquals(2, queueRepository.size)
        assertEquals(dispatch1.id, dispatches1[0].id)
        assertEquals(dispatch2.id, dispatches1[1].id)
        assertEquals(dispatch1.id, dispatches2[0].id)
        assertEquals(dispatch2.id, dispatches2[1].id)
    }

    @Test
    fun storeDispatch_RemovesOldEntries_And_CreatesEntries_ForAdditionalProcessor_WhenAlreadyEnqueuedForAnother() {
        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), setOf(processor1))
        var dispatches1 = queueRepository.getQueuedDispatches(2, processor1)
        var dispatches2 = queueRepository.getQueuedDispatches(2, processor2)

        assertEquals(2, dispatches1.size)
        assertEquals(0, dispatches2.size)

        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), setOf(processor2))

        dispatches1 = queueRepository.getQueuedDispatches(2, processor1)
        dispatches2 = queueRepository.getQueuedDispatches(2, processor2)

        assertEquals(0, dispatches1.size)
        assertEquals(2, dispatches2.size)
    }

    @Test
    fun storeDispatch_OnlyInserts_LastDispatches_WhenIncoming_Is_GreaterThan_MaxQueueSize() {
        queueRepository.resize(2)
        val dispatch3 = Dispatch.create(
            eventName = "dispatch3",
            type = TealiumDispatchType.View,
            dataObject = DataObject.create {
                put("key3", "string3")
            }
        )

        queueRepository.storeDispatch(
            listOf(dispatch1, dispatch2, dispatch3),
            setOf(processor1, processor2)
        )
        val dispatches1 = queueRepository.getQueuedDispatches(-1, processor1)
        val dispatches2 = queueRepository.getQueuedDispatches(-1, processor2)

        assertEquals(2, dispatches1.size)
        assertEquals(dispatch2.id, dispatches1[0].id)
        assertEquals(dispatch3.id, dispatches1[1].id)

        assertEquals(2, dispatches2.size)
        assertEquals(dispatch2.id, dispatches2[0].id)
        assertEquals(dispatch3.id, dispatches2[1].id)
    }

//    @Test
//    fun storeDispatch_RemovesExpiredDispatches_When_MakingSpace() {
//        settings.onNext(CoreSettings(maxQueueSize = 1, expiration = 1))
//
//        val expired = Dispatch.create(
//            "expired",
//            dispatch1.payload(),
//            SQLQueueRepository.getExpiryTimestamp(queueRepository.expiration) - 1
//        )!!
//
//        queueRepository.storeDispatch(listOf(expired), setOf(processor1))
//        queueRepository.storeDispatch(listOf(dispatch1), setOf(processor1))
//        assertEquals(1, queueRepository.size)
//        assertEquals(1, queueRepository.processorQueueSize())
//
//        queueRepository.storeDispatch(listOf(dispatch2), setOf(processor1))
//        assertEquals(1, queueRepository.size)
//        assertEquals(1, queueRepository.processorQueueSize())
//
//        val dispatches1 = queueRepository.getQueuedDispatches(-1, processor1)
//
//        assertEquals(1, dispatches1.size)
//        assertEquals(dispatch2.id, dispatches1[0].id)
//    }

    @Test
    fun deleteDispatch_DeletesEntries_ForIndividualProcessor() {
        queueRepository.storeDispatch(listOf(dispatch1), setOf(processor1, processor2))
        queueRepository.storeDispatch(listOf(dispatch2), setOf(processor1, processor2))
        var dispatches1 = queueRepository.getQueuedDispatches(2, processor1)
        var dispatches2 = queueRepository.getQueuedDispatches(2, processor2)

        assertEquals(2, dispatches1.size)
        assertEquals(2, dispatches2.size)
        assertEquals(dispatches1.first().id, dispatches2.first().id)

        queueRepository.deleteDispatch(dispatch1, processor1)
        dispatches1 = queueRepository.getQueuedDispatches(1, processor1)
        dispatches2 = queueRepository.getQueuedDispatches(1, processor2)

        assertNotEquals(dispatches1.first().id, dispatches2.first().id)
    }

    @Test
    fun deleteDispatch_DeletesEntries_But_OnlyReducesQueueSize_When_AllQueueEntriesProcessed() {
        queueRepository.storeDispatch(listOf(dispatch1), defaultProcessors)
        queueRepository.storeDispatch(listOf(dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)
        assertEquals(6, queueRepository.processorQueueSize())

        queueRepository.deleteDispatch(dispatch1, processor1)
        assertEquals(2, queueRepository.size)
        assertEquals(5, queueRepository.processorQueueSize())

        queueRepository.deleteDispatch(dispatch1, processor2)
        queueRepository.deleteDispatch(dispatch1, processor3)
        assertEquals(3, queueRepository.processorQueueSize())
        assertEquals(1, queueRepository.size)
    }

    @Test
    fun deleteDispatches_DeletesMultipleEntries_ForIndividualProcessor() {
        queueRepository.storeDispatch(listOf(dispatch1), setOf(processor1, processor2))
        queueRepository.storeDispatch(listOf(dispatch2), setOf(processor1, processor2))
        var dispatches1 = queueRepository.getQueuedDispatches(2, processor1)
        var dispatches2 = queueRepository.getQueuedDispatches(2, processor2)

        assertEquals(2, dispatches1.size)
        assertEquals(2, dispatches2.size)
        assertEquals(dispatches1.first().id, dispatches2.first().id)

        queueRepository.deleteDispatches(listOf(dispatch1, dispatch2), processor1)
        dispatches1 = queueRepository.getQueuedDispatches(2, processor1)
        dispatches2 = queueRepository.getQueuedDispatches(2, processor2)

        assertEquals(0, dispatches1.size)
        assertEquals(2, dispatches2.size)
    }

    @Test
    fun deleteDispatches_DeletesMultipleEntries_But_OnlyReducesQueueSize_When_AllQueueEntriesProcessed() {
        queueRepository.storeDispatch(listOf(dispatch1), defaultProcessors)
        queueRepository.storeDispatch(listOf(dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)
        assertEquals(6, queueRepository.processorQueueSize())

        queueRepository.deleteDispatches(listOf(dispatch1, dispatch2), processor1)
        assertEquals(2, queueRepository.size)
        assertEquals(4, queueRepository.processorQueueSize())

        queueRepository.deleteDispatches(listOf(dispatch1, dispatch2), processor2)
        queueRepository.deleteDispatches(listOf(dispatch1, dispatch2), processor3)
        assertEquals(0, queueRepository.size)
        assertEquals(0, queueRepository.processorQueueSize())
    }

    @Test
    fun deleteQueues_AddingProcessor_DoesntAffectExistingProcessors() {
        val processor4 = "processor_4"
        queueRepository.deleteQueues(
            defaultProcessors + processor4
        )
        queueRepository.storeDispatch(listOf(dispatch1), defaultProcessors + processor4)

        val dispatches1 = queueRepository.getQueuedDispatches(1, processor1)
        val dispatches2 = queueRepository.getQueuedDispatches(1, processor2)
        val dispatches3 = queueRepository.getQueuedDispatches(1, processor3)
        val dispatches4 = queueRepository.getQueuedDispatches(1, processor4)

        assertEquals(1, dispatches1.size)
        assertEquals(1, dispatches2.size)
        assertEquals(1, dispatches3.size)
        assertEquals(1, dispatches4.size)
    }

    @Test
    fun deleteQueues_DeletesOnlyQueueEntries_For_RemovedProcessors() {
        queueRepository.storeDispatch(listOf(dispatch1), defaultProcessors)
        var dispatches1 = queueRepository.getQueuedDispatches(1, processor1)
        var dispatches2 = queueRepository.getQueuedDispatches(1, processor2)
        var dispatches3 = queueRepository.getQueuedDispatches(1, processor3)
        assertEquals(1, dispatches1.size)
        assertEquals(1, dispatches2.size)
        assertEquals(1, dispatches3.size)

        queueRepository.deleteQueues( // remove processor3
            setOf(processor1, processor2)
        )

        dispatches1 = queueRepository.getQueuedDispatches(1, processor1)
        dispatches2 = queueRepository.getQueuedDispatches(1, processor2)
        dispatches3 = queueRepository.getQueuedDispatches(1, processor3)

        assertEquals(1, dispatches1.size)
        assertEquals(1, dispatches2.size)
        assertEquals(0, dispatches3.size)
    }

    @Test
    fun deleteDispatches_RemovesAllQueueEntries_WhenProcessorsEmpty() {
        queueRepository.storeDispatch(listOf(dispatch1), defaultProcessors)
        var dispatches1 = queueRepository.getQueuedDispatches(1, processor1)
        var dispatches2 = queueRepository.getQueuedDispatches(1, processor2)
        var dispatches3 = queueRepository.getQueuedDispatches(1, processor3)
        assertEquals(1, dispatches1.size)
        assertEquals(1, dispatches2.size)
        assertEquals(1, dispatches3.size)

        queueRepository.deleteQueues(
            setOf()
        )

        dispatches1 = queueRepository.getQueuedDispatches(1, processor1)
        dispatches2 = queueRepository.getQueuedDispatches(1, processor2)
        dispatches3 = queueRepository.getQueuedDispatches(1, processor3)

        assertEquals(0, dispatches1.size)
        assertEquals(0, dispatches2.size)
        assertEquals(0, dispatches3.size)
    }

    @Test
    fun deleteQueues_DeletesQueueEntriesForRemovedProcessors_AndAreNotAvailableIfReenabled() {
        queueRepository.storeDispatch(listOf(dispatch1), defaultProcessors)
        var dispatches3 = queueRepository.getQueuedDispatches(1, processor3)
        assertEquals(1, dispatches3.size)

        queueRepository.deleteQueues( // remove processor3
            setOf(processor1, processor2)
        )

        dispatches3 = queueRepository.getQueuedDispatches(1, processor3)
        assertEquals(0, dispatches3.size)

        queueRepository.deleteQueues( // enable processor3 again
            setOf(processor1, processor2, processor3)
        )

        dispatches3 = queueRepository.getQueuedDispatches(1, processor3)
        assertEquals(0, dispatches3.size)
    }

    @Test
    fun resize_RemovesOldestDispatchesFirst_When_NewSize_IsSmaller() {
        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)

        queueRepository.resize(1) // deletes dispatch1
        val dispatches = queueRepository.getQueuedDispatches(1, processor1)

        assertEquals(1, queueRepository.size)
        assertEquals(dispatches.first().id, dispatch2.id)
    }

    @Test
    fun resize_DoesNothing_WhenNewSize_IsGreater() {
        queueRepository.resize(2)
        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)

        queueRepository.resize(3)
        val dispatches = queueRepository.getQueuedDispatches(-1, processor1)

        assertEquals(2, queueRepository.size)
        assertEquals(dispatches.first().id, dispatch1.id)
    }

    @Test
    fun resize_DoesNothing_WhenNewSize_IsNegative() {
        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)

        queueRepository.resize(-1)
        val dispatches = queueRepository.getQueuedDispatches(-1, processor1)

        assertEquals(2, queueRepository.size)
        assertEquals(dispatches.first().id, dispatch1.id)
    }

    @Test
    fun resize_DoesNothing_When_NewSize_IsBigger() {
        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)

        queueRepository.resize(20)
        val dispatches = queueRepository.getQueuedDispatches(2, processor1)

        assertEquals(2, queueRepository.size)
        assertEquals(dispatches.first().id, dispatch1.id)
    }

    @Test
    fun setExpiration_RemovesExpired() {
        val expired = Dispatch.create(
            "expired",
            dispatch1.payload(),
            SQLQueueRepository.getExpiryTimestamp(queueRepository.expiration) - 1
        )!!

        queueRepository.storeDispatch(listOf(expired, dispatch1, dispatch2), defaultProcessors)
        assertEquals(3, queueRepository.size)

        queueRepository.setExpiration(queueRepository.expiration)
        assertEquals(2, queueRepository.size)
    }

    @Test
    fun storeDispatch_RemovesOldestDispatches_IfSpaceRequired() {
        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)

        queueRepository.resize(2)
        val dispatch3 = Dispatch.create(
            eventName = "dispatch3",
            type = TealiumDispatchType.Event,
            dataObject = DataObject.create {
                put("key1", "string1")
            }
        )
        queueRepository.storeDispatch(listOf(dispatch3), defaultProcessors) // removes dispatch1
        val dispatches = queueRepository.getQueuedDispatches(2, processor1)

        assertEquals(2, queueRepository.size)
        assertEquals(dispatches.first().id, dispatch2.id)
        assertEquals(dispatches.last().id, dispatch3.id)
    }

    @Test
    fun size_DoesInclude_ExpiredEntries() {
        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)

        val expiredDispatch = Dispatch.create(
            "expired",
            dispatch1.payload(),
            SQLQueueRepository.getExpiryTimestamp(queueRepository.expiration) - 1
        )
        queueRepository.storeDispatch(listOf(expiredDispatch!!), defaultProcessors)

        assertEquals(3, queueRepository.size)
    }

    @Test
    fun getQueuedDispatches_DoesNotInclude_ExpiredEntries() {
        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)

        val expiredDispatch = Dispatch.create(
            "expired",
            dispatch1.payload(),
            SQLQueueRepository.getExpiryTimestamp(queueRepository.expiration) - 1
        )
        queueRepository.storeDispatch(listOf(expiredDispatch!!), defaultProcessors)

        val dispatches1 = queueRepository.getQueuedDispatches(3, processor1)
        val dispatches2 = queueRepository.getQueuedDispatches(3, processor2)

        assertEquals(2, dispatches1.size)
        assertEquals(2, dispatches2.size)
        assertNull(dispatches1.find { it.id == "expired" })
        assertNull(dispatches2.find { it.id == "expired" })
    }

    @Test
    fun getQueuedDispatches_DoesNot_ReturnMoreThanRequested() {
        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)

        val dispatches1 = queueRepository.getQueuedDispatches(1, processor1)
        val dispatches2 = queueRepository.getQueuedDispatches(1, processor2)

        assertEquals(1, dispatches1.size)
        assertEquals(1, dispatches2.size)
    }

    @Test
    fun getQueuedDispatches_ReturnsAll_WhenMoreIsRequested() {
        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)

        val dispatches1 = queueRepository.getQueuedDispatches(3, processor1)
        val dispatches2 = queueRepository.getQueuedDispatches(3, processor2)

        assertEquals(2, dispatches1.size)
        assertEquals(2, dispatches2.size)
    }

    @Test
    fun getQueuedDispatches_ReturnsAll_WhenNegativeCountProvided() {
        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)

        val dispatches1 = queueRepository.getQueuedDispatches(-1, processor1)
        val dispatches2 = queueRepository.getQueuedDispatches(-100, processor2)

        assertEquals(2, dispatches1.size)
        assertEquals(2, dispatches2.size)
    }

    @Test
    fun getQueuedDispatches_DoesNotReturn_ExcludedDispatches() {
        queueRepository.storeDispatch(listOf(dispatch1, dispatch2), defaultProcessors)
        assertEquals(2, queueRepository.size)

        val dispatches1 = queueRepository.getQueuedDispatches(1, setOf(dispatch1), processor1)
        val dispatches2 =
            queueRepository.getQueuedDispatches(10, setOf(dispatch1, dispatch2), processor2)

        assertEquals(1, dispatches1.size)
        assertEquals(dispatch2.id, dispatches1.first().id)
        assertEquals(0, dispatches2.size)
    }

    @Test
    fun getQueuedDispatches_Returns_Dispatches_OrderedByTimestamp() {
        val dispatch1 =
            Dispatch.create("event_1", dispatch1.payload(), getTimestampMilliseconds())!!
        val dispatch2 =
            Dispatch.create("event_2", dispatch2.payload(), getTimestampMilliseconds() + 100)!!
        val dispatch3 =
            Dispatch.create("event_3", dispatch2.payload(), getTimestampMilliseconds() + 200)!!
        // incorrect insertion order
        queueRepository.storeDispatch(listOf(dispatch3, dispatch2, dispatch1), defaultProcessors)
        assertEquals(3, queueRepository.size)

        val dispatches1 = queueRepository.getQueuedDispatches(5, processor1)
        val dispatches2 = queueRepository.getQueuedDispatches(5, processor2)

        assertEquals(3, dispatches1.size)
        assertEquals(dispatch1.id, dispatches1[0].id)
        assertEquals(dispatch2.id, dispatches1[1].id)
        assertEquals(dispatch3.id, dispatches1[2].id)

        assertEquals(3, dispatches2.size)
        assertEquals(dispatch1.id, dispatches2[0].id)
        assertEquals(dispatch2.id, dispatches2[1].id)
        assertEquals(dispatch3.id, dispatches2[2].id)
    }

//    @Test
    //TODO("Failing as this used to assume a trigger would create the entries from the `processors` table")
//    fun updateDispatches_OnUpgrade_MigratesExistingQueuedDispatches() {
//        val mockProvider = mockk<DatabaseProvider>()
//        val inMemoryDb =
//            DatabaseTestUtils.createV1Database(getDefaultConfig(app).application.applicationContext)
//        every { mockProvider.database } returns inMemoryDb
//
//
//        DatabaseTestUtils.insertLegacyDispatch(
//            inMemoryDb, "expired", timestamp = SQLQueueRepository.getExpiryTimestamp(
//                TimeFrame(10, TimeUnit.DAYS)
//            )
//        )
//        DatabaseTestUtils.insertLegacyDispatch(
//            inMemoryDb, "not-expired", timestamp = SQLQueueRepository.getExpiryTimestamp(
//                TimeFrame(1, TimeUnit.DAYS)
//            )
//        )
//        inMemoryDb.upgrade(1, 3)
//
//        // migrate
//        val queueRepository =
//            SQLQueueRepository(mockProvider, expiration = TimeFrame(5, TimeUnit.DAYS))
//        queueRepository.updateProcessors(setOf(processor1, processor2))
//        val dispatches1 = queueRepository.getQueuedDispatches(2, processor1)
//        val dispatches2 = queueRepository.getQueuedDispatches(2, processor2)
//        val dispatches3 = queueRepository.getQueuedDispatches(2, processor3)
//
//
//        assertEquals(1, queueRepository.size)
//        assertEquals(2, queueRepository.processorQueueSize()) // one for processor 1 + 2
//
//        assertEquals(1, dispatches1.size)
//        assertEquals(1, dispatches2.size)
//        assertEquals(0, dispatches3.size)
//
//        assertEquals("not-expired", dispatches1.first().id)
//        assertEquals("not-expired", dispatches2.first().id)
//    }

    @Test
    fun updateDispatches_OnUpgrade_EmptyQueueDoesNotThrow() {
        val mockProvider = mockk<DatabaseProvider>()
        val inMemoryDb =
            DatabaseTestUtils.createV1Database(getDefaultConfig(app).application.applicationContext)
        inMemoryDb.upgrade(1, 3)
        every { mockProvider.database } returns inMemoryDb


        // migrate
        val queueRepository =
            SQLQueueRepository(mockProvider, expiration = 5.days)
        queueRepository.deleteQueues(setOf(processor1, processor2))
        val dispatches1 = queueRepository.getQueuedDispatches(2, processor1)
        val dispatches2 = queueRepository.getQueuedDispatches(2, processor2)
        val dispatches3 = queueRepository.getQueuedDispatches(2, processor3)


        assertEquals(0, queueRepository.size)
        assertEquals(0, queueRepository.processorQueueSize())

        assertEquals(0, dispatches1.size)
        assertEquals(0, dispatches2.size)
        assertEquals(0, dispatches3.size)
    }

    private fun prePopulateProcessors() {
        queueRepository.deleteQueues(
            setOf(processor1, processor2, processor3)
        )
    }
}
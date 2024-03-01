package com.tealium.core.internal.persistence

import com.tealium.core.api.Expiry
import com.tealium.core.api.PersistenceException
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.Subject
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModuleStoreTests {

    @MockK(relaxed = true)
    private lateinit var keyValueRepository: KeyValueRepository

    private lateinit var dataStore: ModuleStore
    private val testBundle = TealiumBundle.create {
        put("string", "value")
        put("int", 1)
        put("long", 10L)
        put("double", 100.1)
    }

    private lateinit var onDataExpired: Subject<TealiumBundle>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        val transactionBlock = slot<(KeyValueRepository) -> Unit>()
        every {
            keyValueRepository.transactionally(
                exceptionHandler = any(),
                block = capture(transactionBlock)
            )
        } answers {
            transactionBlock.invoke(keyValueRepository)
        }
        every {
            keyValueRepository.transactionally(
                block = capture(transactionBlock)
            )
        } answers {
            transactionBlock.invoke(keyValueRepository)
        }

        onDataExpired = Observables.publishSubject()

        dataStore = ModuleStore(keyValueRepository, onDataExpired)
    }

    @Test
    fun edit_Returns_DifferentEditors() {
        val editor1 = dataStore.edit()
        val editor2 = dataStore.edit()

        assertNotSame(editor1, editor2)
    }

    @Test
    fun count_Returns_ItemCount() {
        every { keyValueRepository.count() } returns 1

        assertEquals(1, dataStore.count())
    }

    @Test
    fun keys_Returns_ItemKeys() {
        every { keyValueRepository.keys() } returns listOf("key1", "key2", "key3")

        assertEquals(listOf("key1", "key2", "key3"), dataStore.keys())
    }

    @Test
    fun get_Returns_CorrectItem_WhenPresent() {
        every { keyValueRepository.get("string") } returns TealiumValue.string("value")
        every { keyValueRepository.get("int") } returns TealiumValue.int(10)

        assertEquals("value", dataStore.get("string")!!.value)
        assertEquals(10, dataStore.get("int")!!.value)
    }

    @Test
    fun get_Returns_Null_WhenNotPresent() {
        every { keyValueRepository.get("missing_key") } returns null

        assertNull(dataStore.get("missing_key"))
    }

    @Test
    fun getAll_Returns_AllValues_AsBundle() {
        every { keyValueRepository.getAll() } returns mapOf(
            "string" to TealiumValue.string("value"),
            "int" to TealiumValue.int(10),
            "bundle" to testBundle.asTealiumValue()
        )

        val allData = dataStore.getAll()

        assertEquals("value", allData.get("string")!!.value)
        assertEquals(10, allData.get("int")!!.value)
        assertEquals(testBundle, allData.get("bundle")!!.value)
    }

    @Test
    fun iterator_ReturnsIterator_ContainingAllKeys() {
        every { keyValueRepository.getAll() } returns mapOf(
            "string" to TealiumValue.string("value"),
            "int" to TealiumValue.int(10),
            "bundle" to testBundle.asTealiumValue()
        )

        val keys = mutableListOf<String>()
        val values = mutableListOf<TealiumValue>()
        for ((key, value) in dataStore) {
            keys.add(key)
            values.add(value)
        }
        assertEquals(listOf("string", "int", "bundle"), keys)
        assertEquals(listOf("value", 10, testBundle), values.map { it.value })
    }


    @Test
    fun editor_EachMethod_ReturnsSameInstance() {
        val editor = dataStore.edit()

        assertSame(editor, editor.putAll(testBundle, Expiry.SESSION))
        assertSame(editor, editor.put("key", testBundle.asTealiumValue(), Expiry.SESSION))
        assertSame(editor, editor.clear())
        assertSame(editor, editor.remove(""))
        assertSame(editor, editor.remove(listOf("", "")))
    }

    @Test
    fun editor_ClearsFirst_BeforeAddingNewData() {
        val value = TealiumValue.string("value")
        dataStore.edit()
            .clear()
            .put("string", value, Expiry.SESSION)
            .commit()

        verifyOrder {
            keyValueRepository.clear()
            keyValueRepository.upsert("string", value, Expiry.SESSION)
        }
    }

    @Test
    fun editor_ExecutesUpdatesInOrderTheyWereAdded() {


        val string = TealiumValue.string("value")
        val int = TealiumValue.int(10)
        dataStore.edit()
            .put("string", string, Expiry.SESSION)
            .put("int", int, Expiry.SESSION)
            .commit()

        verifyOrder {
            keyValueRepository.upsert("string", string, Expiry.SESSION)
            keyValueRepository.upsert("int", int, Expiry.SESSION)
        }
    }

    @Test
    fun editor_OnlyCommitsOnce() {
        val editor = dataStore.edit()

        editor
            .clear()
            .commit()
        editor.commit()

        verify(exactly = 1) {
            keyValueRepository.clear()
        }
    }

    @Test
    fun editor_Remove_Deletes() {
        val editor = dataStore.edit()

        editor
            .remove("key1")
            .remove("key2")
            .commit()

        verify {
            keyValueRepository.delete("key1")
            keyValueRepository.delete("key2")
        }
    }

    @Test
    fun editor_RemoveAll_DeletesAll() {
        dataStore.edit()
            .remove(listOf("key1", "key2"))
            .commit()

        verify {
            keyValueRepository.delete("key1")
            keyValueRepository.delete("key2")
        }
    }

    @Test
    fun editor_Put_UpsertsData() {
        val string = TealiumValue.string("value")
        dataStore.edit()
            .put("string", string, Expiry.SESSION)
            .commit()

        verify {
            keyValueRepository.upsert("string", string, Expiry.SESSION)
        }
    }

    @Test
    fun editor_PutAll_UpsertsAllData() {
        val string = TealiumValue.string("value")
        val int = TealiumValue.int(10)

        dataStore.edit()
            .putAll(TealiumBundle.create {
                put("string", string)
                put("int", int)
            }, Expiry.SESSION)
            .commit()

        verify {
            keyValueRepository.upsert("string", string, Expiry.SESSION)
            keyValueRepository.upsert("int", int, Expiry.SESSION)
        }
    }

    @Test
    fun editor_PutAll_IgnoresNullValues() {
        val nullValue = TealiumValue.NULL

        dataStore.edit()
            .putAll(TealiumBundle.create {
                put("null", nullValue)
            }, Expiry.SESSION)
            .commit()

        verify {
            keyValueRepository wasNot Called
        }
    }

    @Test(expected = PersistenceException::class)
    fun editor_Commit_ThrowsPersistenceException_OnFailure() {
        every { keyValueRepository.upsert(any(), any(), any()) } answers {
            1
        } andThenAnswer {
            throw PersistenceException("", Exception())
        }

        dataStore.edit()
            .putAll(TealiumBundle.create {
                put("key1", "value1")
                put("key2", "value2")
            }, Expiry.SESSION)
            .commit()
    }

    @Test
    fun editor_Commit_CanRetry_WhenThrowsPersistenceException() {
        every { keyValueRepository.upsert(any(), any(), any()) } answers {
            1
        } andThenAnswer {
            throw PersistenceException("", Exception())
        }

        repeat(2) {
            try {
                dataStore.edit()
                    .putAll(TealiumBundle.create {
                        put("key1", "value1")
                        put("key2", "value2")
                    }, Expiry.SESSION)
                    .commit()
            } catch (ignored: PersistenceException) {
            }
        }
    }

    @Test
    fun onDataRemoved_NotifiesExpiredData_WhenExpired() {
        dataStore.onDataRemoved.take(1).subscribe { removed ->
            assertEquals(2, removed.size)
            assertEquals("key", removed[0])
            assertEquals("key2", removed[1])
        }

        onDataExpired.onNext(TealiumBundle.create {
            put("key", "value")
            put("key2", "value2")
        })
    }

    @Test
    fun onDataRemoved_NotifiesRemovedData_WhenCommitted() {
        every { keyValueRepository.delete(any()) } returns 1

        dataStore.onDataRemoved.subscribe { removed ->
            assertEquals("key1", removed[0])
            assertEquals("key2", removed[1])
        }
        dataStore.edit()
            .remove("key1")
            .remove("key2")
            .commit()
    }

    @Test
    fun onDataRemoved_DoesNot_NotifyRemovedData_WhenCommitted_ButKeyDoesNotExist() {
        every { keyValueRepository.delete(any()) } returnsMany listOf(1, 0, 1)

        dataStore.onDataRemoved.subscribe { removed ->
            assertEquals(1, removed.size)
            assertTrue(removed.contains("key1"))
            assertFalse(removed.contains("key100"))
        }
        dataStore.edit()
            .remove("key1")
            .remove("key100")
            .commit()
    }

    @Test
    fun onDataRemoved_NotifiesOfClearedKeys_WhenCommitted() {
        every { keyValueRepository.keys() } returns listOf("key1", "key2", "key3")

        dataStore.onDataRemoved.subscribe { cleared ->

            assertEquals("key1", cleared[0])
            assertEquals("key2", cleared[1])
            assertEquals("key3", cleared[2])
        }
        dataStore.edit()
            .clear()
            .commit()
    }

    @Test
    fun onDataRemoved_BlendsExpiredWithRemoved() {
        every { keyValueRepository.delete("key1") } returns 1
        every { keyValueRepository.delete("key2") } returns 1
        val verifier = mockk<(List<String>) -> Unit>(relaxed = true)
        dataStore.onDataRemoved.subscribe(verifier)

        dataStore.edit()
            .remove("key1")
            .remove("key2")
            .commit()

        verify {
            verifier(match { removed ->
                removed[0] == "key1" && removed[1] == "key2"
            })
        }

        onDataExpired.onNext(TealiumBundle.create {
            put("expired1", TealiumValue.string("test"))
            put("expired2", TealiumValue.string("test"))
        })
        verify {
            verifier(match { removed ->
                removed[0] == "expired1" && removed[1] == "expired2"
            })
        }
    }

    @Test
    fun onDataUpdated_NotifiesUpdated_WhenCommittedSuccessfully() {
        every { keyValueRepository.upsert(any(), any(), any()) } returns 1

        dataStore.onDataUpdated.subscribe { emission ->
            assertEquals("value1", emission.get("key1")?.value)
            assertEquals("value2", emission.get("key2")?.value)
        }
        dataStore.edit()
            .put("key1", TealiumValue.string("value1"), Expiry.SESSION)
            .put("key2", TealiumValue.string("value2"), Expiry.SESSION)
            .commit()
    }

    @Test
    fun onDataUpdated_DoesNot_NotifyUpdated_WhenCommitFailed() {
        every { keyValueRepository.upsert(any(), any(), any()) } returns 0
        val verifier = mockk<(TealiumBundle) -> Unit>(relaxed = true)

        dataStore.onDataUpdated.subscribe(verifier)
        dataStore.edit()
            .put("key1", TealiumValue.string("value1"), Expiry.SESSION)
            .put("key2", TealiumValue.string("value2"), Expiry.SESSION)
            .commit()

        verify(inverse = true) {
            verifier(any())
        }
    }

    @Test
    fun onData_NotifiesUpdated_And_NotifiesRemoved_WhenCommitted() {
        every { keyValueRepository.upsert("added", any(), Expiry.SESSION) } returns 1L
        every { keyValueRepository.delete("removed") } returns 1

        val onUpdated = mockk<(TealiumBundle) -> Unit>(relaxed = true)
        val onRemoved = mockk<(List<String>) -> Unit>(relaxed = true)

        dataStore.onDataUpdated.subscribe(onUpdated)
        dataStore.onDataRemoved.subscribe(onRemoved)

        dataStore.edit()
            .put("added", TealiumValue.string("value"), Expiry.SESSION)
            .remove("removed")
            .commit()

        verify {
            onUpdated(match { updatedItems ->
                updatedItems.size == 1 && updatedItems.get("added")?.value == "value"
            })

            onRemoved(match {  removedKeys ->
                removedKeys.first() == "removed"
            })
        }
    }
}
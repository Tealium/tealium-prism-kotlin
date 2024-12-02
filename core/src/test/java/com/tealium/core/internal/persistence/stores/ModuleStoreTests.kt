package com.tealium.core.internal.persistence.stores

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.persistence.PersistenceException
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.internal.persistence.repositories.KeyValueRepository
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModuleStoreTests {

    @MockK(relaxed = true)
    private lateinit var keyValueRepository: KeyValueRepository

    private lateinit var dataStore: ModuleStore
    private val testDataObject = DataObject.create {
        put("string", "value")
        put("int", 1)
        put("long", 10L)
        put("double", 100.1)
    }

    private lateinit var onDataExpired: Subject<DataObject>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { keyValueRepository.transactionally(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (it.invocation.args[0] as (KeyValueRepository) -> Unit).invoke(keyValueRepository)
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

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_Count_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.count()
    }

    @Test
    fun keys_Returns_ItemKeys() {
        every { keyValueRepository.keys() } returns listOf("key1", "key2", "key3")

        assertEquals(listOf("key1", "key2", "key3"), dataStore.keys())
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_Keys_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.keys()
    }

    @Test
    fun get_Returns_CorrectItem_WhenPresent() {
        every { keyValueRepository.get("string") } returns DataItem.string("value")
        every { keyValueRepository.get("int") } returns DataItem.int(10)

        assertEquals("value", dataStore.get("string")!!.value)
        assertEquals(10, dataStore.get("int")!!.value)
    }

    @Test
    fun get_Returns_Null_WhenNotPresent() {
        every { keyValueRepository.get("missing_key") } returns null

        assertNull(dataStore.get("missing_key"))
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_get_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.get("key")
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_getString_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.getString("key")
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_getInt_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.getInt("key")
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_getLong_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.getLong("key")
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_getDouble_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.getDouble("key")
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_getBoolean_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.getBoolean("key")
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_getDataList_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.getDataList("key")
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_getDataObject_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.getDataObject("key")
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_getConvertible_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.get("key") { it.toString() }
    }

    @Test
    fun getAll_Returns_AllValues_AsDataObject() {
        every { keyValueRepository.getAll() } returns mapOf(
            "string" to DataItem.string("value"),
            "int" to DataItem.int(10),
            "object" to testDataObject.asDataItem()
        )

        val allData = dataStore.getAll()

        assertEquals("value", allData.get("string")!!.value)
        assertEquals(10, allData.get("int")!!.value)
        assertEquals(testDataObject, allData.get("object")!!.value)
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_getAll_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.getAll()
    }

    @Test
    fun iterator_ReturnsIterator_ContainingAllKeys() {
        every { keyValueRepository.getAll() } returns mapOf(
            "string" to DataItem.string("value"),
            "int" to DataItem.int(10),
            "object" to testDataObject.asDataItem()
        )

        val keys = mutableListOf<String>()
        val values = mutableListOf<DataItem>()
        for ((key, value) in dataStore) {
            keys.add(key)
            values.add(value)
        }
        assertEquals(listOf("string", "int", "object"), keys)
        assertEquals(listOf("value", 10, testDataObject), values.map { it.value })
    }

    @Test
    fun editor_EachMethod_ReturnsSameInstance() {
        val editor = dataStore.edit()

        assertSame(editor, editor.putAll(testDataObject, Expiry.SESSION))
        assertSame(editor, editor.put("key", testDataObject.asDataItem(), Expiry.SESSION))
        assertSame(editor, editor.clear())
        assertSame(editor, editor.remove(""))
        assertSame(editor, editor.remove(listOf("", "")))
    }

    @Test
    fun editor_ClearsFirst_BeforeAddingNewData() {
        val value = DataItem.string("value")
        dataStore.edit()
            .clear()
            .put("string", value, Expiry.SESSION)
            .commit()

        verifyOrder {
            keyValueRepository.clear()
            keyValueRepository.upsert("string", value, Expiry.SESSION)
        }
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_clear_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.clear()
    }

    @Test
    fun editor_ExecutesUpdatesInOrderTheyWereAdded() {
        val string = DataItem.string("value")
        val int = DataItem.int(10)
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

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_remove_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.remove("key")
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

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_removeAll_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.remove(listOf("key", "key2"))
    }

    @Test
    fun editor_Put_UpsertsData() {
        val string = DataItem.string("value")
        dataStore.edit()
            .put("string", string, Expiry.SESSION)
            .commit()

        verify {
            keyValueRepository.upsert("string", string, Expiry.SESSION)
        }
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_put_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.put("key", "value", Expiry.FOREVER)
    }

    @Test
    fun editor_Put_UnsupportedDouble_UpsertsData_As_String() {
        dataStore.edit()
            .put("nan", Double.NaN, Expiry.SESSION)
            .put("positive-infinity", Double.POSITIVE_INFINITY, Expiry.SESSION)
            .put("negative-infinity", Double.NEGATIVE_INFINITY, Expiry.SESSION)
            .commit()

        verify {
            keyValueRepository.upsert("nan", DataItem.string("NaN"), Expiry.SESSION)
            keyValueRepository.upsert(
                "positive-infinity",
                DataItem.string("Infinity"),
                Expiry.SESSION
            )
            keyValueRepository.upsert(
                "negative-infinity",
                DataItem.string("-Infinity"),
                Expiry.SESSION
            )
        }
    }

    @Test
    fun editor_PutAll_UpsertsAllData() {
        val string = DataItem.string("value")
        val int = DataItem.int(10)

        dataStore.edit()
            .putAll(DataObject.create {
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
        val nullValue = DataItem.NULL

        dataStore.edit()
            .putAll(DataObject.create {
                put("null", nullValue)
            }, Expiry.SESSION)
            .commit()

        verify {
            keyValueRepository wasNot Called
        }
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_putAll_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.putAll(DataObject.create { put("key", "value") }, Expiry.FOREVER)
    }

    @Test(expected = PersistenceException::class)
    fun editor_Commit_ThrowsPersistenceException_OnFailure() {
        every { keyValueRepository.transactionally(any()) } throws PersistenceException(
            "",
            Exception()
        )

        dataStore.edit()
            .putAll(DataObject.create {
                put("key1", "value1")
                put("key2", "value2")
            }, Expiry.SESSION)
            .commit()
    }

    @Test(expected = DataStore.Editor.EditorClosedException::class)
    fun editor_Commit_Throws_EditorClosedException_After_Close() {
        val editor = dataStore.edit()

        editor.close()
        editor.commit()

        verify(inverse = true) { keyValueRepository.transactionally(any()) }
    }

    @Test
    fun onDataRemoved_NotifiesExpiredData_WhenExpired() {
        dataStore.onDataRemoved.take(1).subscribe { removed ->
            assertEquals(2, removed.size)
            assertEquals("key", removed[0])
            assertEquals("key2", removed[1])
        }

        onDataExpired.onNext(DataObject.create {
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

        onDataExpired.onNext(DataObject.create {
            put("expired1", DataItem.string("test"))
            put("expired2", DataItem.string("test"))
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
            .put("key1", DataItem.string("value1"), Expiry.SESSION)
            .put("key2", DataItem.string("value2"), Expiry.SESSION)
            .commit()
    }

    @Test
    fun onDataUpdated_DoesNot_NotifyUpdated_WhenCommitFailed() {
        every { keyValueRepository.upsert(any(), any(), any()) } returns 0
        val verifier = mockk<(DataObject) -> Unit>(relaxed = true)

        dataStore.onDataUpdated.subscribe(verifier)
        dataStore.edit()
            .put("key1", DataItem.string("value1"), Expiry.SESSION)
            .put("key2", DataItem.string("value2"), Expiry.SESSION)
            .commit()

        verify(inverse = true) {
            verifier(any())
        }
    }

    @Test
    fun onData_NotifiesUpdated_And_NotifiesRemoved_WhenCommitted() {
        every { keyValueRepository.upsert("added", any(), Expiry.SESSION) } returns 1L
        every { keyValueRepository.delete("removed") } returns 1

        val onUpdated = mockk<(DataObject) -> Unit>(relaxed = true)
        val onRemoved = mockk<(List<String>) -> Unit>(relaxed = true)

        dataStore.onDataUpdated.subscribe(onUpdated)
        dataStore.onDataRemoved.subscribe(onRemoved)

        dataStore.edit()
            .put("added", DataItem.string("value"), Expiry.SESSION)
            .remove("removed")
            .commit()

        verify {
            onUpdated(match { updatedItems ->
                updatedItems.size == 1 && updatedItems.get("added")?.value == "value"
            })

            onRemoved(match { removedKeys ->
                removedKeys.first() == "removed"
            })
        }
    }
}
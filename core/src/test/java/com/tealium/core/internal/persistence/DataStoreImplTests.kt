package com.tealium.core.internal.persistence

import com.tealium.core.api.Expiry
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreImplTests {

    @MockK(relaxed = true)
    private lateinit var dataStorageStrategy: DataStorageStrategy<String, TealiumValue>

    private lateinit var dataStore: DataStoreImpl
    private val testBundle = TealiumBundle.create {
        put("string", "value")
        put("int", 1)
        put("long", 10L)
        put("double", 100.1)
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        val transactionBlock = slot<(DataStorageStrategy<String, TealiumValue>) -> Unit>()
        every {
            dataStorageStrategy.transactionally(
                exceptionHandler = any(),
                block = capture(transactionBlock)
            )
        } answers {
            transactionBlock.invoke(dataStorageStrategy)
        }

        dataStore = DataStoreImpl(dataStorageStrategy)
    }

    @Test
    fun edit_Returns_DifferentEditors() {
        val editor1 = dataStore.edit()
        val editor2 = dataStore.edit()

        assertNotSame(editor1, editor2)
    }

    @Test
    fun count_Returns_ItemCount() {
        every { dataStorageStrategy.count() } returns 1

        assertEquals(1, dataStore.count())
    }

    @Test
    fun keys_Returns_ItemKeys() {
        every { dataStorageStrategy.keys() } returns listOf("key1", "key2", "key3")

        assertEquals(listOf("key1", "key2", "key3"), dataStore.keys())
    }

    @Test
    fun get_Returns_CorrectItem_WhenPresent() {
        every { dataStorageStrategy.get("string") } returns TealiumValue.string("value")
        every { dataStorageStrategy.get("int") } returns TealiumValue.int(10)

        assertEquals("value", dataStore.get("string")!!.value)
        assertEquals(10, dataStore.get("int")!!.value)
    }

    @Test
    fun get_Returns_Null_WhenNotPresent() {
        every { dataStorageStrategy.get("missing_key") } returns null

        assertNull(dataStore.get("missing_key"))
    }

    @Test
    fun getAll_Returns_AllValues_AsBundle() {
        every { dataStorageStrategy.getAll() } returns mapOf(
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
        every { dataStorageStrategy.getAll() } returns mapOf(
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
            dataStorageStrategy.clear()
            dataStorageStrategy.upsert("string", value, Expiry.SESSION)
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
            dataStorageStrategy.upsert("string", string, Expiry.SESSION)
            dataStorageStrategy.upsert("int", int, Expiry.SESSION)
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
            dataStorageStrategy.clear()
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
            dataStorageStrategy.delete("key1")
            dataStorageStrategy.delete("key2")
        }
    }

    @Test
    fun editor_RemoveAll_DeletesAll() {
        dataStore.edit()
            .remove(listOf("key1", "key2"))
            .commit()

        verify {
            dataStorageStrategy.delete("key1")
            dataStorageStrategy.delete("key2")
        }
    }

    @Test
    fun editor_Put_UpsertsData() {
        val string = TealiumValue.string("value")
        dataStore.edit()
            .put("string", string, Expiry.SESSION)
            .commit()

        verify {
            dataStorageStrategy.upsert("string", string, Expiry.SESSION)
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
            dataStorageStrategy.upsert("string", string, Expiry.SESSION)
            dataStorageStrategy.upsert("int", int, Expiry.SESSION)
        }
    }

    @Test
    fun editor_PutAll_RemovesNullValues() {
        val nullValue = TealiumValue.NULL

        dataStore.edit()
            .putAll(TealiumBundle.create {
                put("null", nullValue)
            }, Expiry.SESSION)
            .commit()

        verify {
            dataStorageStrategy.delete("null")
        }
    }
}
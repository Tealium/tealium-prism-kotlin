package com.tealium.core.internal.persistence

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.core.Environment
import com.tealium.core.TealiumConfig
import com.tealium.core.api.DataStore
import com.tealium.core.api.Expiry
import com.tealium.core.api.Module
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.data.bundle.TealiumList
import com.tealium.core.api.data.bundle.TealiumValue
import com.tealium.tests.common.TestModule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@Suppress("UNCHECKED_CAST")
class DataStoreImplTests {

    private lateinit var app: Application
    private lateinit var dataStoreFactory: DataStoreProviderImpl
    private lateinit var moduleRepository: ModuleStorageRepositoryImpl

    private val module1 = TestModule("module1")
    private val module2 = TestModule("module2")
    private val testBundle = TealiumBundle.create {
        put("string", "value")
        put("int", 1)
        put("long", 10L)
        put("double", 100.1)
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext() as Application
        val config = TealiumConfig(
            application = app,
            "test",
            "test",
            Environment.DEV,
            "",
            listOf()
        )

        // use-in-memory DB
        val inMemoryDbProvider = InMemoryDatabaseProvider(config)
        // Repositories
        moduleRepository = ModuleStorageRepositoryImpl(
            inMemoryDbProvider
        )

        dataStoreFactory = DataStoreProviderImpl(inMemoryDbProvider, moduleRepository)
    }

    @Test
    fun editor_EachMethod_ReturnsSameInstance() {
        val editor = getEmptyDataStore(module1).edit()

        assertSame(editor, editor.putAll(testBundle, Expiry.SESSION))
        assertSame(editor, editor.put("key", testBundle.asTealiumValue(), Expiry.SESSION))
        assertSame(editor, editor.clear())
        assertSame(editor, editor.remove(""))
        assertSame(editor, editor.remove(listOf("", "")))
    }

    @Test
    fun count_Returns_ItemCount() {
        val dataStore = getEmptyDataStore(module1)
        dataStore.assertEmpty()

        dataStore.edit()
            .put("key", TealiumValue.convert("value"), Expiry.SESSION)
            .commit()

        assertEquals(1, dataStore.count())
    }

    @Test
    fun count_ReturnsOnly_SpecifiedModuleItemCount() {
        val dataStore1 = getEmptyDataStore(module1)
        val dataStore2 = getEmptyDataStore(module2)

        dataStore1.edit()
            .put("key", TealiumValue.convert("value"), Expiry.SESSION)
            .commit()

        assertEquals(1, dataStore1.count())
        dataStore2.assertEmpty()
    }

    @Test
    fun keys_ReturnsOnly_StoredKeys() {
        val dataStore = getEmptyDataStore(module1)
        assertTrue(dataStore.keys().isEmpty())

        dataStore.edit()
            .put("key", TealiumValue.convert("value"), Expiry.SESSION)
            .commit()

        assertEquals(1, dataStore.keys().count())
        assertEquals("key", dataStore.keys().first())
    }

    @Test
    fun keys_ReturnsOnly_SpecifiedModuleKeys() {
        val dataStore1 = getEmptyDataStore(module1)
        val dataStore2 = getEmptyDataStore(module2)
        assertTrue(dataStore1.keys().isEmpty())
        assertTrue(dataStore2.keys().isEmpty())

        dataStore1.edit()
            .put("key", TealiumValue.convert("value"), Expiry.SESSION)
            .commit()
        dataStore2.edit()
            .put("key2", TealiumValue.convert("value"), Expiry.SESSION)
            .commit()

        assertEquals(1, dataStore1.keys().count())
        assertEquals(1, dataStore2.keys().count())
        assertEquals("key", dataStore1.keys().first())
        assertEquals("key2", dataStore2.keys().first())
    }

    @Test
    fun get_Returns_StringValue() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .put("key", TealiumValue.convert("value"), Expiry.SESSION)
            .commit()

        assertEquals("value", dataStore.get("key")?.value)
    }

    @Test
    fun get_Returns_IntValue() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .put("key", TealiumValue.convert(10), Expiry.SESSION)
            .commit()

        assertEquals(10, dataStore.get("key")?.value)
    }

    @Test
    fun get_Returns_LongValue() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .put("key", TealiumValue.convert(100L), Expiry.SESSION)
            .commit()

        assertEquals(100L, dataStore.get("key")?.value)
    }

    @Test
    fun get_Returns_DoubleValue() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .put("key", TealiumValue.convert(100.100), Expiry.SESSION)
            .commit()

        assertEquals(100.100, dataStore.get("key")?.value)
    }

    @Test
    fun get_Returns_ListValue() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .put(
                "key", TealiumValue.convert(
                    arrayOf(1, 2, 3)
                ), Expiry.SESSION
            )
            .commit()

        assertArrayEquals(
            arrayOf(1, 2, 3),
            (dataStore.get("key")?.value as TealiumList)
                .map { it.getInt() }
                .toTypedArray()
        )
    }

    @Test
    fun get_Returns_MixedListValue() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .put(
                "key", TealiumValue.convert(
                    arrayOf(1, "", true)
                ), Expiry.SESSION
            )
            .commit()


        assertArrayEquals(
            arrayOf(1, "", true),
            (dataStore.get("key")?.value as TealiumList)
                .map { it.value }
                .toTypedArray()
        )
    }

    @Test
    fun get_Returns_BundleValue() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .put("key", TealiumValue.convert(testBundle), Expiry.SESSION)
            .commit()

        val storedBundle = dataStore.get("key")?.getBundle()!!

        assertEquals("value", storedBundle.getString("string"))
        assertEquals(1, storedBundle.getInt("int"))
        // TODO - add type coercions between numerics
        assertEquals(10, storedBundle.getInt("long"))
        assertEquals(100.1, storedBundle.getDouble("double"))
    }

    @Test
    fun getAll_ReturnsAll_Values() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .putAll(testBundle, Expiry.SESSION)
            .commit()

        val bundle = dataStore.getAll()
        assertEquals("value", bundle.get("string")?.value)
        assertEquals(1, bundle.get("int")?.value)
        assertEquals(10L, bundle.get("long")?.value)
        assertEquals(100.1, bundle.get("double")?.value)
    }

    @Test
    fun clear_RemovesAll_StoredData() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .putAll(testBundle, Expiry.SESSION)
            .commit()
        assertEquals(testBundle.count(), dataStore.count())

        dataStore.edit()
            .clear()
            .commit()

        dataStore.assertEmpty()
    }

    @Test
    fun clear_RemovesOnly_SpecifiedModuleStoredData() {
        val dataStore1 = getPrepopulatedDataStore(module1)
        val dataStore2 = getPrepopulatedDataStore(module2)

        dataStore1.edit()
            .clear()
            .commit()

        dataStore1.assertEmpty()
        assertEquals(testBundle.count(), dataStore2.count())
    }


    @Test
    fun remove_Removes_StoredData() {
        val dataStore = getPrepopulatedDataStore(module1)

        dataStore.prepopulate(testBundle)
        dataStore.get("string").assertNotNull()

        dataStore.edit()
            .remove("string")
            .commit()

        dataStore.get("string").assertNull()
        dataStore.get("int").assertNotNull()
        dataStore.get("long").assertNotNull()
        dataStore.get("double").assertNotNull()
    }

    @Test
    fun remove_RemovesOnly_SpecifiedModuleStoredData() {
        val dataStore1 = getPrepopulatedDataStore(module1)
        val dataStore2 = getPrepopulatedDataStore(module2)
        dataStore1.get("string").assertNotNull()
        dataStore2.get("string").assertNotNull()

        dataStore1.edit()
            .remove("string")
            .commit()

        // dataStore1
        dataStore1.get("string").assertNull()
        dataStore1.get("int").assertNotNull()
        dataStore1.get("long").assertNotNull()
        dataStore1.get("double").assertNotNull()
        // dataStore2
        dataStore2.get("string").assertNotNull()
        dataStore2.get("int").assertNotNull()
        dataStore2.get("long").assertNotNull()
        dataStore2.get("double").assertNotNull()
    }

    @Test
    fun clear_RemovesAll_StoredValues_BeforeAddingNew() {
        val dataStore = getPrepopulatedDataStore(module1)
        dataStore.get("string").assertNotNull()

        dataStore.edit()
            .clear()
            .put("new_string", TealiumValue.string("new_value"), Expiry.SESSION)
            .put("new_int", TealiumValue.int(10), Expiry.SESSION)
            .commit()

        dataStore.get("new_string")
            .assertValueEquals("new_value")
        dataStore.get("new_int")
            .assertValueEquals(10)

        dataStore.get("string").assertNull()
        dataStore.get("int").assertNull()
        dataStore.get("long").assertNull()
        dataStore.get("double").assertNull()
    }

    @Test
    fun get_DoesNotReturn_ExpiredData() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .put(
                "expired",
                TealiumValue.string("expired"),
                Expiry.afterEpochTime(getTimestamp() - 10000)
            )
            .put(
                "not_expired", TealiumValue.string("not_expired"),
                Expiry.afterEpochTime(getTimestamp() + 10000)
            )
            .commit()

        dataStore.get("expired").assertNull()
        dataStore.get("not_expired").assertValueEquals("not_expired")
    }

    @Test
    fun getAll_DoesNotReturn_ExpiredData() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .put(
                "expired",
                TealiumValue.string("expired"),
                Expiry.afterEpochTime(getTimestamp() - 10000)
            )
            .put(
                "not_expired", TealiumValue.string("not_expired"),
                Expiry.afterEpochTime(getTimestamp() + 10000)
            )
            .commit()

        val allData = dataStore.getAll()
        assertEquals(1, allData.count())
        allData.get("expired").assertNull()
        allData.get("not_expired").assertValueEquals("not_expired")
    }

    @Test
    fun count_DoesNotInclude_ExpiredData() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .put(
                "expired",
                TealiumValue.string("expired"),
                Expiry.afterEpochTime(getTimestamp() - 10000)
            )
            .put(
                "not_expired", TealiumValue.string("not_expired"),
                Expiry.afterEpochTime(getTimestamp() + 10000)
            )
            .commit()

        assertEquals(1, dataStore.count())
    }

    @Test
    fun keys_DoesNotReturn_ExpiredData() {
        val dataStore = getEmptyDataStore(module1)

        dataStore.edit()
            .put(
                "expired",
                TealiumValue.string("expired"),
                Expiry.afterEpochTime(getTimestamp() - 10000)
            )
            .put(
                "not_expired", TealiumValue.string("not_expired"),
                Expiry.afterEpochTime(getTimestamp() + 10000)
            )
            .commit()

        val keys = dataStore.keys()
        assertEquals(1, keys.count())
        assertFalse(keys.contains("expired"))
        assertTrue(keys.contains("not_expired"))
    }

    private fun getEmptyDataStore(module: Module): DataStore {
        return dataStoreFactory.getDataStore(module).also {
            it.assertEmpty()
        }
    }

    private fun getPrepopulatedDataStore(
        module: Module,
        populatedWith: TealiumBundle = testBundle,
        expiry: Expiry = Expiry.SESSION
    ): DataStore {
        return dataStoreFactory.getDataStore(module).also {
            it.prepopulate(populatedWith, expiry)
        }
    }

    private fun DataStore.assertEmpty(): Boolean {
        return count() == 0
    }

    private fun DataStore.prepopulate(bundle: TealiumBundle, expiry: Expiry = Expiry.SESSION) {
        edit()
            .putAll(bundle, expiry)
            .commit()

        assertEquals(bundle.count(), count())
    }

    private fun TealiumValue?.assertNull() {
        assertNull(this)
    }

    private fun TealiumValue?.assertNotNull() {
        assertNotNull(this)
    }

    private fun TealiumValue?.assertValueEquals(expected: Any) {
        if (this == null) {
            fail("TealiumValue was null")
        }
        assertEquals(expected, this!!.value)
    }
}
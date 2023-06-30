package com.tealium.core.internal.persistence

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.core.Environment
import com.tealium.core.TealiumConfig
import com.tealium.core.api.Expiry
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.data.bundle.TealiumList
import com.tealium.core.api.data.bundle.TealiumValue
import com.tealium.tests.common.TestModule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@Suppress("UNCHECKED_CAST")
class SQLiteStorageStrategyTests {

    private lateinit var app: Application
    private lateinit var dbProvider: DatabaseProvider
    private lateinit var moduleRepository: ModuleStorageRepositoryImpl

    private var module1Id: Long = -1
    private val module1 = TestModule("module1")
    private var module2Id: Long = -1
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
        dbProvider = InMemoryDatabaseProvider(config)
        // Repositories
        moduleRepository = ModuleStorageRepositoryImpl(
            dbProvider
        )
        module1Id = moduleRepository.registerModule(module1.name)
        module2Id = moduleRepository.registerModule(module2.name)
    }

    @Test
    fun count_Returns_ItemCount() {
        val storage = getEmptyStorage(module1Id)

        storage.insert("key", TealiumValue.convert("value"), Expiry.SESSION)

        assertEquals(1, storage.count())
    }

    @Test
    fun count_ReturnsOnly_SpecifiedModuleItemCount() {
        val storage1 = getEmptyStorage(module1Id)
        val storage2 = getEmptyStorage(module2Id)

        storage1.insert("key", TealiumValue.convert("value"), Expiry.SESSION)

        assertEquals(1, storage1.count())
        storage2.assertEmpty()
    }

    @Test
    fun keys_ReturnsOnly_StoredKeys() {
        val storage = getEmptyStorage(module1Id)
        assertTrue(storage.keys().isEmpty())

        storage.insert("key", TealiumValue.convert("value"), Expiry.SESSION)

        assertEquals(1, storage.keys().count())
        assertEquals("key", storage.keys().first())
    }

    @Test
    fun keys_ReturnsOnly_SpecifiedModuleKeys() {
        val storage1 = getEmptyStorage(module1Id)
        val storage2 = getEmptyStorage(module2Id)
        assertTrue(storage1.keys().isEmpty())
        assertTrue(storage2.keys().isEmpty())

        storage1.insert("key1", TealiumValue.convert("value"), Expiry.SESSION)
        storage2.insert("key2", TealiumValue.convert("value"), Expiry.SESSION)

        assertEquals(1, storage1.keys().count())
        assertEquals(1, storage2.keys().count())
        assertEquals("key1", storage1.keys().first())
        assertEquals("key2", storage2.keys().first())
    }

    @Test
    fun get_Returns_StringValue() {
        val storage = getEmptyStorage(module1Id)

        storage.insert("key", TealiumValue.convert("value"), Expiry.SESSION)

        assertEquals("value", storage.get("key")?.value)
    }

    @Test
    fun get_Returns_IntValue() {
        val storage = getEmptyStorage(module1Id)

        storage.insert("key", TealiumValue.convert(10), Expiry.SESSION)

        assertEquals(10, storage.get("key")?.value)
    }

    @Test
    fun get_Returns_LongValue() {
        val storage = getEmptyStorage(module1Id)

        storage.insert("key", TealiumValue.convert(100L), Expiry.SESSION)

        assertEquals(100L, storage.get("key")?.value)
    }

    @Test
    fun get_Returns_DoubleValue() {
        val storage = getEmptyStorage(module1Id)

        storage.insert("key", TealiumValue.convert(100.100), Expiry.SESSION)

        assertEquals(100.100, storage.get("key")?.value)
    }

    @Test
    fun get_Returns_ListValue() {
        val storage = getEmptyStorage(module1Id)

        storage.insert(
            "key", TealiumValue.convert(
                arrayOf(1, 2, 3)
            ), Expiry.SESSION
        )

        assertArrayEquals(
            arrayOf(1, 2, 3),
            (storage.get("key")?.value as TealiumList)
                .map { it.getInt() }
                .toTypedArray()
        )
    }

    @Test
    fun get_Returns_MixedListValue() {
        val storage = getEmptyStorage(module1Id)

        storage.insert(
            "key", TealiumValue.convert(
                arrayOf(1, "", true)
            ), Expiry.SESSION
        )

        assertArrayEquals(
            arrayOf(1, "", true),
            (storage.get("key")?.value as TealiumList)
                .map { it.value }
                .toTypedArray()
        )
    }

    @Test
    fun get_Returns_BundleValue() {
        val storage = getEmptyStorage(module1Id)

        storage.insert("key", TealiumValue.convert(testBundle), Expiry.SESSION)
        val storedBundle = storage.get("key")?.getBundle()!!

        assertEquals("value", storedBundle.getString("string"))
        assertEquals(1, storedBundle.getInt("int"))
        // TODO - add type coercions between numerics
        assertEquals(10, storedBundle.getInt("long"))
        assertEquals(100.1, storedBundle.getDouble("double"))
    }

    @Test
    fun getAll_ReturnsAll_Values() {
        val storage = getPrepopulatedStorage(module1Id, populatedWith = testBundle)

        val bundle = storage.getAll()
        assertEquals("value", bundle.get("string")?.value)
        assertEquals(1, bundle.get("int")?.value)
        assertEquals(10L, bundle.get("long")?.value)
        assertEquals(100.1, bundle.get("double")?.value)
    }

    @Test
    fun clear_RemovesAll_StoredData() {
        val storage = getPrepopulatedStorage(module1Id, populatedWith = testBundle)
        assertEquals(testBundle.count(), storage.count())

        storage.clear()

        storage.assertEmpty()
    }

    @Test
    fun clear_RemovesOnly_SpecifiedModuleStoredData() {
        val storage1 = getPrepopulatedStorage(module1Id, populatedWith = testBundle)
        val storage2 = getPrepopulatedStorage(module2Id, populatedWith = testBundle)

        storage1.clear()

        storage1.assertEmpty()
        assertEquals(testBundle.count(), storage2.count())
    }

    @Test
    fun delete_Removes_StoredData() {
        val storage = getPrepopulatedStorage(module1Id)

        storage.prepopulate(testBundle)
        storage.get("string").assertNotNull()

        storage.delete("string")

        storage.get("string").assertNull()
        storage.get("int").assertNotNull()
        storage.get("long").assertNotNull()
        storage.get("double").assertNotNull()
    }

    @Test
    fun delete_RemovesOnly_SpecifiedModuleStoredData() {
        val storage1 = getPrepopulatedStorage(module1Id)
        val storage2 = getPrepopulatedStorage(module2Id)
        storage1.get("string").assertNotNull()
        storage2.get("string").assertNotNull()

        storage1.delete("string")

        // storage1
        storage1.get("string").assertNull()
        storage1.get("int").assertNotNull()
        storage1.get("long").assertNotNull()
        storage1.get("double").assertNotNull()
        // storage2
        storage2.get("string").assertNotNull()
        storage2.get("int").assertNotNull()
        storage2.get("long").assertNotNull()
        storage2.get("double").assertNotNull()
    }

    @Test
    fun get_DoesNotReturn_ExpiredData() {
        val storage = getEmptyStorage(module1Id)

        storage.insert(
            "expired",
            TealiumValue.string("expired"),
            Expiry.afterEpochTime(getTimestamp() - 10000)
        )
        storage.insert(
            "not_expired", TealiumValue.string("not_expired"),
            Expiry.afterEpochTime(getTimestamp() + 10000)
        )

        storage.get("expired").assertNull()
        storage.get("not_expired").assertValueEquals("not_expired")
    }

    @Test
    fun getAll_DoesNotReturn_ExpiredData() {
        val storage = getEmptyStorage(module1Id)

        storage.insert(
            "expired",
            TealiumValue.string("expired"),
            Expiry.afterEpochTime(getTimestamp() - 10000)
        )
        storage.insert(
            "not_expired", TealiumValue.string("not_expired"),
            Expiry.afterEpochTime(getTimestamp() + 10000)
        )

        val allData = storage.getAll()
        assertEquals(1, allData.count())
        allData.get("expired").assertNull()
        allData.get("not_expired").assertValueEquals("not_expired")
    }

    @Test
    fun count_DoesNotInclude_ExpiredData() {
        val storage = getEmptyStorage(module1Id)

        storage.insert(
            "expired",
            TealiumValue.string("expired"),
            Expiry.afterEpochTime(getTimestamp() - 10000)
        )
        storage.insert(
            "not_expired", TealiumValue.string("not_expired"),
            Expiry.afterEpochTime(getTimestamp() + 10000)
        )

        assertEquals(1, storage.count())
    }

    @Test
    fun keys_DoesNotReturn_ExpiredData() {
        val storage = getEmptyStorage(module1Id)

        storage.insert(
            "expired",
            TealiumValue.string("expired"),
            Expiry.afterEpochTime(getTimestamp() - 10000)
        )
        storage.insert(
            "not_expired", TealiumValue.string("not_expired"),
            Expiry.afterEpochTime(getTimestamp() + 10000)
        )

        val keys = storage.keys()
        assertEquals(1, keys.count())
        assertFalse(keys.contains("expired"))
        assertTrue(keys.contains("not_expired"))
    }

    @Test
    fun update_UpdatesAllData_ForExistingEntry() {
        val storage = getPrepopulatedStorage(module1Id)

        storage.update(
            "string", TealiumValue.string("new_value"),
            Expiry.FOREVER
        )

        val value = storage.get("string")!!
        assertEquals("new_value", value.value)
        assertEquals(Expiry.FOREVER, storage.getExpiry("string"))
    }

    @Test
    fun update_DoesNothing_WhenEntryDoesntExist() {
        val storage = getPrepopulatedStorage(module1Id)

        storage.update(
            "non_existient_key", TealiumValue.string("new_value"),
            Expiry.FOREVER
        )

        val value = storage.get("non_existient_key")
        assertNull(value)
        assertNull(storage.getExpiry("non_existient_key"))
    }

    @Test
    fun insert_Inserts_WhenEntryDoesntExist() {
        val storage = getPrepopulatedStorage(module1Id)

        storage.insert(
            "new_string", TealiumValue.string("new_value"),
            Expiry.FOREVER
        )

        val value = storage.get("new_string")!!
        assertEquals("new_value", value.value)
        assertEquals(Expiry.FOREVER, storage.getExpiry("new_string"))
    }

    @Test
    fun insert_Replaces_WhenEntryAlreadyExists() {
        val storage = getPrepopulatedStorage(module1Id)

        storage.insert(
            "string", TealiumValue.string("new_value"),
            Expiry.FOREVER
        )

        val value = storage.get("string")!!
        assertEquals("new_value", value.value)
        assertEquals(Expiry.FOREVER, storage.getExpiry("string"))
    }

    @Test
    fun upsert_Inserts_WhenEntryDoesntExist() {
        val storage = getPrepopulatedStorage(module1Id)

        storage.upsert(
            "new_string", TealiumValue.string("new_value"),
            Expiry.FOREVER
        )

        val value = storage.get("new_string")!!
        assertEquals("new_value", value.value)
        assertEquals(Expiry.FOREVER, storage.getExpiry("new_string"))
    }

    @Test
    fun upsert_Updates_WhenEntryExists() {
        val storage = getPrepopulatedStorage(module1Id)

        storage.upsert(
            "string", TealiumValue.string("new_value"),
            Expiry.FOREVER
        )

        val value = storage.get("string")!!
        assertEquals("new_value", value.value)
        assertEquals(Expiry.FOREVER, storage.getExpiry("string"))
    }

    @Test
    fun contains_ReturnsTrue_WhenKeyExists() {
        val storage = getPrepopulatedStorage(module1Id)

        assertTrue(storage.contains("string"))
    }

    @Test
    fun contains_ReturnsFalse_WhenKeyDoesntExist() {
        val storage = getPrepopulatedStorage(module1Id)

        assertFalse(storage.contains("non-existent"))
    }

    @Test
    fun contains_ReturnsFalse_WhenKeyIsExpired() {
        val storage = getPrepopulatedStorage(module1Id)

        storage.insert(
            "expired",
            TealiumValue.string("expired"),
            Expiry.afterEpochTime(getTimestamp() - 10000)
        )

        assertFalse(storage.contains("expired"))
    }

    private fun getEmptyStorage(moduleId: Long): SQLiteStorageStrategy {
        return SQLiteStorageStrategy(dbProvider, moduleId).also {
            it.assertEmpty()
        }
    }

    private fun getPrepopulatedStorage(
        moduleId: Long,
        populatedWith: TealiumBundle = testBundle,
        expiry: Expiry = Expiry.SESSION
    ): SQLiteStorageStrategy {
        return getEmptyStorage(moduleId).also {
            it.prepopulate(populatedWith, expiry)
        }
    }

    private fun DataStorageStrategy<*, *>.assertEmpty(): Boolean {
        return count() == 0
    }

    private fun DataStorageStrategy<String, TealiumValue>.prepopulate(
        bundle: TealiumBundle,
        expiry: Expiry = Expiry.SESSION
    ) {
        transactionally({
            fail(it.message)
        }) {
            bundle.forEach {
                upsert(it.key, it.value, expiry)
            }
        }

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
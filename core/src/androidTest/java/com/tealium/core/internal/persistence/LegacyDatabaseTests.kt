package com.tealium.core.internal.persistence

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.tealium.core.internal.persistence.DatabaseTestUtils.upgrade
import com.tealium.core.internal.persistence.repositories.SQLKeyValueRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * This test suite should cover all possible existing data.
 */
class LegacyDatabaseTests {

    // TODO - setup db with

    private lateinit var app: Application
    private lateinit var dbProvider: DatabaseProvider
    private lateinit var database: SQLiteDatabase

    private lateinit var dataStore: ModuleStore

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext() as Application
        dbProvider = mockk()
        database = DatabaseTestUtils.createV1Database(app)
        every { dbProvider.database } returns database

        DatabaseTestUtils.populateV1Database(database)

        database.upgrade(1, 3)

        dataStore = ModuleStore(
            SQLKeyValueRepository(
                dbProvider = dbProvider,
                moduleId = 1 // Upgraded db migrates data layer as id 1
            ),
        )
    }

    @Test
    fun dataStore_get_String_Returns_String() {
        val value = dataStore.get("string")!!

        assertEquals("value with spaces", value.getString())
    }

    @Test
    fun dataStore_get_Int_Returns_Int() {
        val value = dataStore.get("int")!!

        assertEquals(10, value.getInt())
    }

    @Test
    fun dataStore_get_Long_Returns_Long() {
        val value = dataStore.get("long")!!

        assertEquals(100L, value.getLong())
    }

    @Test
    fun dataStore_get_Double_Returns_Double() {
        val value = dataStore.get("double")!!

        assertEquals(100.1, value.getDouble())
    }

    @Test
    fun dataStore_get_BooleanTrue_Returns_BooleanTrue() {
        val value = dataStore.get("boolean_true")!!

        assertEquals(true, value.getBoolean())
    }

    @Test
    fun dataStore_get_BooleanFalse_Returns_BooleanFalse() {
        val value = dataStore.get("boolean_false")!!

        assertEquals(false, value.getBoolean())
    }

    @Test
    fun dataStore_get_StringArray_Returns_DataList() {
        val value = dataStore.get("string_array")!!.getDataList()!!

        assertEquals("value1", value.getString(0))
        assertEquals("value2", value.getString(1))
        assertEquals("value3", value.getString(2))
    }

    @Test
    fun dataStore_get_IntArray_Returns_DataList() {
        val value = dataStore.get("int_array")!!.getDataList()!!

        assertEquals(1, value.getInt(0))
        assertEquals(2, value.getInt(1))
        assertEquals(3, value.getInt(2))
    }

    @Test
    fun dataStore_get_LongArray_Returns_DataList() {
        val value = dataStore.get("long_array")!!.getDataList()!!

        assertEquals(100L, value.getLong(0))
        assertEquals(200L, value.getLong(1))
        assertEquals(300L, value.getLong(2))
    }

    @Test
    fun dataStore_get_DoubleArray_Returns_DataList() {
        val value = dataStore.get("double_array")!!.getDataList()!!

        assertEquals(1.1, value.getDouble(0))
        assertEquals(2.2, value.getDouble(1))
        assertEquals(3.3, value.getDouble(2))
    }

    @Test
    fun dataStore_get_BooleanArray_Returns_DataList() {
        val value = dataStore.get("boolean_array")!!.getDataList()!!

        assertEquals(false, value.getBoolean(0))
        assertEquals(true, value.getBoolean(1))
        assertEquals(false, value.getBoolean(2))
    }

    @Test
    fun dataStore_get_JSONArray_Returns_DataList() {
        val value = dataStore.get("json_array")!!.getDataList()!!

        assertEquals("test", value.getString(0))
        assertEquals(1, value.getInt(1))
        assertEquals(true, value.getBoolean(2))
    }

    @Test
    fun dataStore_get_JSONObject_Returns_DataObject() {
        val value = dataStore.get("json_object")!!.getDataObject()!!

        assertEquals("value", value.getString("string"))
        assertEquals(1, value.getInt("int"))
        assertEquals(true, value.getBoolean("boolean"))
    }
}
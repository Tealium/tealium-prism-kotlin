package com.tealium.prism.core.api.network

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.data.TestDataObjectConvertible
import com.tealium.prism.core.api.data.TestDataListConvertible
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.internal.network.ResourceCacheImpl
import com.tealium.prism.core.internal.persistence.stores.getSharedDataStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResourceCacheTests {

    private val list = TestDataListConvertible("value", 10)
    private val dataObject = TestDataObjectConvertible("value", 10)

    private lateinit var dataObjectCacher: ResourceCache<TestDataObjectConvertible>
    private lateinit var dataListCacher: ResourceCache<TestDataListConvertible>
    private lateinit var dataStore: DataStore
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()

        dataStore = getSharedDataStore(app)

        dataObjectCacher = ResourceCacheImpl(dataStore, "object", TestDataObjectConvertible.Converter)
        dataListCacher = ResourceCacheImpl(dataStore, "list", TestDataListConvertible.Converter)
    }

    @Test
    fun readResource_Returns_Null_When_Nothing_Cached() {
        assertNull(dataObjectCacher.resource)
        assertNull(dataListCacher.resource)
    }

    @Test
    fun readResource_Returns_Null_When_Not_Convertible() {
        val dataListConvertible = TestDataListConvertible("value", 10)
        val dataObjectConvertible = TestDataObjectConvertible("value", 10)
        dataStore.edit()
            .put("object", dataListConvertible, Expiry.FOREVER)
            .put("list", dataObjectConvertible, Expiry.FOREVER)
            .commit()

        assertNull(dataObjectCacher.resource)
        assertNull(dataListCacher.resource)
    }

    @Test
    fun readResource_Returns_Valid_Instance_When_Cache_Is_Convertible() {
        dataStore.edit()
            .put("object", dataObject, Expiry.FOREVER)
            .put("list", list, Expiry.FOREVER)
            .commit()

        val convertedDataObject = dataObjectCacher.resource!!
        val convertedDataList = dataListCacher.resource!!

        assertEquals("value", convertedDataObject.string)
        assertEquals(10, convertedDataObject.int)
        assertEquals("value", convertedDataList.string)
        assertEquals(10, convertedDataList.int)
    }

    @Test
    fun readEtag_Returns_Null_When_Not_Present() {
        assertNull(dataObjectCacher.etag)
        assertNull(dataListCacher.etag)
    }

    @Test
    fun readEtag_Returns_Etag_When_Present() {
        dataStore.edit()
            .put("object_etag", "12345", Expiry.FOREVER)
            .put("list_etag", "67890", Expiry.FOREVER)
            .commit()

        assertEquals("12345", dataObjectCacher.etag)
        assertEquals("67890", dataListCacher.etag)
    }

    @Test
    fun saveResource_Saves_Resource_Without_Etag() {
        dataObjectCacher.saveResource(dataObject, null)
        dataListCacher.saveResource(list, null)

        assertEquals(dataObject, dataObjectCacher.resource)
        assertEquals(list, dataListCacher.resource)

        assertNull(dataObjectCacher.etag)
        assertNull(dataListCacher.etag)
    }

    @Test
    fun saveResource_Saves_Resource_With_Etag() {
        dataObjectCacher.saveResource(dataObject, "12345")
        dataListCacher.saveResource(list, "67890")

        assertEquals(dataObject, dataObjectCacher.resource)
        assertEquals(list, dataListCacher.resource)

        assertEquals("12345", dataObjectCacher.etag)
        assertEquals("67890", dataListCacher.etag)
    }

    @Test
    fun saveResource_Removes_Existing_Etag_When_New_Etag_Is_Null() {
        dataObjectCacher.saveResource(dataObject, "12345")
        dataObjectCacher.saveResource(dataObject, null)
        dataListCacher.saveResource(list, "67890")
        dataListCacher.saveResource(list, null)

        assertNull(dataObjectCacher.etag)
        assertNull(dataListCacher.etag)
    }
}
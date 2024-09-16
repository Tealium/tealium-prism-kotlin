package com.tealium.core.api.network

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.core.api.data.TestBundleSerializable
import com.tealium.core.api.data.TestListSerializable
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.internal.network.ResourceCacheImpl
import com.tealium.core.internal.persistence.getSharedDataStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResourceCacheTests {

    private val list = TestListSerializable("value", 10)
    private val bundle = TestBundleSerializable("value", 10)

    private lateinit var bundleCacher: ResourceCache<TestBundleSerializable>
    private lateinit var listCacher: ResourceCache<TestListSerializable>
    private lateinit var dataStore: DataStore
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()

        dataStore = getSharedDataStore(app)

        bundleCacher = ResourceCacheImpl(dataStore, "bundle", TestBundleSerializable.Deserializer)
        listCacher = ResourceCacheImpl(dataStore, "list", TestListSerializable.Deserializer)
    }

    @Test
    fun readResource_Returns_Null_When_Nothing_Cached() {
        assertNull(bundleCacher.resource)
        assertNull(listCacher.resource)
    }

    @Test
    fun readResource_Returns_Null_When_Not_Deserializable() {
        val list = TestListSerializable("value", 10)
        val bundle = TestBundleSerializable("value", 10)
        dataStore.edit()
            .put("bundle", list, Expiry.FOREVER)
            .put("list", bundle, Expiry.FOREVER)
            .commit()

        assertNull(bundleCacher.resource)
        assertNull(listCacher.resource)
    }

    @Test
    fun readResource_Returns_Valid_Instance_When_Cache_Is_Deserializable() {
        dataStore.edit()
            .put("bundle", bundle, Expiry.FOREVER)
            .put("list", list, Expiry.FOREVER)
            .commit()

        val deserializedBundle = bundleCacher.resource!!
        val deserializedList = listCacher.resource!!

        assertEquals("value", deserializedBundle.string)
        assertEquals(10, deserializedBundle.int)
        assertEquals("value", deserializedList.string)
        assertEquals(10, deserializedList.int)
    }

    @Test
    fun readEtag_Returns_Null_When_Not_Present() {
        assertNull(bundleCacher.etag)
        assertNull(listCacher.etag)
    }

    @Test
    fun readEtag_Returns_Etag_When_Present() {
        dataStore.edit()
            .put("bundle_etag", "12345", Expiry.FOREVER)
            .put("list_etag", "67890", Expiry.FOREVER)
            .commit()

        assertEquals("12345", bundleCacher.etag)
        assertEquals("67890", listCacher.etag)
    }

    @Test
    fun saveResource_Saves_Resource_Without_Etag() {
        bundleCacher.saveResource(bundle, null)
        listCacher.saveResource(list, null)

        assertEquals(bundle, bundleCacher.resource)
        assertEquals(list, listCacher.resource)

        assertNull(bundleCacher.etag)
        assertNull(listCacher.etag)
    }

    @Test
    fun saveResource_Saves_Resource_With_Etag() {
        bundleCacher.saveResource(bundle, "12345")
        listCacher.saveResource(list, "67890")

        assertEquals(bundle, bundleCacher.resource)
        assertEquals(list, listCacher.resource)

        assertEquals("12345", bundleCacher.etag)
        assertEquals("67890", listCacher.etag)
    }

    @Test
    fun saveResource_Removes_Existing_Etag_When_New_Etag_Is_Null() {
        bundleCacher.saveResource(bundle, "12345")
        bundleCacher.saveResource(bundle, null)
        listCacher.saveResource(list, "67890")
        listCacher.saveResource(list, null)

        assertNull(bundleCacher.etag)
        assertNull(listCacher.etag)
    }
}
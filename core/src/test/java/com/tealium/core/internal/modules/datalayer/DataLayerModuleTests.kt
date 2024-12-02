package com.tealium.core.internal.modules.datalayer

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.ModuleStoreProvider
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class DataLayerModuleTests {

    @MockK
    private lateinit var dataStore: DataStore

    private lateinit var dataLayer: DataLayerModule

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        dataLayer = DataLayerModule(dataStore)
    }

    @Test
    fun id_Matches_Factory_Id() {
        assertEquals(DataLayerModule.id, dataLayer.id)
    }

    @Test
    fun collect_Returns_All_Items_In_DataStore() {
        val data = DataObject.create { put("key", "value") }
        every { dataStore.getAll() } returns data

        assertEquals(data, dataLayer.collect())
    }

    @Test
    fun dataStore_Returns_Injected_DataStore() {
        assertSame(dataStore, dataLayer.dataStore)
    }

    @Test
    fun factory_Create_Returns_New_Instance_Each_Time() {
        val context = mockk<TealiumContext>()
        val storageProvider = mockk<ModuleStoreProvider>()
        every { context.storageProvider } returns storageProvider
        every {storageProvider.getModuleStore(any<ModuleFactory>()) } returns dataStore

        val dataLayer1 = DataLayerModule.create(context, DataObject.EMPTY_OBJECT)
        val dataLayer2 = DataLayerModule.create(context, DataObject.EMPTY_OBJECT)

        assertNotSame(dataLayer1, dataLayer2)
    }
}
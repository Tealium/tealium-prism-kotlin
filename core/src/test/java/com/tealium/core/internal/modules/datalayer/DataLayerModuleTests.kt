package com.tealium.core.internal.modules.datalayer

import com.tealium.core.api.Modules
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.ModuleStoreProvider
import com.tealium.core.api.tracking.DispatchContext
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
    fun id_Matches_Factory_ModuleType() {
        assertEquals(DataLayerModule.Factory().moduleType, dataLayer.id)
    }

    @Test
    fun collect_Returns_All_Items_In_DataStore() {
        val data = DataObject.create { put("key", "value") }
        every { dataStore.getAll() } returns data
        val dispatchContext =
            DispatchContext(DispatchContext.Source.application(), DataObject.EMPTY_OBJECT)

        assertEquals(data, dataLayer.collect(dispatchContext))
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
        every { storageProvider.getModuleStore(any<String>()) } returns dataStore

        val factory = DataLayerModule.Factory()
        val dataLayer1 = factory.create(Modules.Types.DATA_LAYER, context, DataObject.EMPTY_OBJECT)
        val dataLayer2 = factory.create(Modules.Types.DATA_LAYER, context, DataObject.EMPTY_OBJECT)

        assertNotSame(dataLayer1, dataLayer2)
    }
}
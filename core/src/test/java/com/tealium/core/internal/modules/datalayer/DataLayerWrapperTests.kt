package com.tealium.core.internal.modules.datalayer

import com.tealium.core.api.Modules
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemUtils.asDataItem
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.TestDataObjectConvertible
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumException
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.ModuleNotEnabledException
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.api.pubsub.ReplaySubject
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.ModuleProxyImpl
import com.tealium.core.internal.persistence.stores.ModuleStore
import com.tealium.core.internal.settings.SdkSettings
import com.tealium.tests.common.SynchronousScheduler
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.mockkEditor
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataLayerWrapperTests {

    @MockK
    private lateinit var dataStore: DataStore

    @RelaxedMockK
    private lateinit var editor: DataStore.Editor

    @MockK
    private lateinit var context: TealiumContext

    private lateinit var moduleManager: ModuleManagerImpl
    private lateinit var moduleManagerSubject: ReplaySubject<ModuleManager?>
    private lateinit var onDataUpdated: Subject<DataObject>
    private lateinit var onDataRemoved: Subject<List<String>>
    private lateinit var moduleProxy: ModuleProxy<DataLayerModule>
    private lateinit var dataLayerModule: DataLayerModule
    private lateinit var dataLayerWrapper: DataLayerWrapper
    private val defaultExpiry = Expiry.UNTIL_RESTART

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        onDataUpdated = Observables.publishSubject()
        onDataRemoved = Observables.publishSubject()

        mockkEditor(editor)
        every { dataStore.edit() } returns editor
        every { dataStore.onDataUpdated } returns onDataUpdated
        every { dataStore.onDataRemoved } returns onDataRemoved
        every { context.logger } returns SystemLogger

        dataLayerModule = DataLayerModule(dataStore, defaultExpiry)
        moduleManager = ModuleManagerImpl(
            SynchronousScheduler()
        )
        moduleManager.addModuleFactory(MockDataLayerFactory(dataLayerModule))
        moduleManager.updateModuleSettings(context, SdkSettings())

        // default to pre-enabled
        moduleManagerSubject = Observables.replaySubject()
        moduleManagerSubject.onNext(moduleManager)

        moduleProxy = ModuleProxyImpl(
            DataLayerModule::class.java,
            moduleManagerSubject,
            SynchronousScheduler()
        )

        dataLayerWrapper = DataLayerWrapper(moduleProxy)
    }

    @Test
    fun transactionally_Executes_Block() {
        dataLayerWrapper.transactionally {
            it.put("key", "value", Expiry.SESSION)
                .put("key", 1, Expiry.FOREVER)
                .commit()
        }

        verify {
            editor.put("key", "value", Expiry.SESSION)
            editor.put("key", 1, Expiry.FOREVER)
            editor.commit()
        }
    }

    @Test
    fun transactionally_Closes_Editor_Outside_Block() {
        var editor: DataStore.Editor? = null
        dataLayerWrapper.transactionally {
            editor = it
        }

        verify {
            editor!!.close()
        }
    }

    @Test(expected = TealiumException::class)
    fun transactionally_Editor_Throws_Outside_Block() {
        every { dataStore.edit() } returns ModuleStore.DataStorageEditor(mockk(), mockk()) {}
        var realEditor: DataStore.Editor? = null
        dataLayerWrapper.transactionally {
            realEditor = it
        }

        realEditor!!.put("key", "value", Expiry.SESSION)
            .commit()
    }

    @Test
    fun transactionally_Does_Not_Automatically_Commit() {
        dataLayerWrapper.transactionally {
            it.put("key", "value", Expiry.SESSION)
        }

        verify(inverse = true) {
            editor.commit()
        }
    }

    @Test
    fun transactionally_Does_Nothing_When_ModuleManager_Shutdown() {
        val block = mockk<TealiumCallback<DataStore.Editor>>(relaxed = true)

        moduleManager.shutdown()
        dataLayerWrapper.transactionally(block)

        verify {
            block wasNot Called
        }
    }

    @Test
    fun putAll_Adds_All_Values_To_DataStore() {
        val data = DataObject.create { put("key", "value") }
        dataLayerWrapper.put(data, Expiry.SESSION)

        verify {
            editor.putAll(data, Expiry.SESSION)
            editor.commit()
        }
    }

    @Test
    fun putAll_Uses_Default_Expiry_When_Omitted() {
        val data = DataObject.create { put("key", "value") }
        dataLayerWrapper.put(data)

        verify {
            editor.putAll(data, defaultExpiry)
            editor.commit()
        }
    }

    @Test
    fun putString_Adds_Single_Value_To_DataStore() {
        dataLayerWrapper.put("key", "value", Expiry.SESSION)

        verify {
            editor.put("key", DataItem.string("value"), Expiry.SESSION)
            editor.commit()
        }
    }

    @Test
    fun putString_Uses_Default_Expiry_When_Omitted() {
        dataLayerWrapper.put("key", "value")

        verify {
            editor.put("key", DataItem.string("value"), defaultExpiry)
            editor.commit()
        }
    }

    @Test
    fun putInt_Adds_Single_Value_To_DataStore() {
        dataLayerWrapper.put("key", 1, Expiry.SESSION)

        verify {
            editor.put("key", DataItem.int(1), Expiry.SESSION)
            editor.commit()
        }
    }

    @Test
    fun putInt_Uses_Default_Expiry_When_Omitted() {
        dataLayerWrapper.put("key", 1)

        verify {
            editor.put("key", DataItem.int(1), defaultExpiry)
            editor.commit()
        }
    }

    @Test
    fun putDouble_Adds_Single_Value_To_DataStore() {
        dataLayerWrapper.put("key", 1.1, Expiry.SESSION)

        verify {
            editor.put("key", DataItem.double(1.1), Expiry.SESSION)
            editor.commit()
        }
    }

    @Test
    fun putDouble_Uses_Default_Expiry_When_Omitted() {
        dataLayerWrapper.put("key", 1.1)

        verify {
            editor.put("key", DataItem.double(1.1), defaultExpiry)
            editor.commit()
        }
    }

    @Test
    fun putLong_Adds_Single_Value_To_DataStore() {
        dataLayerWrapper.put("key", 100L, Expiry.SESSION)

        verify {
            editor.put("key", DataItem.long(100L), Expiry.SESSION)
            editor.commit()
        }
    }

    @Test
    fun putLong_Uses_Default_Expiry_When_Omitted() {
        dataLayerWrapper.put("key", 100L)

        verify {
            editor.put("key", DataItem.long(100L), defaultExpiry)
            editor.commit()
        }
    }

    @Test
    fun putBoolean_Adds_Single_Value_To_DataStore() {
        dataLayerWrapper.put("key", false, Expiry.SESSION)

        verify {
            editor.put("key", DataItem.boolean(false), Expiry.SESSION)
            editor.commit()
        }
    }

    @Test
    fun putBoolean_Uses_Default_Expiry_When_Omitted() {
        dataLayerWrapper.put("key", false)

        verify {
            editor.put("key", DataItem.boolean(false), defaultExpiry)
            editor.commit()
        }
    }

    @Test
    fun putDataList_Adds_Single_Value_To_DataStore() {
        val dataList = DataList.create { add("string") }
        dataLayerWrapper.put("key", dataList, Expiry.SESSION)

        verify {
            editor.put("key", match<DataItem> { it.value == dataList }, Expiry.SESSION)
            editor.commit()
        }
    }

    @Test
    fun putDataList_Uses_Default_Expiry_When_Omitted() {
        val dataList = DataList.create { add("string") }
        dataLayerWrapper.put("key", dataList)

        verify {
            editor.put("key", match<DataItem> { it.value == dataList }, defaultExpiry)
            editor.commit()
        }
    }

    @Test
    fun putDataObject_Adds_Single_Value_To_DataStore() {
        val dataObject = DataObject.create { put("sub_key", "value") }
        dataLayerWrapper.put("key", dataObject, Expiry.SESSION)

        verify {
            editor.put("key", match<DataItem> { it.value == dataObject }, Expiry.SESSION)
            editor.commit()
        }
    }

    @Test
    fun putDataObject_Uses_Default_Expiry_When_Omitted() {
        val dataObject = DataObject.create { put("sub_key", "value") }
        dataLayerWrapper.put("key", dataObject)

        verify {
            editor.put("key", match<DataItem> { it.value == dataObject }, defaultExpiry)
            editor.commit()
        }
    }

    @Test
    fun putDataConvertible_Adds_Single_Value_To_DataStore() {
        val convertible = TestDataObjectConvertible("string", 10)
        dataLayerWrapper.put("key", convertible, Expiry.SESSION)

        verify {
            editor.put("key", match<DataItem> {
                TestDataObjectConvertible.Converter.convert(it)!! == convertible
            }, Expiry.SESSION)
            editor.commit()
        }
    }

    @Test
    fun putDataConvertible_Uses_Default_Expiry_When_Omitted() {
        val convertible = TestDataObjectConvertible("string", 10)
        dataLayerWrapper.put("key", convertible)

        verify {
            editor.put("key", match<DataItem> {
                TestDataObjectConvertible.Converter.convert(it)!! == convertible
            }, defaultExpiry)
            editor.commit()
        }
    }

    @Test
    fun put_Does_Nothing_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()

        dataLayerWrapper.put(DataObject.create { put("key", "value") }, Expiry.SESSION)
        dataLayerWrapper.put("key", "value", Expiry.SESSION)
        dataLayerWrapper.put("key", 1, Expiry.SESSION)
        dataLayerWrapper.put("key", 1.1, Expiry.SESSION)
        dataLayerWrapper.put("key", 100L, Expiry.SESSION)
        dataLayerWrapper.put("key", true, Expiry.SESSION)
        dataLayerWrapper.put("key", DataList.create {
            add("key")
            add("value")
        }, Expiry.SESSION)
        dataLayerWrapper.put("key", DataObject.create { put("key", "value") }, Expiry.SESSION)
        dataLayerWrapper.put(DataObject.create { put("key", "value") })
        dataLayerWrapper.put("key", "value")
        dataLayerWrapper.put("key", 1)
        dataLayerWrapper.put("key", 1.1)
        dataLayerWrapper.put("key", 100L)
        dataLayerWrapper.put("key", true)
        dataLayerWrapper.put("key", DataList.create {
            add("key")
            add("value")
        })
        dataLayerWrapper.put("key", DataObject.create { put("key", "value") })

        verify {
            editor wasNot Called
        }
    }

    @Test
    fun getString_Returns_String_Value_When_In_DataLayer() {
        every { dataStore.getString("key") } returns "value"

        dataLayerWrapper.getString("key").subscribe {
            assertEquals("value", it.getOrThrow())
        }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun getString_Throws_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()

        dataLayerWrapper.getString("key").subscribe {
            it.getOrThrow()
        }
    }

    @Test
    fun getInt_Returns_Int_Value_When_In_DataLayer() {
        every { dataStore.getInt("key") } returns 10

        dataLayerWrapper.getInt("key").subscribe {
            assertEquals(10, it.getOrThrow())
        }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun getInt_Throws_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()

        dataLayerWrapper.getInt("key").subscribe {
            it.getOrThrow()
        }
    }

    @Test
    fun getLong_Returns_Long_Value_When_In_DataLayer() {
        every { dataStore.getLong("key") } returns 100L

        dataLayerWrapper.getLong("key").subscribe {
            assertEquals(100L, it.getOrThrow())
        }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun getLong_Throws_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()

        dataLayerWrapper.getLong("key").subscribe {
            it.getOrThrow()
        }
    }

    @Test
    fun getDouble_Returns_Double_Value_When_In_DataLayer() {
        every { dataStore.getDouble("key") } returns 1.1

        dataLayerWrapper.getDouble("key").subscribe {
            assertEquals(1.1, it.getOrThrow())
        }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun getDouble_Throws_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()

        dataLayerWrapper.getDouble("key").subscribe {
            it.getOrThrow()
        }
    }

    @Test
    fun getBoolean_Returns_Boolean_Value_When_In_DataLayer() {
        every { dataStore.getBoolean("key") } returns false

        dataLayerWrapper.getBoolean("key").subscribe {
            assertEquals(false, it.getOrThrow())
        }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun getBoolean_Throws_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()

        dataLayerWrapper.getBoolean("key").subscribe {
            it.getOrThrow()
        }
    }

    @Test
    fun getDataList_Returns_DataList_Value_When_In_DataLayer() {
        val dataList = DataList.create { add("string") }
        every { dataStore.getDataList("key") } returns dataList

        dataLayerWrapper.getDataList("key").subscribe {
            assertEquals(dataList, it.getOrThrow())
        }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun getDataList_Throws_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()

        dataLayerWrapper.getDataList("key").subscribe {
            it.getOrThrow()
        }
    }

    @Test
    fun getDataObject_Returns_DataObject_Value_When_In_DataLayer() {
        val dataObject = DataObject.create { put("sub_key", "value") }
        every { dataStore.getDataObject("key") } returns dataObject

        dataLayerWrapper.getDataObject("key").subscribe {
            assertEquals(dataObject, it.getOrThrow())
        }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun getDataObject_Throws_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()

        dataLayerWrapper.getDataObject("key").subscribe {
            it.getOrThrow()
        }
    }

    @Test
    fun getConvertible_Returns_Converted_Value_When_In_DataLayer() {
        val converter = DataItemConverter {
            it.value.toString()
        }
        every { dataStore.get("key", converter) } answers { converter.convert(100.asDataItem()) }

        dataLayerWrapper.get("key", converter).subscribe {
            assertEquals("100", it.getOrThrow())
        }
    }

    @Test
    fun getConvertible_Returns_Null_When_Not_Present_In_DataLayer() {
        val converter = mockk<DataItemConverter<String>>()
        every { dataStore.get("key", converter) } answers { null }

        dataLayerWrapper.get("key", converter).subscribe {
            assertNull(it.getOrThrow())
        }
        verify { converter wasNot Called }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun getConvertible_Throws_When_ModuleManager_Shutdown() {
        val converter = mockk<DataItemConverter<String>>()
        moduleManager.shutdown()

        dataLayerWrapper.get("key", converter).subscribe {
            it.getOrThrow()
        }
        verify { converter wasNot Called }
    }

    @Test
    fun getAll_Returns_All_Values_From_DataLayer() {
        val dataObject = DataObject.create { put("sub_key", "value") }
        every { dataStore.getAll() } returns dataObject

        dataLayerWrapper.getAll().subscribe {
            assertEquals(dataObject, it.getOrThrow())
        }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun getAll_Throws_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()

        dataLayerWrapper.getAll().subscribe {
            it.getOrThrow()
        }
    }

    @Test
    fun remove_Removes_Key_From_DataLayer() {
        dataLayerWrapper.remove("key")

        verify {
            editor.remove(listOf("key"))
            editor.commit()
        }
    }

    @Test
    fun remove_Many_Removes_All_Keys_From_DataLayer() {
        dataLayerWrapper.remove(listOf("key", "key2"))

        verify {
            editor.remove(listOf("key", "key2"))
            editor.commit()
        }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun remove_Throws_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()

        dataLayerWrapper.remove("key").subscribe {
            it.getOrThrow()
        }

        verify {
            editor wasNot Called
        }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun remove_Many_Throws_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()

        dataLayerWrapper.remove(listOf("key", "key2")).subscribe {
            it.getOrThrow()
        }

        verify {
            editor wasNot Called
        }
    }

    @Test
    fun clear_Clears_All_Keys_From_DataLayer() {
        dataLayerWrapper.clear()

        verify {
            editor.clear()
            editor.commit()
        }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun clear_Throws_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()

        dataLayerWrapper.clear().subscribe {
            it.getOrThrow()
        }

        verify {
            editor wasNot Called
        }
    }

    @Test
    fun getOnDataUpdated_Emits_Updates_Values() {
        val observer = mockk<Observer<DataObject>>(relaxed = true)
        val data = DataObject.create { put("key", "value") }

        dataLayerWrapper.onDataUpdated.subscribe(observer)
        onDataUpdated.onNext(data)

        verify { observer.onNext(data) }
    }

    @Test
    fun getOnDataUpdated_Emits_Nothing_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()
        val observer = mockk<Observer<DataObject>>(relaxed = true)
        val data = DataObject.create { put("key", "value") }

        dataLayerWrapper.onDataUpdated.subscribe(observer)
        onDataUpdated.onNext(data)

        verify { observer wasNot Called }
    }

    @Test
    fun getOnDataRemoved_Emits_Updates_Values() {
        val observer = mockk<Observer<List<String>>>(relaxed = true)
        val keys = listOf("key", "key2")

        dataLayerWrapper.onDataRemoved.subscribe(observer)
        onDataRemoved.onNext(keys)

        verify { observer.onNext(keys) }
    }

    @Test
    fun getOnDataRemoved_Emits_Nothing_When_ModuleManager_Shutdown() {
        moduleManager.shutdown()
        val observer = mockk<Observer<List<String>>>(relaxed = true)
        val keys = listOf("key", "key2")

        dataLayerWrapper.onDataRemoved.subscribe(observer)
        onDataRemoved.onNext(keys)

        verify { observer wasNot Called }
    }

    /**
     * Mock [ModuleFactory] to return a [DataLayerModule] with an injected mock [DataStore]
     */
    private class MockDataLayerFactory(private val module: DataLayerModule) : ModuleFactory {
        override val moduleType: String
            get() = Modules.Types.DATA_LAYER

        override fun create(moduleId: String, context: TealiumContext, configuration: DataObject): Module? {
            return module
        }
    }
}
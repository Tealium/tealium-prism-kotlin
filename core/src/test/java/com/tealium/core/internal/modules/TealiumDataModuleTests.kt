package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.misc.Environment
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext
import com.tealium.tests.common.TestModule
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TealiumDataModuleTests {

    private lateinit var config: TealiumConfig
    private lateinit var mockModuleManager: ModuleManager
    private lateinit var mockContext: TealiumContext
    private lateinit var tealiumDataModule: TealiumDataModule

    private val dispatchContext = mockk<DispatchContext>(relaxed = true)
    private val visitorId: StateSubject<String> = Observables.stateSubject("visitor-123")
    private val modules: StateSubject<List<Module>> = Observables.stateSubject(listOf())

    @Before
    fun setUp() {
        mockModuleManager = mockk()
        mockContext = mockk(relaxed = true)

        config = TealiumConfig(mockk(), "test-account", "test-profile", Environment.DEV, listOf(), "test-datasource")

        every { mockContext.config } returns config
        every { mockContext.visitorId } returns visitorId
        every { mockContext.moduleManager } returns mockModuleManager
        every { mockModuleManager.modules } returns modules
    }

    @Test
    fun collect_Includes_Account_Name_From_Config() {
        tealiumDataModule = TealiumDataModule(config, visitorId, mockModuleManager)

        val result = tealiumDataModule.collect(dispatchContext)

        assertEquals(config.accountName, result.getString(Dispatch.Keys.TEALIUM_ACCOUNT))
    }

    @Test
    fun collect_Includes_Profile_Name_From_Config() {
        tealiumDataModule = TealiumDataModule(config, visitorId, mockModuleManager)

        val result = tealiumDataModule.collect(dispatchContext)

        assertEquals(config.profileName, result.getString(Dispatch.Keys.TEALIUM_PROFILE))
    }

    @Test
    fun collect_Includes_Environment_From_Config() {
        tealiumDataModule = TealiumDataModule(config, visitorId, mockModuleManager)

        val result = tealiumDataModule.collect(dispatchContext)

        assertEquals(config.environment.environment, result.getString(Dispatch.Keys.TEALIUM_ENVIRONMENT))
    }

    @Test
    fun collect_Includes_DataSource_From_Config_When_Configured() {
        tealiumDataModule = TealiumDataModule(config, visitorId, mockModuleManager)

        val result = tealiumDataModule.collect(dispatchContext)

        assertEquals(config.datasource, result.getString(Dispatch.Keys.TEALIUM_DATASOURCE_ID))
    }

    @Test
    fun collect_Does_Not_Include_DataSource_When_Not_Configured() {
        val config = TealiumConfig(mockk(), "", "", Environment.DEV, listOf(), datasource = null)
        tealiumDataModule = TealiumDataModule(config, visitorId, mockModuleManager)

        val result = tealiumDataModule.collect(dispatchContext)

        assertNull(result.getAll()[Dispatch.Keys.TEALIUM_DATASOURCE_ID])
    }

    @Test
    fun collect_Includes_Library_Build_Name_And_Version() {
        tealiumDataModule = TealiumDataModule(config, visitorId, mockModuleManager)

        val result = tealiumDataModule.collect(dispatchContext)

        assertEquals(BuildConfig.TEALIUM_LIBRARY_NAME, result.getString(Dispatch.Keys.TEALIUM_LIBRARY_NAME))
        assertEquals(BuildConfig.TEALIUM_LIBRARY_VERSION, result.getString(Dispatch.Keys.TEALIUM_LIBRARY_VERSION))
    }

    @Test
    fun collect_Includes_Current_Visitor_Id() {
        tealiumDataModule = TealiumDataModule(config, visitorId, mockModuleManager)

        visitorId.onNext("visitor1")
        val result1 = tealiumDataModule.collect(dispatchContext)
        visitorId.onNext("visitor2")
        val result2 = tealiumDataModule.collect(dispatchContext)

        assertEquals("visitor1", result1.getString(Dispatch.Keys.TEALIUM_VISITOR_ID))
        assertEquals("visitor2", result2.getString(Dispatch.Keys.TEALIUM_VISITOR_ID))
    }

    @Test
    fun collect_Includes_New_Random_Value_On_Each_Collection() {
        tealiumDataModule = TealiumDataModule(config, visitorId, mockModuleManager)

        val result1 = tealiumDataModule.collect(dispatchContext).getString(Dispatch.Keys.TEALIUM_RANDOM)!!
        val result2 = tealiumDataModule.collect(dispatchContext).getString(Dispatch.Keys.TEALIUM_RANDOM)!!

        assertNotEquals(result1, result2)
        listOf(result1, result2).forEach { randomValue ->
            assertNotNull(randomValue)
            assertEquals(16, randomValue.length)
            assertTrue(randomValue.all { it.isDigit() })
        }
    }

    @Test
    fun collect_Includes_Module_Information_When_Modules_Present() {
        val mockModule1 = TestModule("module1", "1.0.0")
        val mockModule2 = TestModule("module2", "2.0.0")
        modules.onNext(listOf(mockModule1, mockModule2))
        tealiumDataModule = TealiumDataModule(config, visitorId, mockModuleManager)

        val result = tealiumDataModule.collect(dispatchContext)

        val moduleIds = result.getDataList(Dispatch.Keys.ENABLED_MODULES)!!
        val moduleVersions = result.getDataList(Dispatch.Keys.ENABLED_MODULES_VERSIONS)!!

        assertEquals(2, moduleIds.size)
        assertEquals(2, moduleVersions.size)
        assertEquals("module1", moduleIds.getString(0))
        assertEquals("module2", moduleIds.getString(1))
        assertEquals("1.0.0", moduleVersions.getString(0))
        assertEquals("2.0.0", moduleVersions.getString(1))
    }

    @Test
    fun collect_Includes_Empty_Module_Lists_When_No_Modules_Present() {
        tealiumDataModule = TealiumDataModule(config, visitorId, mockModuleManager)

        val result = tealiumDataModule.collect(dispatchContext)

        val moduleIds = result.getDataList(Dispatch.Keys.ENABLED_MODULES)!!
        val moduleVersions = result.getDataList(Dispatch.Keys.ENABLED_MODULES_VERSIONS)!!

        assertEquals(0, moduleIds.size)
        assertEquals(0, moduleVersions.size)
    }

    @Test
    fun collect_Returns_New_DataObject_Instance_When_Called_Multiple_Times() {
        tealiumDataModule = TealiumDataModule(config, visitorId, mockModuleManager)

        val result1 = tealiumDataModule.collect(dispatchContext)
        val result2 = tealiumDataModule.collect(dispatchContext)

        assertNotSame(result1, result2)
    }

    @Test
    fun factory_Id_Matches_Module_Id() {
        val tealiumDataModule = TealiumDataModule.Factory.create(mockContext, mockk())!!

        assertEquals(TealiumDataModule.Factory.id, tealiumDataModule.id)
    }

    @Test
    fun factory_Creates_New_Instances() {
        val instance1 = TealiumDataModule.Factory.create(mockContext, mockk())
        val instance2 = TealiumDataModule.Factory.create(mockContext, mockk())

        assertNotSame(instance1, instance2)
    }
}

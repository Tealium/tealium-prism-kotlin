package com.tealium.core.internal.modules

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.internal.modules.datalayer.DataLayerModule
import com.tealium.core.internal.settings.ModuleSettings
import com.tealium.core.internal.settings.SdkSettings
import com.tealium.tests.common.SynchronousScheduler
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestCollector
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.TestModule
import com.tealium.tests.common.TestModuleFactory
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModuleManagerImplTests {

    private val testDispatcher = TestDispatcher.mock("dispatcher")
    private val testCollector = TestCollector.mock("collector")
    private val testModule = TestModule.mock("module")
    private val moduleWithObservable =
        ModuleWithObservable(Observables.publishSubject(), "module_with_observable")
    private val modules = listOf(
        testCollector,
        testDispatcher,
        testModule,
        moduleWithObservable
    )
    private val defaultFactories = modules.map { TestModuleFactory(it) }

    private lateinit var moduleManager: InternalModuleManager
    private lateinit var modulesSubject: StateSubject<List<Module>>
    private lateinit var context: TealiumContext

    @Before
    fun setUp() {
        modulesSubject = Observables.stateSubject(listOf())

        moduleManager = ModuleManagerImpl(
            SynchronousScheduler(), modulesSubject
        )
        defaultFactories.forEach(moduleManager::addModuleFactory)

        context = mockk<TealiumContext>()
        every { context.logger } returns SystemLogger
        moduleManager.updateModuleSettings(context, SdkSettings())
    }

    @Test
    fun getModuleOfType_ReturnsSame_MatchingGivenSpecificClass() {
        val collector = moduleManager.getModuleOfType(TestCollector::class.java)
        val dispatcher = moduleManager.getModuleOfType(TestDispatcher::class.java)
        val module = moduleManager.getModuleOfType(TestModule::class.java)

        assertSame(testCollector, collector)
        assertSame(testDispatcher, dispatcher)
        assertSame(testModule, module)
    }

    @Test
    fun getModuleOfType_ReturnsSame_MatchingGivenInterface() {
        val collector = moduleManager.getModuleOfType(Collector::class.java)
        val dispatcher = moduleManager.getModuleOfType(Dispatcher::class.java)

        assertSame(testCollector, collector)
        assertSame(testDispatcher, dispatcher)
    }

    @Test
    fun getModuleOfType_ReturnsFirst_When_MatchingMultiple() {
        val module = moduleManager.getModuleOfType(Module::class.java)

        assertSame(testCollector, module)
    }

    @Test
    fun getModuleOfType_ReturnsNull_When_MatchingNone() {
        val module = moduleManager.getModuleOfType(DataLayerModule::class.java)

        assertNull(module)
    }

    @Test
    fun getModuleOfType_ReturnsSame_MatchingGivenSpecificClass_OnGivenScheduler() {
        val onCollector = mockk<TealiumCallback<TestCollector?>>(relaxed = true)
        val onDispatcher = mockk<TealiumCallback<TestDispatcher?>>(relaxed = true)
        val onModule = mockk<TealiumCallback<TestModule?>>(relaxed = true)
        moduleManager.getModuleOfType(TestCollector::class.java, onCollector)
        moduleManager.getModuleOfType(TestDispatcher::class.java, onDispatcher)
        moduleManager.getModuleOfType(TestModule::class.java, onModule)

        verify(timeout = 1000) {
            onCollector.onComplete(testCollector)
            onDispatcher.onComplete(testDispatcher)
            onModule.onComplete(testModule)
        }
    }

    @Test
    fun getModuleOfType_ReturnsSame_MatchingGivenInterface_OnGivenScheduler() {
        val onCollector = mockk<TealiumCallback<Collector?>>(relaxed = true)
        val onDispatcher = mockk<TealiumCallback<Dispatcher?>>(relaxed = true)
        moduleManager.getModuleOfType(Collector::class.java, onCollector)
        moduleManager.getModuleOfType(Dispatcher::class.java, onDispatcher)

        verify(timeout = 1000) {
            onCollector.onComplete(testCollector)
            onDispatcher.onComplete(testDispatcher)
        }
    }

    @Test
    fun getModuleOfType_ReturnsFirst_When_MatchingMultiple_OnGivenScheduler() {
        val onModule = mockk<TealiumCallback<Module?>>(relaxed = true)
        moduleManager.getModuleOfType(Module::class.java, onModule)

        verify(timeout = 1000) {
            onModule.onComplete(testCollector)
        }
    }

    @Test
    fun getModuleOfType_ReturnsNull_When_MatchingNone_OnGivenScheduler() {
        val onModule = mockk<TealiumCallback<DataLayerModule?>>(relaxed = true)
        moduleManager.getModuleOfType(DataLayerModule::class.java, onModule)

        verify(timeout = 1000) {
            onModule.onComplete(null)
        }
    }

    @Test
    fun getModulesOfType_ReturnsAll_When_MatchingMultiple() {
        val modules = moduleManager.getModulesOfType(Module::class.java)

        assertSame(testCollector, modules[0])
        assertSame(testDispatcher, modules[1])
        assertSame(testModule, modules[2])
    }

    @Test
    fun getModulesOfType_ReturnsEmpty_When_MatchingNone() {
        val modules = moduleManager.getModulesOfType(DataLayerModule::class.java)
        assertTrue(modules.isEmpty())
    }

    @Test
    fun observeModule_Emits_Module_When_The_Module_Is_Available() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)

        moduleManager.observeModule(TestDispatcher::class.java)
            .subscribe(observer)

        verify {
            observer.onNext(testDispatcher)
        }
    }

    @Test
    fun observeModule_Emits_Null_When_The_Module_Is_Unavailable() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(testDispatcher.id))

        moduleManager.observeModule(TestDispatcher::class.java)
            .subscribe(observer)

        verify {
            observer.onNext(null)
        }
    }

    @Test
    fun observeModule_Emits_Updated_Module_When_Enabled_And_Disabled() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(testDispatcher.id))

        moduleManager.observeModule(TestDispatcher::class.java).subscribe(observer)
        moduleManager.updateModuleSettings(context, enableModuleSettings(testDispatcher.id))
        moduleManager.updateModuleSettings(context, disableModuleSettings(testDispatcher.id))
        moduleManager.updateModuleSettings(context, enableModuleSettings(testDispatcher.id))

        verifyOrder {
            observer.onNext(null)
            observer.onNext(testDispatcher)
            observer.onNext(null)
            observer.onNext(testDispatcher)
        }
    }

    @Test
    fun observeModule_Emits_Transformed_Module_When_The_Module_Is_Available() {
        val observer = mockk<Observer<Int>>(relaxed = true)

        moduleManager.observeModule(ModuleWithObservable::class.java) { it.subject }
            .subscribe(observer)
        moduleWithObservable.subject.onNext(1)
        moduleWithObservable.subject.onNext(2)

        verify {
            observer.onNext(1)
            observer.onNext(2)
        }
    }

    @Test
    fun observeModule_With_Transform_Emits_Nothing_When_The_Module_Is_Unavailable() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(moduleWithObservable.id))

        moduleManager.observeModule(ModuleWithObservable::class.java) { it.subject }
            .subscribe(observer)
        moduleWithObservable.subject.onNext(1)

        verify(inverse = true) {
            observer.onNext(any())
        }
    }

    @Test
    fun observeModule_Emits_Updated_Transformed_Module_When_Enabled_And_Disabled() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(moduleWithObservable.id))

        moduleManager.observeModule(ModuleWithObservable::class.java) { it.subject }
            .subscribe(observer)
        moduleWithObservable.subject.onNext(1)
        moduleManager.updateModuleSettings(context, enableModuleSettings(moduleWithObservable.id))
        moduleWithObservable.subject.onNext(2)
        moduleManager.updateModuleSettings(context, disableModuleSettings(moduleWithObservable.id))
        moduleWithObservable.subject.onNext(3)
        moduleManager.updateModuleSettings(context, enableModuleSettings(moduleWithObservable.id))
        moduleWithObservable.subject.onNext(4)

        verifyOrder {
            observer.onNext(2)
            observer.onNext(4)
        }
        verify(inverse = true) {
            observer.onNext(1)
            observer.onNext(3)
        }
    }

    @Test
    fun updateModuleSettings_Updates_Existing_Modules() {
        moduleManager.updateModuleSettings(context, SdkSettings(modules = mapOf()))

        verify {
            testCollector.updateConfiguration(any())
            testDispatcher.updateConfiguration(any())
            testModule.updateConfiguration(any())
        }
    }

    @Test
    fun updateModuleSettings_Removes_Modules_That_Return_Null_From_UpdateConfiguration() {
        every { testModule.updateConfiguration(any()) } returns null
        moduleManager.updateModuleSettings(context, SdkSettings(modules = mapOf()))

        assertFalse(modulesSubject.value.contains(testModule))

        assertTrue(modulesSubject.value.contains(testCollector))
        assertTrue(modulesSubject.value.contains(testDispatcher))
    }

    @Test
    fun updateModuleSettings_Calls_OnShutdown_When_UpdateConfiguration_ReturnsNull() {
        every { testModule.updateConfiguration(any()) } returns null
        moduleManager.updateModuleSettings(context, SdkSettings(modules = mapOf()))

        verify { testModule.onShutdown() }
    }

    @Test
    fun updateModuleSettings_Calls_OnShutdown_When_Module_Settings_Disabled() {
        moduleManager.updateModuleSettings(context, disableModuleSettings(testModule.id))

        verify { testModule.onShutdown() }
    }

    @Test
    fun updateModuleSettings_ProvidesConfiguration_ToSpecificModules() {
        val testCollectorSettings = ModuleSettings(configuration = DataObject.create {
            put("collector_setting", "10")
        })
        val testDispatcherSettings = ModuleSettings(configuration = DataObject.create {
            put("dispatcher_setting", "10")
        })
        val testModuleSettings = ModuleSettings(configuration = DataObject.create {
            put("module_setting", "10")
        })
        moduleManager.updateModuleSettings(
            context, SdkSettings(
                modules = mapOf(
                    testCollector.id to testCollectorSettings,
                    testDispatcher.id to testDispatcherSettings,
                    testModule.id to testModuleSettings
                )
            )
        )

        verify {
            testCollector.updateConfiguration(match { it.getString("collector_setting") == "10" })
            testDispatcher.updateConfiguration(match { it.getString("dispatcher_setting") == "10" })
            testModule.updateConfiguration(match { it.getString("module_setting") == "10" })
        }
    }

    @Test
    fun updateModuleSettings_Does_Not_Create_Module_When_Settings_Disabled() {
        val creator = mockk<(TealiumContext, DataObject) -> Module>()

        moduleManager.addModuleFactory(TestModuleFactory("disabled_module", creator = creator))
        moduleManager.updateModuleSettings(context, disableModuleSettings("disabled_module"))

        verify {
            creator wasNot Called
        }
    }

    @Test
    fun updateModuleSettings_Does_Create_Module_When_Settings_Disabled_But_Module_Cannot_Be_Disabled() {
        val creator = mockk<(TealiumContext, DataObject) -> Module>()
        every { creator.invoke(any(), any()) } returns mockk(relaxed = true)

        moduleManager.addModuleFactory(
            TestModuleFactory(
                "disabled_module",
                canBeDisabled = false,
                creator = creator
            )
        )
        moduleManager.updateModuleSettings(context, disableModuleSettings("disabled_module"))

        verify {
            creator(any(), any())
        }
    }

    @Test
    fun updateModuleSettings_Updates_Module_Configuration_When_Settings_Disabled_But_Module_Cannot_Be_Disabled() {
        val creator = mockk<(TealiumContext, DataObject) -> Module>(relaxed = true)
        val nonDisableableModule = TestModule.mock("disabled_module")
        every { creator.invoke(any(), any()) } returns nonDisableableModule

        moduleManager.addModuleFactory(
            TestModuleFactory(
                "disabled_module",
                canBeDisabled = false,
                creator = creator
            )
        )
        val disabledSettings = disableModuleSettings("disabled_module")

        // creates
        moduleManager.updateModuleSettings(context, disabledSettings)
        // updates
        moduleManager.updateModuleSettings(context, disabledSettings)

        verify {
            nonDisableableModule.updateConfiguration(any())
        }
    }

    @Test
    fun shutdown_Emits_Empty_Modules() {
        val observer = mockk<Observer<List<Module>>>(relaxed = true)

        moduleManager.modules.subscribe(observer)
        moduleManager.shutdown()

        verify { observer.onNext(emptyList()) }
    }

    @Test
    fun shutdown_Shuts_Down_All_Modules() {
        moduleManager.shutdown()

        verify {
            testModule.onShutdown()
            testCollector.onShutdown()
            testDispatcher.onShutdown()
        }
    }

    @Test
    fun addModuleFactory_Adds_Factory_When_ModuleFactory_With_Same_Id_Not_Present() {
        val newModule = TestModule("unseen_id")
        val newModuleFactory = TestModuleFactory(newModule)

        moduleManager.addModuleFactory(newModuleFactory)
        moduleManager.updateModuleSettings(context, SdkSettings())

        assertTrue(moduleManager.modules.value.contains(newModule))
    }

    @Test
    fun addModuleFactory_Does_Not_Add_Factory_When_ModuleFactory_With_Same_Id_Is_Present() {
        val newModule = TestModule(testDispatcher.id)
        val newModuleFactory = TestModuleFactory(newModule)

        moduleManager.addModuleFactory(newModuleFactory)
        moduleManager.updateModuleSettings(context, SdkSettings())

        assertFalse(moduleManager.modules.value.contains(newModule))
    }

    @Test
    fun addModuleFactory_Returns_True_When_ModuleFactory_With_Same_Id_Not_Present() {
        val newModuleFactory = TestModuleFactory(TestModule("unseen_id"))

        assertTrue(moduleManager.addModuleFactory(newModuleFactory))
    }

    @Test
    fun addModuleFactory_Returns_False_When_ModuleFactory_With_Same_Id_Is_Present() {
        val newModuleFactory = TestModuleFactory(TestModule(testDispatcher.id))

        assertFalse(moduleManager.addModuleFactory(newModuleFactory))
    }

    @Test
    fun getModulesInfo_Returns_Information_For_All_Enabled_Modules() {
        val info = moduleManager.modulesInfo

        for (mod in modules) {
            assertNotNull(info.find {
                it.id == mod.id
                        && it.version == mod.version
            })
        }
    }

    @Test
    fun getModulesInfo_Returns_Information_For_Enabled_Modules_In_Order() {
        val info = moduleManager.modulesInfo

        modules.forEachIndexed { idx, module ->
            assertEquals(module.id, info[idx].id)
            assertEquals(module.version, info[idx].version)
        }
    }

    @Test
    fun getModulesInfo_Does_Not_Return_Information_For_Disabled_Modules() {
        moduleManager.updateModuleSettings(context, disableModuleSettings(testDispatcher.id))

        val info = moduleManager.modulesInfo

        assertNull(info.find { it.id == testDispatcher.id })
    }
}

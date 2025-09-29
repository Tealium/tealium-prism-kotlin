package com.tealium.core.internal.modules

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
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
    private val defaultFactories = modules.map { TestModuleFactory.forModule(it) }

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
    fun getModuleOfType_Returns_Same_Matching_Given_Specific_Class() {
        val collector = moduleManager.getModuleOfType(TestCollector::class.java)
        val dispatcher = moduleManager.getModuleOfType(TestDispatcher::class.java)
        val module = moduleManager.getModuleOfType(TestModule::class.java)

        assertSame(testCollector, collector)
        assertSame(testDispatcher, dispatcher)
        assertSame(testModule, module)
    }

    @Test
    fun getModuleOfType_Returns_Same_Matching_Given_Interface() {
        val collector = moduleManager.getModuleOfType(Collector::class.java)
        val dispatcher = moduleManager.getModuleOfType(Dispatcher::class.java)

        assertSame(testCollector, collector)
        assertSame(testDispatcher, dispatcher)
    }

    @Test
    fun getModuleOfType_Returns_First_When_Matching_Multiple() {
        val module = moduleManager.getModuleOfType(Module::class.java)

        assertSame(testCollector, module)
    }

    @Test
    fun getModuleOfType_Returns_Null_When_Matching_None() {
        val module = moduleManager.getModuleOfType(DataLayerModule::class.java)

        assertNull(module)
    }

    @Test
    fun getModuleOfType_Returns_Same_Matching_Given_Specific_Class_On_Given_Scheduler() {
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
    fun getModuleOfType_Returns_Same_Matching_Given_Interface_On_Given_Scheduler() {
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
    fun getModuleOfType_Returns_First_When_Matching_Multiple_On_Given_Scheduler() {
        val onModule = mockk<TealiumCallback<Module?>>(relaxed = true)
        moduleManager.getModuleOfType(Module::class.java, onModule)

        verify(timeout = 1000) {
            onModule.onComplete(testCollector)
        }
    }

    @Test
    fun getModuleOfType_Returns_Null_When_Matching_None_On_Given_Scheduler() {
        val onModule = mockk<TealiumCallback<DataLayerModule?>>(relaxed = true)
        moduleManager.getModuleOfType(DataLayerModule::class.java, onModule)

        verify(timeout = 1000) {
            onModule.onComplete(null)
        }
    }

    @Test
    fun getModulesOfType_Returns_All_When_Matching_Multiple() {
        val modules = moduleManager.getModulesOfType(Module::class.java)

        assertSame(testCollector, modules[0])
        assertSame(testDispatcher, modules[1])
        assertSame(testModule, modules[2])
    }

    @Test
    fun getModulesOfType_Returns_Empty_When_Matching_None() {
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
    fun updateModuleSettings_Provides_Configuration_To_Specific_Modules() {
        val testCollectorSettings = ModuleSettings(testCollector.id, configuration = DataObject.create {
            put("collector_setting", "10")
        })
        val testDispatcherSettings = ModuleSettings(testDispatcher.id, configuration = DataObject.create {
            put("dispatcher_setting", "10")
        })
        val testModuleSettings = ModuleSettings(testModule.id, configuration = DataObject.create {
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
        val creator = mockk<(String, TealiumContext, DataObject) -> Module>()

        moduleManager.addModuleFactory(TestModuleFactory("disabled_module", creator = creator))
        moduleManager.updateModuleSettings(context, disableModuleSettings("disabled_module"))

        verify {
            creator wasNot Called
        }
    }

    @Test
    fun updateModuleSettings_Does_Create_Module_When_Settings_Disabled_But_Module_Cannot_Be_Disabled() {
        val creator = mockk<(String, TealiumContext, DataObject) -> Module>()
        every { creator.invoke(any(), any(), any()) } returns mockk(relaxed = true)

        moduleManager.addModuleFactory(
            TestModuleFactory(
                "disabled_module",
                canBeDisabled = false,
                creator = creator
            )
        )
        moduleManager.updateModuleSettings(context, disableModuleSettings("disabled_module"))

        verify {
            creator(any(), any(), any())
        }
    }

    @Test
    fun updateModuleSettings_Updates_Module_Configuration_When_Settings_Disabled_But_Module_Cannot_Be_Disabled() {
        val creator = mockk<(String, TealiumContext, DataObject) -> Module>(relaxed = true)
        val nonDisableableModule = TestModule.mock("disabled_module")
        every { creator.invoke(any(), any(), any()) } returns nonDisableableModule

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
    fun updateModuleSettings_Creates_Single_Instance_Module_With_Fixed_Id() {
        val customId = "custom-id"
        moduleManager.updateModuleSettings(context, SdkSettings(modules = mapOf(
            customId to ModuleSettings(moduleType = testModule.id, moduleId = customId)
        )))

        assertNotNull(moduleManager.modules.value.find { it.id == testModule.id })
    }

    @Test
    fun updateModuleSettings_Creates_Single_Instance_Module_Only_Once() {
        moduleManager.updateModuleSettings(context, SdkSettings(modules = mapOf(
            "1" to ModuleSettings(moduleType = testModule.id, "1"),
            "2" to ModuleSettings(moduleType = testModule.id, "2"),
        )))

        assertEquals(1, moduleManager.modules.value.count { it.id == testModule.id })
    }

    @Test
    fun updateModuleSettings_Creates_Multiple_Instance_Modules_Using_ModuleId_From_Settings() {
        val multiFactory = multiInstanceFactory("custom_type")
        moduleManager.addModuleFactory(multiFactory)

        moduleManager.updateModuleSettings(context, SdkSettings(modules = mapOf(
            "1" to ModuleSettings(moduleType = multiFactory.moduleType, "1"),
            "2" to ModuleSettings(moduleType = multiFactory.moduleType, "2"),
        )))

        assertNotNull(moduleManager.modules.value.find { it.id == "1" })
        assertNotNull(moduleManager.modules.value.find { it.id == "2" })
    }

    @Test
    fun updateModuleSettings_Creates_Multiple_Instance_Modules_Once_When_ModuleId_Clashes() {
        val multiFactory = multiInstanceFactory("custom_type")
        moduleManager.addModuleFactory(multiFactory)

        moduleManager.updateModuleSettings(context, SdkSettings(modules = mapOf(
            "1" to ModuleSettings(moduleType = multiFactory.moduleType, "1"),
            "2" to ModuleSettings(moduleType = multiFactory.moduleType, "1"),
        )))

        assertEquals(1, moduleManager.modules.value.count { it.id == "1"})
        assertEquals(0, moduleManager.modules.value.count { it.id == "2"})
    }

    @Test
    fun updateModuleSettings_Creates_Multiple_Instance_Modules_Once_When_ModuleId_Is_Undefined() {
        val multiFactory = multiInstanceFactory("custom_type")
        moduleManager.addModuleFactory(multiFactory)

        moduleManager.updateModuleSettings(context, SdkSettings(modules = mapOf(
            "1" to ModuleSettings(moduleType = multiFactory.moduleType),
            "2" to ModuleSettings(moduleType = multiFactory.moduleType),
        )))

        assertEquals(1, moduleManager.modules.value.count { it.id == multiFactory.moduleType})
        assertEquals(0, moduleManager.modules.value.count { it.id == "1"})
        assertEquals(0, moduleManager.modules.value.count { it.id == "2"})
    }

    @Test
    fun updateModuleSettings_Creates_Multiple_Instance_Modules_Each_Time_When_ModuleId_Is_Undefined_On_One_Settings() {
        val multiFactory = multiInstanceFactory("custom_type")
        moduleManager.addModuleFactory(multiFactory)

        moduleManager.updateModuleSettings(context, SdkSettings(modules = mapOf(
            "1" to ModuleSettings(moduleType = multiFactory.moduleType, "1"),
            "2" to ModuleSettings(moduleType = multiFactory.moduleType), // uses default moduleType for id
        )))

        assertEquals(1, moduleManager.modules.value.count { it.id == multiFactory.moduleType})
        assertEquals(1, moduleManager.modules.value.count { it.id == "1"})
        assertEquals(0, moduleManager.modules.value.count { it.id == "2"})
    }

    @Test
    fun updateModuleSettings_Creates_Modules_In_Order_Defined_By_Settings() {
        val multiFactory = multiInstanceFactory("custom_type")
        moduleManager.addModuleFactory(multiFactory)

        moduleManager.updateModuleSettings(context, SdkSettings(modules = mapOf(
            "3" to ModuleSettings(moduleType = multiFactory.moduleType, "3", order = 3),
            "2" to ModuleSettings(moduleType = multiFactory.moduleType, "2", order = 2),
            "1" to ModuleSettings(moduleType = multiFactory.moduleType, "1", order = 1)
        )))

        assertEquals("1", moduleManager.modules.value[0].id)
        assertEquals("2", moduleManager.modules.value[1].id)
        assertEquals("3", moduleManager.modules.value[2].id)
    }

    @Test
    fun updateModuleSettings_Creates_Modules_Putting_Modules_With_No_Order_Last() {
        val multiFactory = multiInstanceFactory("custom_type")
        moduleManager.addModuleFactory(multiFactory)

        moduleManager.updateModuleSettings(context, SdkSettings(modules = mapOf(
            "2" to ModuleSettings(moduleType = multiFactory.moduleType, "2"),
            "1" to ModuleSettings(moduleType = multiFactory.moduleType, "1", order = 1)
        )))

        // todo - revisit if we don't add using default settings
        // modules from settings first, then additional defaults from Factories that had no settings
        (listOf("1", "2") + modules.map(Module::id)).forEachIndexed { idx, id ->
            assertEquals(id, moduleManager.modules.value[idx].id)
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
        val newModuleFactory = TestModuleFactory.forModule(newModule)

        moduleManager.addModuleFactory(newModuleFactory)
        moduleManager.updateModuleSettings(context, SdkSettings())

        assertTrue(moduleManager.modules.value.contains(newModule))
    }

    @Test
    fun addModuleFactory_Does_Not_Add_Factory_When_ModuleFactory_With_Same_Id_Is_Present() {
        val newModule = TestModule(testDispatcher.id)
        val newModuleFactory = TestModuleFactory.forModule(newModule)

        moduleManager.addModuleFactory(newModuleFactory)
        moduleManager.updateModuleSettings(context, SdkSettings())

        assertFalse(moduleManager.modules.value.contains(newModule))
    }

    @Test
    fun addModuleFactory_Returns_True_When_ModuleFactory_With_Same_Id_Not_Present() {
        val newModuleFactory = TestModuleFactory.forModule(TestModule("unseen_id"))

        assertTrue(moduleManager.addModuleFactory(newModuleFactory))
    }

    @Test
    fun addModuleFactory_Returns_False_When_ModuleFactory_With_Same_Id_Is_Present() {
        val newModuleFactory = TestModuleFactory.forModule(TestModule(testDispatcher.id))

        assertFalse(moduleManager.addModuleFactory(newModuleFactory))
    }

    private fun multiInstanceFactory(moduleType: String, creator: (String, TealiumContext, DataObject) -> Module? = { moduleId, _, _ ->
        TestModule(moduleId)
    }): ModuleFactory {
        return TestModuleFactory(
            moduleType = moduleType,
            allowsMultipleInstances = true,
            creator = creator
        )
    }
}

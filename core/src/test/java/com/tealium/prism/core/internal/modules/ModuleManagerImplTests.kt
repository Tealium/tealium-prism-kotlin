package com.tealium.prism.core.internal.modules

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.modules.datalayer.DataLayerModule
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.settings.SdkSettings
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
import org.junit.Assert.assertNotSame
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
    private val modules = listOf(
        testCollector,
        testDispatcher,
        testModule
    )
    private val defaultFactories = modules.map { TestModuleFactory.forModule(it) }
    private val defaultSettings = modules.mapIndexed { idx, module ->
        ModuleSettings(module.id, order = idx)
    }.associateBy(ModuleSettings::moduleType)

    private lateinit var moduleManager: ModuleManagerImpl
    private lateinit var modulesSubject: StateSubject<List<Module>>
    private lateinit var context: TealiumContext

    @Before
    fun setUp() {
        modulesSubject = Observables.stateSubject(listOf())

        moduleManager = ModuleManagerImpl(
            Scheduler.SYNCHRONOUS, modulesSubject
        )
        defaultFactories.forEach(moduleManager::addModuleFactory)

        context = mockk<TealiumContext>()
        every { context.logger } returns SystemLogger
        moduleManager.updateModuleSettings(context, SdkSettings(modules = defaultSettings))
    }

    @Test
    fun getModuleOfType_Returns_Same_Matching_Given_Specific_Class_On_Given_Scheduler() {
        val onCollector = mockk<Callback<TestCollector?>>(relaxed = true)
        val onDispatcher = mockk<Callback<TestDispatcher?>>(relaxed = true)
        val onModule = mockk<Callback<TestModule?>>(relaxed = true)
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
        val onCollector = mockk<Callback<Collector?>>(relaxed = true)
        val onDispatcher = mockk<Callback<Dispatcher?>>(relaxed = true)
        moduleManager.getModuleOfType(Collector::class.java, onCollector)
        moduleManager.getModuleOfType(Dispatcher::class.java, onDispatcher)

        verify(timeout = 1000) {
            onCollector.onComplete(testCollector)
            onDispatcher.onComplete(testDispatcher)
        }
    }

    @Test
    fun getModuleOfType_Returns_First_When_Matching_Multiple_On_Given_Scheduler() {
        val onModule = mockk<Callback<Module?>>(relaxed = true)
        moduleManager.getModuleOfType(Module::class.java, onModule)

        verify(timeout = 1000) {
            onModule.onComplete(testCollector)
        }
    }

    @Test
    fun getModuleOfType_Returns_Null_When_Matching_None_On_Given_Scheduler() {
        val onModule = mockk<Callback<DataLayerModule?>>(relaxed = true)
        moduleManager.getModuleOfType(DataLayerModule::class.java, onModule)

        verify(timeout = 1000) {
            onModule.onComplete(null)
        }
    }

    @Test
    fun observeModule_Emits_Immediately_When_Modules_Are_Preloaded() {
        val preloaded = ModuleManagerImpl(
            Scheduler.SYNCHRONOUS,
            Observables.stateSubject(listOf(testDispatcher))
        )

        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)
        preloaded.observeModule(TestDispatcher::class.java).subscribe(observer)

        verify { observer.onNext(testDispatcher) }
    }

    @Test
    fun observeModule_Emits_Module_Created_Later_In_The_Same_Settings_Pass() {
        val manager = freshModuleManager()
        var observed: TestDispatcher? = null

        // Resolves a dependency from its own constructor, so it subscribes mid-pass.
        manager.addModuleFactory(
            TestModuleFactory.singleInstance("ctor_observer") { moduleId, _, _ ->
                manager.observeModule(TestDispatcher::class.java).subscribeOnce { dispatcher ->
                    observed = dispatcher
                }
                TestModule(moduleId)
            }
        )
        manager.addModuleFactory(TestModuleFactory.forModule(testDispatcher))
        manager.addModuleFactory(TestModuleFactory.forModule(testModule))

        // First pass: no dispatcher configured, so the gate ends up holding a list without one.
        manager.updateModuleSettings(
            context,
            SdkSettings(modules = mapOf(testModule.id to ModuleSettings(testModule.id, order = 0)))
        )
        // Second pass: the observer is ordered before the dispatcher, so it is created first.
        manager.updateModuleSettings(
            context,
            SdkSettings(
                modules = mapOf(
                    "ctor_observer" to ModuleSettings("ctor_observer", order = 0),
                    testDispatcher.id to ModuleSettings(testDispatcher.id, order = 1)
                )
            )
        )

        assertSame(testDispatcher, observed)
    }

    @Test
    fun observeModule_Does_Not_Emit_Before_Modules_Are_Loaded() {
        val freshManager = freshModuleManager()
        defaultFactories.forEach(freshManager::addModuleFactory)

        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)
        freshManager.observeModule(TestDispatcher::class.java)
            .subscribe(observer)

        verify(inverse = true) {
            observer.onNext(any())
        }

        freshManager.updateModuleSettings(context, SdkSettings(modules = defaultSettings))

        verify {
            observer.onNext(testDispatcher)
        }
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
    fun observeModule_Emits_Null_Once_When_Module_Stays_Absent_Across_Passes() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(testDispatcher.id))

        moduleManager.observeModule(TestDispatcher::class.java).subscribe(observer)
        moduleManager.updateModuleSettings(context, enableModuleSettings(testModule.id))
        assertEquals(listOf(testModule), moduleManager.modules.value)
        moduleManager.updateModuleSettings(context, enableModuleSettings(testCollector.id))
        assertEquals(listOf(testCollector), moduleManager.modules.value)

        verify(exactly = 1) {
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
    fun observeModule_Emits_Null_After_Shutdown() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)

        moduleManager.observeModule(TestDispatcher::class.java).subscribe(observer)
        moduleManager.shutdown()

        verifyOrder {
            observer.onNext(testDispatcher)
            observer.onNext(null)
        }
    }

    @Test
    fun updateModuleSettings_Updates_Existing_Modules() {
        moduleManager.updateModuleSettings(context, SdkSettings(modules = defaultSettings))

        verify {
            testCollector.updateConfiguration(any())
            testDispatcher.updateConfiguration(any())
            testModule.updateConfiguration(any())
        }
    }

    @Test
    fun updateModuleSettings_Removes_Modules_That_Return_Null_From_UpdateConfiguration() {
        every { testModule.updateConfiguration(any()) } returns null
        moduleManager.updateModuleSettings(context, SdkSettings(modules = defaultSettings))

        assertFalse(modulesSubject.value.contains(testModule))

        assertTrue(modulesSubject.value.contains(testCollector))
        assertTrue(modulesSubject.value.contains(testDispatcher))
    }

    @Test
    fun updateModuleSettings_Calls_OnShutdown_When_UpdateConfiguration_ReturnsNull() {
        every { testModule.updateConfiguration(any()) } returns null
        moduleManager.updateModuleSettings(context, SdkSettings(modules = defaultSettings))

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

        listOf("1", "2").forEachIndexed { idx, id ->
            assertEquals(id, moduleManager.modules.value[idx].id)
        }
    }

    @Test
    fun updateModuleSettings_Does_Not_Create_Modules_With_No_Settings() {
        moduleManager.updateModuleSettings(context, SdkSettings(modules = emptyMap()))

        assertTrue(moduleManager.modules.value.isEmpty())
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
        moduleManager.updateModuleSettings(context, SdkSettings(modules = mapOf(
            newModuleFactory.moduleType to ModuleSettings(newModuleFactory.moduleType)
        )))

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

    @Test
    fun observeModule_Replays_The_Last_Known_Module_To_A_Late_Subscriber() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)

        // setUp already ran a pass: its answer is replayed rather than withheld.
        moduleManager.observeModule(TestDispatcher::class.java).subscribe(observer)

        verify(exactly = 1) {
            observer.onNext(testDispatcher)
        }
    }

    /**
     * A subscriber still held at the gate has to be released by the shutdown: it is waiting for a
     * modules pass that is never going to run.
     */
    @Test
    fun observeModule_Emits_Null_On_Shutdown_To_A_Subscriber_Waiting_For_The_First_Pass() {
        val freshManager = freshModuleManager()
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)

        freshManager.observeModule(TestDispatcher::class.java).subscribe(observer)

        verify(inverse = true) {
            observer.onNext(any())
        }

        freshManager.shutdown()

        verify(exactly = 1) {
            observer.onNext(null)
        }
    }

    @Test
    fun observeModule_Emits_Null_To_A_Subscriber_That_Arrives_After_Shutdown() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)
        moduleManager.shutdown()

        moduleManager.observeModule(TestDispatcher::class.java).subscribe(observer)

        verify(exactly = 1) {
            observer.onNext(null)
        }
    }

    @Test
    fun observeModule_Emits_The_First_Module_Matching_By_Settings_Order() {
        val fixture = twoDispatchers()
        val emissions = mutableListOf<Dispatcher?>()

        fixture.manager.observeModule(Dispatcher::class.java).subscribe { emissions.add(it) }
        // second is ordered first by the settings, so it is the one that matches the interface.
        fixture.manager.updateModuleSettings(
            context, dispatchersSettings(fixture.second, fixture.first)
        )

        assertEquals(1, emissions.size)
        assertSame(fixture.second, emissions.first())
    }

    /**
     * [Module.updateConfiguration] reconfiguring a module in place must not be confused with it
     * being replaced: only the latter has to re-emit, which is why the lookups compare by instance
     * identity rather than by [Module.id].
     */
    @Test
    fun observeModule_Re_Emits_When_The_Module_Is_Replaced_By_A_New_Instance() {
        val manager = replacingModuleManager()
        val emissions = mutableListOf<ReplacingModule?>()

        manager.observeModule(ReplacingModule::class.java).subscribe { emissions.add(it) }
        manager.updateModuleSettings(context, replacingModuleSettings())
        manager.updateModuleSettings(context, replacingModuleSettings())

        assertEquals(2, emissions.size)
        assertNotSame(emissions.first(), emissions.last())
    }

    @Test
    fun observeModules_Does_Not_Emit_Before_Modules_Are_Loaded() {
        val freshManager = freshModuleManager()
        defaultFactories.forEach(freshManager::addModuleFactory)
        val observer = mockk<Observer<List<Dispatcher>>>(relaxed = true)

        freshManager.observeModules(Dispatcher::class.java).subscribe(observer)

        // A premature empty list would read as "no dispatcher is enabled".
        verify(inverse = true) {
            observer.onNext(any())
        }

        freshManager.updateModuleSettings(context, SdkSettings(modules = defaultSettings))

        verify {
            observer.onNext(listOf(testDispatcher))
        }
    }

    @Test
    fun observeModules_Emits_Every_Matching_Module_Ordered_By_Settings_Order() {
        val fixture = twoDispatchers()
        val emissions = mutableListOf<List<Dispatcher>>()

        fixture.manager.observeModules(Dispatcher::class.java).subscribe { emissions.add(it) }
        fixture.manager.updateModuleSettings(
            context, dispatchersSettings(fixture.second, fixture.first)
        )

        assertEquals(1, emissions.size)
        assertEquals(listOf(fixture.second, fixture.first), emissions.first())
    }

    @Test
    fun observeModules_Skips_The_Modules_That_Do_Not_Match_The_Class() {
        val emissions = mutableListOf<List<Dispatcher>>()

        moduleManager.observeModules(Dispatcher::class.java).subscribe { emissions.add(it) }

        assertEquals(3, modulesSubject.value.size)
        assertEquals(listOf<Dispatcher>(testDispatcher), emissions.single())
    }

    @Test
    fun observeModules_Emits_Empty_List_When_A_Pass_Matches_No_Module() {
        val freshManager = freshModuleManager()
        val observer = mockk<Observer<List<Dispatcher>>>(relaxed = true)

        freshManager.observeModules(Dispatcher::class.java).subscribe(observer)
        // The pass has run and no module matched: the empty list is the answer.
        freshManager.mockReady(context)

        verify(exactly = 1) {
            observer.onNext(emptyList())
        }
    }

    @Test
    fun observeModules_Does_Not_Re_Emit_The_Same_Elements_When_Another_Module_Is_Disabled() {
        val observer = mockk<Observer<List<TestDispatcher>>>(relaxed = true)

        moduleManager.observeModules(TestDispatcher::class.java).subscribe(observer)
        moduleManager.updateModuleSettings(context, settingsWithDisabled(testModule.id))

        verify(exactly = 1) {
            observer.onNext(listOf(testDispatcher))
        }
    }

    // A subscriber still held at the gate has to be released by the shutdown, as with observeModule.
    @Test
    fun observeModules_Emits_Empty_List_On_Shutdown_While_Waiting_For_The_First_Pass() {
        val freshManager = freshModuleManager()
        val observer = mockk<Observer<List<Dispatcher>>>(relaxed = true)

        freshManager.observeModules(Dispatcher::class.java).subscribe(observer)

        verify(inverse = true) {
            observer.onNext(any())
        }

        freshManager.shutdown()

        verify(exactly = 1) {
            observer.onNext(emptyList())
        }
    }

    @Test
    fun observeModules_Re_Emits_The_Same_Instances_In_A_New_Order() {
        val fixture = twoDispatchers()
        val emissions = mutableListOf<List<Dispatcher>>()
        fixture.manager.updateModuleSettings(
            context, dispatchersSettings(fixture.second, fixture.first)
        )

        fixture.manager.observeModules(Dispatcher::class.java).subscribe { emissions.add(it) }
        fixture.manager.updateModuleSettings(
            context, dispatchersSettings(fixture.first, fixture.second)
        )

        assertEquals(2, emissions.size)
        assertEquals(listOf(fixture.first, fixture.second), emissions.last())
        // same instances, swapped
        assertSame(emissions.first().last(), emissions.last().first())
    }

    @Test
    fun observeModules_Re_Emits_When_A_Module_Is_Replaced_By_A_New_Instance() {
        val manager = replacingModuleManager()
        val emissions = mutableListOf<List<ReplacingModule>>()

        manager.observeModules(ReplacingModule::class.java).subscribe { emissions.add(it) }
        manager.updateModuleSettings(context, replacingModuleSettings())
        manager.updateModuleSettings(context, replacingModuleSettings())

        assertEquals(2, emissions.size)
        assertNotSame(emissions.first().single(), emissions.last().single())
    }

    @Test
    fun getModuleOfType_Completes_With_Null_When_Shutdown_Happens_Before_Any_Settings_Pass() {
        val freshManager = freshModuleManager()
        val onDispatcher = mockk<Callback<TestDispatcher?>>(relaxed = true)

        freshManager.getModuleOfType(TestDispatcher::class.java, onDispatcher)

        verify(inverse = true) {
            onDispatcher.onComplete(any())
        }

        freshManager.shutdown()

        verify(exactly = 1) {
            onDispatcher.onComplete(null)
        }
    }

    @Test
    fun getModuleOfType_Completes_With_Null_When_Called_After_Shutdown() {
        val onDispatcher = mockk<Callback<TestDispatcher?>>(relaxed = true)
        moduleManager.shutdown()

        moduleManager.getModuleOfType(TestDispatcher::class.java, onDispatcher)

        verify(exactly = 1) {
            onDispatcher.onComplete(null)
        }
    }

    @Test
    fun getModuleOfType_Does_Not_Call_Callback_When_Disposed_Before_Modules_Are_Ready() {
        val freshManager = freshModuleManager()
        val callback = mockk<Callback<TestDispatcher?>>(relaxed = true)
        freshManager.addModuleFactory(TestModuleFactory.forModule(testDispatcher))

        val disposable = freshManager.getModuleOfType(TestDispatcher::class.java, callback)
        disposable.dispose()

        freshManager.updateModuleSettings(context, SdkSettings(modules = defaultSettings))

        verify(exactly = 0) {
            callback.onComplete(any())
        }

        // Verify callback is called when NOT disposed
        val callbackNotDisposed = mockk<Callback<TestDispatcher?>>(relaxed = true)
        val disposableNotDisposed = freshManager.getModuleOfType(TestDispatcher::class.java, callbackNotDisposed)

        verify(exactly = 1) {
            callbackNotDisposed.onComplete(testDispatcher)
        }
    }

    @Test
    fun getModuleOfType_Completes_With_Null_When_Called_During_A_Module_Shutdown() {
        val manager = freshModuleManager()
        val resolved = mutableListOf<TestDispatcher?>()
        manager.addModuleFactory(
            TestModuleFactory.singleInstance("shutdown_observer") { moduleId, _, _ ->
                ShutdownObservingModule(moduleId, manager) { resolved.add(it) }
            }
        )
        manager.addModuleFactory(TestModuleFactory.forModule(testDispatcher))
        manager.updateModuleSettings(
            context,
            SdkSettings(
                modules = mapOf(
                    "shutdown_observer" to ModuleSettings("shutdown_observer", order = 0),
                    testDispatcher.id to ModuleSettings(testDispatcher.id, order = 1)
                )
            )
        )

        manager.shutdown()

        // The sibling is on its way out too, so the only honest answer is "not enabled".
        assertEquals(listOf<TestDispatcher?>(null), resolved)
    }

    @Test
    fun updateModuleSettings_Calls_OnShutdown_When_Module_Omitted_From_Settings() {
        // The entry is gone from the settings altogether, not present and disabled.
        moduleManager.updateModuleSettings(
            context,
            SdkSettings(modules = mapOf(testDispatcher.id to ModuleSettings(testDispatcher.id)))
        )

        verify { testCollector.onShutdown() }
        assertFalse(modulesSubject.value.contains(testCollector))
    }

    @Test
    fun updateModuleSettings_Calls_OnShutdown_On_The_Instance_Replaced_By_UpdateConfiguration() {
        val replacement = TestModule(testModule.id)
        every { testModule.updateConfiguration(any()) } returns replacement

        moduleManager.updateModuleSettings(context, SdkSettings(modules = defaultSettings))

        verify { testModule.onShutdown() }
        assertTrue(modulesSubject.value.contains(replacement))
        assertFalse(modulesSubject.value.contains(testModule))
    }

    /** Centralizing the shutdown in updateModuleSettings must not shut the disabled path down twice. */
    @Test
    fun updateModuleSettings_Calls_OnShutdown_Exactly_Once_When_Module_Disabled() {
        moduleManager.updateModuleSettings(context, settingsWithDisabled(testModule.id))

        verify(exactly = 1) { testModule.onShutdown() }
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

    private fun freshModuleManager(): ModuleManagerImpl =
        ModuleManagerImpl(Scheduler.SYNCHRONOUS, Observables.stateSubject(listOf()))

    /**
     * Returns the [defaultSettings] with the module of the given [moduleId] explicitly disabled, so
     * that every other module of the pass is left untouched.
     */
    private fun settingsWithDisabled(moduleId: String): SdkSettings =
        SdkSettings(
            modules = defaultSettings + (moduleId to ModuleSettings(moduleId, enabled = false))
        )

    private data class DispatcherPair(
        val manager: ModuleManagerImpl,
        val first: TestDispatcher,
        val second: TestDispatcher
    )

    /**
     * A fresh manager with two [Dispatcher]s available, so that a lookup matching both has
     * something to return in order.
     */
    private fun twoDispatchers(): DispatcherPair {
        val manager = freshModuleManager()
        val first = TestDispatcher.mock("dispatcher_1")
        val second = TestDispatcher.mock("dispatcher_2")
        manager.addModuleFactory(TestModuleFactory.forModule(first))
        manager.addModuleFactory(TestModuleFactory.forModule(second))
        return DispatcherPair(manager, first, second)
    }

    private fun dispatchersSettings(vararg dispatchers: TestDispatcher): SdkSettings =
        SdkSettings(
            modules = dispatchers.mapIndexed { idx, dispatcher ->
                dispatcher.id to ModuleSettings(dispatcher.id, order = idx)
            }.toMap()
        )

    private fun replacingModuleManager(): ModuleManagerImpl = freshModuleManager().apply {
        addModuleFactory(
            TestModuleFactory.singleInstance(REPLACING_MODULE_TYPE) { moduleId, _, _ ->
                ReplacingModule(moduleId)
            }
        )
    }

    private fun replacingModuleSettings(): SdkSettings =
        SdkSettings(
            modules = mapOf(REPLACING_MODULE_TYPE to ModuleSettings(REPLACING_MODULE_TYPE))
        )

    /**
     * Hands back a brand new instance instead of `this` from [updateConfiguration], the way a module
     * whose configuration change requires reconstruction behaves.
     */
    private class ReplacingModule(override val id: String) : Module {
        override val version: String = "1.0"

        override fun updateConfiguration(configuration: DataObject): Module = ReplacingModule(id)
    }

    /**
     * Looks another module up from its own [onShutdown], the way a module releasing a dependency
     * while the instance is being torn down would.
     */
    private class ShutdownObservingModule(
        override val id: String,
        private val moduleManager: ModuleManager,
        private val onResolved: (TestDispatcher?) -> Unit
    ) : Module {
        override val version: String = "1.0"

        override fun onShutdown() {
            moduleManager.getModuleOfType(TestDispatcher::class.java) { onResolved(it) }
        }
    }

    private companion object {
        const val REPLACING_MODULE_TYPE = "replacing"
    }
}

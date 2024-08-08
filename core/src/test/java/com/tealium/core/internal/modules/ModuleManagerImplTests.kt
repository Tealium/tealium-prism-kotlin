package com.tealium.core.internal.modules

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.modules.*
import com.tealium.core.internal.settings.SdkSettings
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.internal.settings.ModuleSettingsImpl
import com.tealium.tests.common.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
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
    private val defaultFactories = listOf(
        TestModuleFactory(testCollector),
        TestModuleFactory(testDispatcher),
        TestModuleFactory(testModule)
    )

    private lateinit var moduleManager: InternalModuleManager
    private lateinit var modulesSubject: StateSubject<Set<Module>>
    private lateinit var context: TealiumContext

    @Before
    fun setUp() {
        modulesSubject = Observables.stateSubject(setOf())

        moduleManager = ModuleManagerImpl(
            defaultFactories, testTealiumScheduler, modulesSubject
        )
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
        val module = moduleManager.getModuleOfType(ModuleManager::class.java)

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
        val onModule = mockk<TealiumCallback<ModuleManager?>>(relaxed = true)
        moduleManager.getModuleOfType(ModuleManager::class.java, onModule)

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
        val modules = moduleManager.getModulesOfType(ModuleManager::class.java)
        assertTrue(modules.isEmpty())
    }

    @Test
    fun updateSettings_Updates_ExistingModules() {
        moduleManager.updateModuleSettings(context, SdkSettings(mapOf()))

        verify {
            testCollector.updateSettings(any())
            testDispatcher.updateSettings(any())
            testModule.updateSettings(any())
        }
    }

    @Test
    fun updateSettings_RemovesModules_ThatReturnNull() {
        every { testModule.updateSettings(any()) } returns null
        moduleManager.updateModuleSettings(context, SdkSettings(mapOf()))

        assertFalse(modulesSubject.value.contains(testModule))

        assertTrue(modulesSubject.value.contains(testCollector))
        assertTrue(modulesSubject.value.contains(testDispatcher))
    }

    @Test
    fun updateSettings_ProvidesSettings_ToSpecificModules() {
        val testCollectorSettings = ModuleSettingsImpl(true, TealiumBundle.create {
            put("collector_setting", "10")
        })
        val testDispatcherSettings = ModuleSettingsImpl(true, TealiumBundle.create {
            put("dispatcher_setting", "10")
        })
        val testModuleSettings = ModuleSettingsImpl(true, TealiumBundle.create {
            put("module_setting", "10")
        })
        moduleManager.updateModuleSettings(context, SdkSettings(mapOf(
            testCollector.name to testCollectorSettings,
            testDispatcher.name to testDispatcherSettings,
            testModule.name to testModuleSettings
        )))

        verify {
            testCollector.updateSettings(testCollectorSettings)
            testDispatcher.updateSettings(testDispatcherSettings)
            testModule.updateSettings(testModuleSettings)
        }
    }
}

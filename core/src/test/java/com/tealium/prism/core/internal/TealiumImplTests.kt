package com.tealium.prism.core.internal

import android.app.Activity
import android.app.Application
import com.tealium.prism.core.api.consent.ConsentDecision
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.settings.modules.ModuleSettingsBuilder
import com.tealium.prism.core.internal.consent.MockCmpAdapter
import com.tealium.prism.core.internal.misc.ActivityManagerImpl
import com.tealium.prism.core.internal.modules.InternalModuleManager
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.modules.TealiumDataModule
import com.tealium.prism.core.internal.modules.datalayer.DataLayerModule
import com.tealium.prism.core.internal.settings.MappingsImpl
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.settings.SdkSettings
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestModule
import com.tealium.tests.common.TestModuleFactory
import com.tealium.tests.common.assertThrows
import com.tealium.tests.common.getDefaultConfig
import com.tealium.tests.common.getDefaultConfigBuilder
import com.tealium.tests.common.testSchedulers
import io.mockk.Ordering
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TealiumImplTests {

    lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
    }

    @Test
    fun isShutdown_Returns_False_When_Not_Shutdown() {
        val tealium =
            TealiumImpl(getDefaultConfig(app), testSchedulers)

        assertFalse(tealium.isShutdown)
    }

    @Test
    fun isShutdown_Returns_True_After_Shutdown_Called() {
        val tealium =
            TealiumImpl(getDefaultConfig(app), testSchedulers)

        tealium.shutdown()

        assertTrue(tealium.isShutdown)
    }

    @Test
    fun init_Throws_Tealium_Exception_And_Shuts_Down_When_Exception_During_Init() {
        val enabledSettings = ModuleSettingsBuilder("type").setEnabled(true).build()
        val config = getDefaultConfigBuilder(app)
            .addModule(TestModuleFactory.singleInstance("type", enabledSettings) { _, _, _ ->
                throw StackOverflowError()
            }).build()
        val disposables = Disposables.composite()

        assertThrows<TealiumException>(cause = StackOverflowError::class) {
            TealiumImpl(config, testSchedulers, disposables = disposables)
        }
        assertTrue(disposables.isDisposed)
    }

    @Test
    fun init_Subscribes_Consent_To_CmpAdapter_And_Disposes_On_Shutdown() {
        val decision = Observables.stateSubject<ConsentDecision?>(null)
        val config = getDefaultConfigBuilder(app)
            .enableConsentIntegration(MockCmpAdapter(_consentDecision = decision))
            .build()
        val tealium = TealiumImpl(config, testSchedulers)

        assertEquals(1, decision.count)

        tealium.shutdown()
        assertEquals(0, decision.count)
    }

    @Test
    fun shutdown_Shuts_Down_Module_Manager() {
        val moduleManager = spyk(ModuleManagerImpl(testSchedulers.tealium))
        val tealium =
            TealiumImpl(getDefaultConfig(app), testSchedulers, moduleManager = moduleManager)

        tealium.shutdown()

        verify { moduleManager.shutdown() }
    }

    @Test
    fun tealium_Notifies_ApplicationSubscriberModules_IfSubscribed_AtLaunch() {
        val appObserver = mockk<Observer<ActivityManager.ApplicationStatus>>(relaxed = true)
        val appAwareModuleFactory = TestModuleFactory(
            "app-aware-module",
            config = listOf(ModuleSettingsBuilder("app-aware-module").build())
        ) { _, ctx, _ ->
            ObserverModule(
                ctx.activityManager.applicationStatus,
                appObserver
            )
        }

        val activityCallbacks = ActivityManagerImpl.ActivityCallbacks(app)
        val activityManager = ActivityManagerImpl(
            app,
            mainScheduler = testSchedulers.main,
            activityMonitor = activityCallbacks
        )
        val activity = mockk<Activity>()
        every { activity.isChangingConfigurations } returns false

        // pre-load an init foregrounded, and backgrounded event
        activityCallbacks.onActivityResumed(activity)
        activityCallbacks.onActivityStopped(activity)

        testSchedulers.tealium.execute {
            TealiumImpl(
                getDefaultConfig(app, modules = listOf(appAwareModuleFactory)),
                testSchedulers,
                activityManager = activityManager
            )
        }

        verify(timeout = 2500, ordering = Ordering.ORDERED) {
            appObserver.onNext(match { it is ActivityManager.ApplicationStatus.Init })
            appObserver.onNext(match { it is ActivityManager.ApplicationStatus.Foregrounded })
            appObserver.onNext(match { it is ActivityManager.ApplicationStatus.Backgrounded })
        }
    }

    @Test
    fun tealium_Notifies_ActivitySubscriberModules_IfSubscribed_AtLaunch() {
        val appObserver = mockk<Observer<ActivityManager.ActivityStatus>>(relaxed = true)
        val appAwareModuleFactory = TestModuleFactory(
            "activity-aware-module",
            config = listOf(ModuleSettingsBuilder("activity-aware-module").build())
        ) { _, ctx, _ ->
            ObserverModule(
                ctx.activityManager.activities,
                appObserver
            )
        }

        val activityCallbacks = ActivityManagerImpl.ActivityCallbacks(app)
        val activityManager = ActivityManagerImpl(
            app,
            mainScheduler = testSchedulers.main,
            activityMonitor = activityCallbacks
        )
        val activity = mockk<Activity>()
        every { activity.isChangingConfigurations } returns false

        // pre-load an init foregrounded, and backgrounded event
        activityCallbacks.onActivityResumed(activity)
        activityCallbacks.onActivityStopped(activity)

        testSchedulers.tealium.execute {
            TealiumImpl(
                getDefaultConfig(app, modules = listOf(appAwareModuleFactory)),
                testSchedulers,
                activityManager = activityManager
            )
        }

        verify(timeout = 2500, ordering = Ordering.ORDERED) {
            appObserver.onNext(match {
                it.activity == activity
                        && it.type == ActivityManager.ActivityLifecycleType.Resumed
            })
            appObserver.onNext(match {
                it.activity == activity
                        && it.type == ActivityManager.ActivityLifecycleType.Stopped
            })
        }
    }

    @Test
    fun addAndValidateDefaultFactories_Adds_Required_Modules_When_Omitted() {
        val defaults = getTestModules(2)

        val validatedFactories =
            TealiumImpl.addAndValidateDefaultFactories(emptyList(), defaults, SystemLogger)

        assertTrue(validatedFactories.containsAll(defaults))
    }

    @Test
    fun addAndValidateDefaultFactories_Does_Not_Add_Default_When_Already_Present() {
        val module1 = getTestModule(1)
        val default1 = getTestModule(1)

        val validatedFactories =
            TealiumImpl.addAndValidateDefaultFactories(
                listOf(module1),
                listOf(default1),
                SystemLogger
            )

        assertTrue(validatedFactories.contains(module1))
        assertFalse(validatedFactories.contains(default1))
    }

    @Test
    fun addAndValidateDefaultFactories_Logs_Warning_When_Default_Id_Present_But_Incorrect_Implementation() {
        val logger = mockk<Logger>(relaxed = true)
        every { logger.shouldLog(LogLevel.WARN) } returns true
        val module1 = getTestModule(1)
        val default1 = DelegateModuleFactory(module1)

        TealiumImpl.addAndValidateDefaultFactories(listOf(module1), listOf(default1), logger)

        verify {
            logger.warn(any(), any<String>())
        }
    }

    @Test
    fun loadModuleFactories_Adds_DataLayer_And_Tealium_Collector_When_Omitted() {
        val moduleManager = mockk<InternalModuleManager>(relaxed = true)
        every { moduleManager.addModuleFactory(any()) } returns true

        TealiumImpl.loadModuleFactories(listOf(), moduleManager, SystemLogger)

        verify {
            moduleManager.addModuleFactory(match { it is DataLayerModule.Factory })
            moduleManager.addModuleFactory(match { it is TealiumDataModule.Factory })
        }
    }

    @Test
    fun extractMappings_Returns_Empty_List_When_SdkSetting_Has_No_ModuleSettings() {
        val result = TealiumImpl.extractMappings(SdkSettings())
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractMappings_Returns_Empty_List_When_SdkSetting_Has_No_Modules_With_Mappings() {
        val result =
            TealiumImpl.extractMappings(SdkSettings(modules = mapOf("dispatcher" to ModuleSettings("module"))))
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractMappings_Returns_Mappings_When_Given_Valid_DataObject() {
        val mappings = MappingsImpl().apply {
            mapFrom("source1", "destination1")
            mapConstant("value", "destination2")
                .ifValueEquals("source2", "expected")
        }.build()

        val allMappings = TealiumImpl.extractMappings(
            SdkSettings(
                modules = mapOf(
                    "dispatcher" to ModuleSettings("module", mappings = mappings)
                )
            )
        )

        assertEquals(1, allMappings.size)

        val dispatcherMappings = allMappings["dispatcher"]!!
        assertEquals(2, dispatcherMappings.size)

        val mapping1 = dispatcherMappings[0]
        assertEquals(JsonPath["destination1"], mapping1.destination.path)
        assertEquals(JsonPath["source1"], mapping1.parameters.reference?.path)
        assertNull(mapping1.parameters.filter)
        assertNull(mapping1.parameters.mapTo)

        val mapping2 = dispatcherMappings[1]
        assertEquals(JsonPath["destination2"], mapping2.destination.path)
        assertEquals(JsonPath["source2"], mapping2.parameters.reference?.path)
        assertEquals("expected", mapping2.parameters.filter?.value)
        assertEquals("value", mapping2.parameters.mapTo?.value)
    }

    private fun getTestModules(count: Int = 1): List<ModuleFactory> = (1..count).map {
        getTestModule(it)
    }

    private fun getTestModule(id: Int = 1): ModuleFactory =
        TestModuleFactory.forModule(TestModule("module_$id"))

    private class ObserverModule<T>(
        observable: Observable<T>,
        private val observer: Observer<T>,
        override val id: String = "observer",
        override val version: String = "1.0.0"
    ) : Module, Observer<T> by observer {
        init {
            observable.subscribe(this)
        }
    }

    private class DelegateModuleFactory(private val delegate: ModuleFactory) :
        ModuleFactory by delegate
}

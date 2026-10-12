package com.tealium.prism.core.internal

import android.app.Activity
import android.app.Application
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.consent.ConsentDecision
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.ReplaySubject
import com.tealium.prism.core.api.settings.modules.ModuleSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.internal.consent.ConsentIntegrationManager
import com.tealium.prism.core.internal.consent.MockCmpAdapter
import com.tealium.prism.core.internal.misc.ActivityManagerImpl
import com.tealium.prism.core.internal.misc.SchedulersImpl
import com.tealium.prism.core.internal.persistence.database.FileDatabaseProvider
import com.tealium.prism.core.internal.persistence.repositories.QueueRepository
import com.tealium.prism.core.internal.persistence.repositories.SQLQueueRepository
import com.tealium.prism.core.internal.modules.InternalModuleManager
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.modules.TealiumDataModule
import com.tealium.prism.core.internal.modules.datalayer.DataLayerModule
import com.tealium.prism.core.internal.settings.CoreSettingsImpl
import com.tealium.prism.core.internal.settings.MappingsImpl
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.settings.SdkSettings
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.TestModule
import com.tealium.tests.common.TestModuleFactory
import com.tealium.tests.common.assertThrows
import com.tealium.tests.common.getDefaultConfig
import com.tealium.tests.common.getDefaultConfigBuilder
import com.tealium.tests.common.testMainScheduler
import com.tealium.tests.common.testNetworkScheduler
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        val tealium = TealiumImpl(
            config,
            SchedulersImpl(testMainScheduler, Scheduler.SYNCHRONOUS, testNetworkScheduler)
        )

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
            mapConstant("value".asDataItem(), "destination2")
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
        assertEquals("value", mapping2.parameters.mapTo?.value?.value)
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

    /**
     * Every emission makes the queue delete the queues of the processors that are not in the list, so
     * nothing may be published before the modules are ready: the readiness gate is what supplies that
     * "not yet" answer, and nothing here may add one of its own.
     */
    @Test
    fun createQueueManager_Processors_Do_Not_Emit_Until_The_Dispatchers_Do() {
        val queueRepository = mockk<QueueRepository>(relaxed = true)
        val onDispatchers: ReplaySubject<List<Dispatcher>> = Observables.replaySubject(1)

        TealiumImpl.createQueueManager(
            queueRepository,
            Observables.stateSubject(CoreSettingsImpl()),
            onDispatchers,
            addConsent = false,
            logger = SystemLogger
        )

        verify(exactly = 0) {
            queueRepository.deleteQueues(any())
        }
    }

    /**
     * No dispatcher enabled is an answer, unlike no emission at all: those queues genuinely have no
     * owner left, so they are cleared down to the consent queue.
     */
    @Test
    fun createQueueManager_Processors_Emit_Only_The_Consent_Manager_When_No_Dispatcher_Is_Enabled() {
        val queueRepository = mockk<QueueRepository>(relaxed = true)
        val onDispatchers: ReplaySubject<List<Dispatcher>> = Observables.replaySubject(1)
        onDispatchers.onNext(emptyList())

        TealiumImpl.createQueueManager(
            queueRepository,
            Observables.stateSubject(CoreSettingsImpl()),
            onDispatchers,
            addConsent = true,
            logger = SystemLogger
        )

        verify {
            queueRepository.deleteQueues(forProcessorsNotIn = setOf(ConsentIntegrationManager.ID))
        }
    }

    @Test
    fun track_Payload_Contains_Visitor_Id_When_VisitorId_Module_Enabled_By_Default() {
        val tealium = TealiumImpl(getDefaultConfig(app), testSchedulers)

        val payload = trackAndAwaitPayload(tealium)

        assertFalse(payload.getString(Dispatch.Keys.TEALIUM_VISITOR_ID).isNullOrEmpty())
        tealium.shutdown()
    }

    @Test
    fun track_Payload_Lacks_Visitor_Id_When_VisitorId_Module_Disabled() {
        val config = getDefaultConfig(
            app,
            modules = listOf(Modules.visitorId { it.setEnabled(false) })
        )
        val tealium = TealiumImpl(config, testSchedulers)

        val payload = trackAndAwaitPayload(tealium)

        assertNull(payload.getString(Dispatch.Keys.TEALIUM_VISITOR_ID))
        tealium.shutdown()
    }

    /**
     * A shutdown is not a configuration change: the empty module list it publishes must not reach the
     * queue, or the dispatches still waiting for their dispatcher would be deleted instead of being
     * sent on the next launch.
     */
    @Test
    fun shutdown_Does_Not_Delete_The_Dispatches_Left_In_The_Queue() {
        val dispatcher = TestDispatcher("test_dispatcher")
        val dispatcherFactory = TestModuleFactory(
            dispatcher.id,
            config = listOf(ModuleSettingsBuilder(dispatcher.id).build())
        ) { _, _, _ -> dispatcher }
        val config = getDefaultConfig(app, modules = listOf(dispatcherFactory))
        val tealium = TealiumImpl(config, testSchedulers)

        // Nothing may dequeue the dispatch before the shutdown.
        tealium.dispatchManager.stopDispatchLoop()
        tealium.queueManager.storeDispatches(
            listOf(Dispatch.create("event", DispatchType.Event, DataObject.EMPTY_OBJECT)),
            setOf(dispatcher.id)
        )

        tealium.shutdown()

        // Read back from the persisted file, as the next launch would.
        val queued = SQLQueueRepository(FileDatabaseProvider(config))
            .getQueuedDispatches(-1, dispatcher.id)
        assertEquals(1, queued.size)
    }

    /**
     * Tracks an event and returns its payload once the collectors have enriched it.
     */
    private fun trackAndAwaitPayload(tealium: TealiumImpl): DataObject {
        val latch = CountDownLatch(1)
        var trackResult: TrackResult? = null
        tealium.track(Dispatch.create("event", DispatchType.Event, DataObject.EMPTY_OBJECT)) { result ->
            trackResult = result
            latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return trackResult!!.dispatch.payload()
    }
}
